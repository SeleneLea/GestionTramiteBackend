package com.example.demo.services;

import com.example.demo.dto.ActividadDocumentosDTO;
import com.example.demo.dto.DocumentoInfoDTO;
import com.example.demo.dto.PoliticaNegocioRequest;
import com.example.demo.models.Documento;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.DocumentoRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PoliticaNegocioService {

    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DocumentoRepository documentoRepository;

    public PoliticaNegocio crear(PoliticaNegocioRequest req, String creadorId) {
        if (politicaRepository.findByNombre(req.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una política con el nombre: " + req.getNombre());
        }

        PoliticaNegocio p = new PoliticaNegocio();
        p.setNombre(req.getNombre());
        p.setDescripcion(req.getDescripcion());
        p.setCategoria(req.getCategoria());
        p.setParametros(req.getParametros());
        p.setCreadorId(creadorId);
        p.setVersionActual(1);
        p.setEstado("borrador");
        p.setFechaCreacion(LocalDateTime.now());

        return politicaRepository.save(p);
    }

    public List<PoliticaNegocio> listarTodas() {
        return politicaRepository.findAll();
    }

    public List<PoliticaNegocio> listarActivas() {
        return politicaRepository.findByEstado("activa");
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

        return politicaRepository.save(p);
    }

    public PoliticaNegocio cambiarEstado(String id, String nuevoEstado) {
        PoliticaNegocio p = politicaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        validarTransicionEstado(p.getEstado(), nuevoEstado);

        if ("activa".equals(nuevoEstado)) {
            // No se puede activar sin diagrama publicado y con nodos
            if (p.getDiagramaId() == null) {
                throw new IllegalArgumentException(
                        "No se puede activar la política: primero asigna un diagrama de flujo");
            }
            var diagrama = diagramaRepository.findById(p.getDiagramaId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El diagrama asignado no existe. Crea o vincula uno antes de activar"));

            long nodos = nodoRepository.findByDiagramaId(diagrama.getId()).size();
            if (nodos == 0) {
                throw new IllegalArgumentException(
                        "El diagrama no tiene nodos. Agrega al menos Inicio, una actividad y Fin antes de activar");
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
        return politicaRepository.save(p);
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
                .filter(act -> act.getDocumentoIds() != null && !act.getDocumentoIds().isEmpty())
                .map(act -> {
                    List<DocumentoInfoDTO> docs = act.getDocumentoIds().stream()
                            .flatMap(docId -> documentoRepository.findById(docId).stream())
                            .map(doc -> new DocumentoInfoDTO(doc.getId(), doc.getNombre(), doc.getDescripcion()))
                            .collect(Collectors.toList());
                    return new ActividadDocumentosDTO(act.getId(), act.getNombre(), docs);
                })
                .collect(Collectors.toList());
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
