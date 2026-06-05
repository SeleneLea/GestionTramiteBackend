package com.example.demo.services;

import com.example.demo.dto.ActividadDocumentosDTO;
import com.example.demo.dto.DocumentoInfoDTO;
import com.example.demo.dto.PoliticaNegocioRequest;
import com.example.demo.models.Actividad;
import com.example.demo.models.Documento;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.DocumentoRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PoliticaNegocioService {

    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private RequisitoDocumentoService requisitoDocumentoService;
    @Autowired private IaProxyService iaProxy;

    public PoliticaNegocio crear(PoliticaNegocioRequest req, String creadorId) {
        if (politicaRepository.findByNombre(req.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una política con el nombre: " + req.getNombre());
        }

        PoliticaNegocio p = new PoliticaNegocio();
        p.setNombre(req.getNombre());
        p.setDescripcion(req.getDescripcion());
        p.setCategoria(req.getCategoria());
        p.setParametros(req.getParametros());
        p.setRequiereDocumentoResolucion(req.isRequiereDocumentoResolucion());
        p.setCreadorId(creadorId);
        p.setVersionActual(1);
        p.setEstado("borrador");
        p.setFechaCreacion(LocalDateTime.now());

        PoliticaNegocio guardada = politicaRepository.save(p);

        if (req.getDiagramaId() != null && !req.getDiagramaId().isBlank()) {
            vincularDiagrama(guardada, req.getDiagramaId());
            guardada = politicaRepository.save(guardada);
        }

        return guardada;
    }

    public List<PoliticaNegocio> listarTodas() {
        return politicaRepository.findAll();
    }

    public List<PoliticaNegocio> listarActivas() {
        return politicaRepository.findByEstado("activa");
    }

    public List<PoliticaNegocio> listarSinDiagrama() {
        java.util.Set<String> conDiagrama = diagramaRepository.findAll().stream()
                .filter(d -> d.getPoliticaId() != null && !"archivado".equals(d.getEstado()))
                .map(com.example.demo.models.DiagramaWorkflow::getPoliticaId)
                .collect(java.util.stream.Collectors.toSet());
        return politicaRepository.findAll().stream()
                .filter(p -> !conDiagrama.contains(p.getId()))
                .toList();
    }

    public Optional<PoliticaNegocio> buscarPorId(String id) {
        return politicaRepository.findById(id);
    }

    public PoliticaNegocio actualizar(String id, PoliticaNegocioRequest req) {
        PoliticaNegocio p = politicaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        politicaRepository.findByNombre(req.getNombre())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new IllegalArgumentException("El nombre ya lo usa otra política");
                    }
                });

        p.setNombre(req.getNombre());
        p.setDescripcion(req.getDescripcion());
        p.setCategoria(req.getCategoria());
        p.setParametros(req.getParametros());
        p.setRequiereDocumentoResolucion(req.isRequiereDocumentoResolucion());

        String nuevoDiagramaId = (req.getDiagramaId() != null && !req.getDiagramaId().isBlank())
                ? req.getDiagramaId()
                : null;
        String diagramaActual = p.getDiagramaId();

        if (!java.util.Objects.equals(nuevoDiagramaId, diagramaActual)) {
            if (diagramaActual != null) {
                diagramaRepository.findById(diagramaActual).ifPresent(d -> {
                    d.setPoliticaId(null);
                    diagramaRepository.save(d);
                });
            }
            if (nuevoDiagramaId != null) {
                vincularDiagrama(p, nuevoDiagramaId);
            } else {
                p.setDiagramaId(null);
            }
        }

        return politicaRepository.save(p);
    }

    private void vincularDiagrama(PoliticaNegocio politica, String diagramaId) {
        var diagrama = diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado: " + diagramaId));

        if (diagrama.getPoliticaId() != null && !diagrama.getPoliticaId().equals(politica.getId())) {
            throw new IllegalArgumentException(
                    "El diagrama ya está vinculado a otra política");
        }

        politica.setDiagramaId(diagrama.getId());
        diagrama.setPoliticaId(politica.getId());
        diagramaRepository.save(diagrama);
    }

    public PoliticaNegocio cambiarEstado(String id, String nuevoEstado) {
        PoliticaNegocio p = politicaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        validarTransicionEstado(p.getEstado(), nuevoEstado);

        if ("activa".equals(nuevoEstado)) {
            if (p.getDiagramaId() == null) {
                throw new IllegalArgumentException(
                        "No se puede activar la política: primero asigna un diagrama de flujo");
            }
            var diagrama = diagramaRepository.findById(p.getDiagramaId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El diagrama asignado no existe. Crea o vincula uno antes de activar"));

            List<NodoDiagrama> nodos = nodoRepository.findByDiagramaId(diagrama.getId());
            if (nodos.isEmpty()) {
                throw new IllegalArgumentException(
                        "El diagrama no tiene nodos. Agrega al menos Inicio, una actividad y Fin antes de activar");
            }

            for (NodoDiagrama nodo : nodos) {
                if (!"decision".equals(nodo.getTipo())) continue;
                if (preguntaVacia(nodo.getNombre())) {
                    throw new IllegalArgumentException(
                            "Hay una decisión sin pregunta. Escribe la pregunta (Sí/No) en cada nodo "
                                    + "de decisión antes de activar la política.");
                }
                List<FlujoTransicion> ramas = flujoRepository.findByNodoOrigenId(nodo.getId());
                boolean tieneSi = ramas.stream().anyMatch(tr -> "si".equalsIgnoreCase(trim(tr.getEtiqueta())));
                boolean tieneNo = ramas.stream().anyMatch(tr -> "no".equalsIgnoreCase(trim(tr.getEtiqueta())));
                if (!tieneSi || !tieneNo) {
                    throw new IllegalArgumentException(
                            "La decisión \"" + nodo.getNombre() + "\" debe tener sus dos ramas conectadas "
                                    + "y etiquetadas (Sí y No) antes de activar la política.");
                }
            }

            politicaRepository.findByEstado("activa").stream()
                    .filter(activa -> activa.getNombre().equals(p.getNombre())
                            && !activa.getId().equals(id))
                    .forEach(activa -> {
                        activa.setEstado("archivada");
                        politicaRepository.save(activa);
                    });
            p.setFechaActivacion(LocalDateTime.now());
        }

        p.setEstado(nuevoEstado);
        PoliticaNegocio guardada = politicaRepository.save(p);

        sincronizarEstadoDiagrama(guardada, nuevoEstado);

        reentrenarClasificadorPoliticaAsync();
        return guardada;
    }

    private void sincronizarEstadoDiagrama(PoliticaNegocio p, String estadoPolitica) {
        if (p.getDiagramaId() == null) return;
        String estadoDiagrama = switch (estadoPolitica) {
            case "activa" -> "publicado";
            case "archivada" -> "archivado";
            default -> "borrador";
        };
        diagramaRepository.findById(p.getDiagramaId()).ifPresent(d -> {
            if (!estadoDiagrama.equals(d.getEstado())) {
                d.setEstado(estadoDiagrama);
                d.setUltimaModificacion(LocalDateTime.now());
                diagramaRepository.save(d);
            }
        });
    }

    private void reentrenarClasificadorPoliticaAsync() {
        Thread t = new Thread(() -> {
            try {
                List<PoliticaNegocio> activas = politicaRepository.findByEstado("activa");
                if (activas.size() < 2) return;
                List<Map<String, Object>> payload = new ArrayList<>();
                for (PoliticaNegocio p : activas) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", p.getId());
                    m.put("nombre", p.getNombre() != null ? p.getNombre() : "");
                    m.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "");
                    m.put("categoria", p.getCategoria() != null ? p.getCategoria() : "");
                    payload.add(m);
                }
                iaProxy.reentrenarPolitica(payload);
            } catch (RuntimeException ex) {
            }
        }, "reentrenar-clasificador-politica");
        t.setDaemon(true);
        t.start();
    }

    private boolean preguntaVacia(String nombre) {
        String norm = java.text.Normalizer
                .normalize(nombre == null ? "" : nombre.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[¿?]", "")
                .trim()
                .toLowerCase();
        return norm.isEmpty() || norm.equals("decision");
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private void validarTransicionEstado(String actual, String nuevo) {
        boolean valida = ("borrador".equals(nuevo) || "activa".equals(nuevo) || "archivada".equals(nuevo))
                && !nuevo.equals(actual);
        if (!valida) {
            throw new IllegalArgumentException(
                    String.format("Transición inválida: '%s' → '%s'", actual, nuevo));
        }
    }

    public List<ActividadDocumentosDTO> obtenerDocumentosRequeridos(String politicaId) {
        PoliticaNegocio p = politicaRepository.findById(politicaId)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        if (p.getDiagramaId() == null) return Collections.emptyList();

        return nodoRepository.findByDiagramaId(p.getDiagramaId()).stream()
                .filter(nodo -> nodo.getActividadId() != null && !nodo.getActividadId().isBlank())
                .flatMap(nodo -> actividadRepository.findById(nodo.getActividadId()).stream())
                .filter(act -> !requisitoDocumentoService.requisitosDe(act).isEmpty())
                .map(act -> {
                    List<DocumentoInfoDTO> docs = requisitoDocumentoService.requisitosDe(act).stream()
                            .flatMap(req -> documentoRepository.findById(req.getDocumentoId()).stream()
                                    .map(doc -> new DocumentoInfoDTO(doc.getId(), doc.getNombre(),
                                            doc.getDescripcion(), req.getProveedor(), req.isObligatorio())))
                            .collect(Collectors.toList());
                    return new ActividadDocumentosDTO(act.getId(), act.getNombre(), docs);
                })
                .collect(Collectors.toList());
    }

    public ActividadDocumentosDTO documentosIniciales(String politicaId) {
        PoliticaNegocio p = politicaRepository.findById(politicaId)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));
        if (p.getDiagramaId() == null) return null;

        List<NodoDiagrama> nodos = nodoRepository.findByDiagramaId(p.getDiagramaId());
        NodoDiagrama inicio = nodos.stream()
                .filter(n -> n.getTipo() != null && n.getTipo().equalsIgnoreCase("inicio"))
                .findFirst().orElse(null);
        if (inicio == null) return null;

        NodoDiagrama actNode = primeraActividadDesde(inicio, nodos);
        if (actNode == null || actNode.getActividadId() == null) return null;
        Actividad act = actividadRepository.findById(actNode.getActividadId()).orElse(null);
        if (act == null) return null;

        List<DocumentoInfoDTO> docs = requisitoDocumentoService.requisitosDe(act).stream()
                .flatMap(req -> documentoRepository.findById(req.getDocumentoId()).stream()
                        .map(d -> new DocumentoInfoDTO(d.getId(), d.getNombre(), d.getDescripcion(),
                                req.getProveedor(), req.isObligatorio())))
                .collect(Collectors.toList());
        return new ActividadDocumentosDTO(act.getId(), act.getNombre(), docs);
    }

    private NodoDiagrama primeraActividadDesde(NodoDiagrama desde, List<NodoDiagrama> nodos) {
        java.util.Set<String> visto = new java.util.HashSet<>();
        java.util.Deque<NodoDiagrama> cola = new java.util.ArrayDeque<>();
        cola.add(desde);
        while (!cola.isEmpty()) {
            NodoDiagrama n = cola.poll();
            if (n == null || !visto.add(n.getId())) continue;
            if (n.getTipo() != null && n.getTipo().equalsIgnoreCase("actividad")
                    && n.getActividadId() != null) {
                return n;
            }
            for (FlujoTransicion t : flujoRepository.findByNodoOrigenId(n.getId())) {
                nodos.stream().filter(x -> x.getId().equals(t.getNodoDestinoId()))
                        .findFirst().ifPresent(cola::add);
            }
        }
        return null;
    }

    public void eliminar(String id) {
        PoliticaNegocio p = politicaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        if ("activa".equals(p.getEstado())) {
            throw new IllegalArgumentException("No se puede eliminar una política activa. Archívala primero");
        }
        politicaRepository.deleteById(id);
    }
}
