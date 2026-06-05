package com.example.demo.services;

import com.example.demo.dto.DiagramaWorkflowRequest;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DiagramaWorkflowService {

    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;

    public DiagramaWorkflow crear(DiagramaWorkflowRequest req, String creadorId) {
        if (req.getPoliticaId() != null) {
            politicaRepository.findById(req.getPoliticaId())
                    .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));
            validarPoliticaSinDiagrama(req.getPoliticaId());
        }

        DiagramaWorkflow d = new DiagramaWorkflow();
        d.setNombre(req.getNombre());
        d.setPoliticaId(req.getPoliticaId());
        d.setCreadorId(creadorId);
        d.setSwimlanes(req.getSwimlanes());
        d.setCanvasData(req.getCanvasData());
        d.setVersionActual(1);
        d.setEstado("borrador");
        d.setGeneradoPorIa(false);
        d.setFechaCreacion(LocalDateTime.now());
        d.setUltimaModificacion(LocalDateTime.now());

        DiagramaWorkflow guardado = diagramaRepository.save(d);

        if (req.getPoliticaId() != null) {
            politicaRepository.findById(req.getPoliticaId()).ifPresent(p -> {
                p.setDiagramaId(guardado.getId());
                politicaRepository.save(p);
            });
        }

        return guardado;
    }

    public List<DiagramaWorkflow> listarTodos() {
        return diagramaRepository.findAll();
    }

    public void validarPoliticaSinDiagrama(String politicaId) {
        if (politicaTieneDiagrama(politicaId)) {
            throw new IllegalArgumentException(
                    "Esa política ya tiene un diagrama. Archívalo o elige otra política.");
        }
    }

    public boolean politicaTieneDiagrama(String politicaId) {
        return diagramaRepository.findAllByPoliticaId(politicaId).stream()
                .anyMatch(d -> !"archivado".equals(d.getEstado()));
    }

    public List<DiagramaWorkflow> listarPorEstado(String estado) {
        return diagramaRepository.findByEstado(estado);
    }

    public List<DiagramaWorkflow> listarSinPolitica() {
        return diagramaRepository.findByPoliticaIdIsNull();
    }

    public Optional<DiagramaWorkflow> buscarPorId(String id) {
        return diagramaRepository.findById(id);
    }

    public DiagramaWorkflow actualizar(String id, DiagramaWorkflowRequest req) {
        DiagramaWorkflow d = diagramaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if ("archivado".equals(d.getEstado())) {
            throw new IllegalArgumentException("No se puede editar un diagrama archivado");
        }

        d.setNombre(req.getNombre());
        d.setPoliticaId(req.getPoliticaId());
        d.setSwimlanes(req.getSwimlanes());
        d.setCanvasData(req.getCanvasData());
        d.setUltimaModificacion(LocalDateTime.now());

        return diagramaRepository.save(d);
    }

    public DiagramaWorkflow cambiarEstado(String diagramaId, String nuevoEstado) {
        DiagramaWorkflow d = diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if ("publicado".equals(nuevoEstado)) {
            validarParaPublicar(d);
        }

        d.setEstado(nuevoEstado);
        d.setUltimaModificacion(LocalDateTime.now());
        DiagramaWorkflow guardado = diagramaRepository.save(d);

        sincronizarVinculoPolitica(guardado);
        return guardado;
    }

    private void sincronizarVinculoPolitica(DiagramaWorkflow d) {
        if (d.getPoliticaId() == null) return;
        politicaRepository.findById(d.getPoliticaId()).ifPresent(p -> {
            boolean cambia = false;
            if ("archivado".equals(d.getEstado())) {
                if (d.getId().equals(p.getDiagramaId())) { p.setDiagramaId(null); cambia = true; }
            } else if (!d.getId().equals(p.getDiagramaId())) {
                p.setDiagramaId(d.getId()); cambia = true;
            }
            if (cambia) politicaRepository.save(p);
        });
    }

    private void validarParaPublicar(DiagramaWorkflow d) {
        List<NodoDiagrama> nodos = nodoRepository.findByDiagramaId(d.getId());
        if (nodos.isEmpty()) {
            throw new IllegalArgumentException("El diagrama no tiene nodos");
        }
        boolean tieneInicio = nodos.stream().anyMatch(n -> "inicio".equals(n.getTipo()));
        boolean tieneFin = nodos.stream().anyMatch(n -> "fin".equals(n.getTipo()));
        if (!tieneInicio) throw new IllegalArgumentException("Falta nodo de inicio");
        if (!tieneFin) throw new IllegalArgumentException("Falta nodo de fin");

        for (NodoDiagrama n : nodos) {
            if ("fin".equals(n.getTipo())) continue;
            if (flujoRepository.findByNodoOrigenId(n.getId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Nodo '" + n.getNombre() + "' no tiene transición saliente");
            }
        }

        java.util.Map<String, String> tipoPorNodo = new java.util.HashMap<>();
        for (NodoDiagrama n : nodos) tipoPorNodo.put(n.getId(), n.getTipo());
        for (var t : flujoRepository.findByDiagramaId(d.getId())) {
            String tipoOrigen = tipoPorNodo.get(t.getNodoOrigenId());
            if (!"decision".equals(tipoPorNodo.get(t.getNodoDestinoId()))) continue;
            if ("fork".equals(tipoOrigen)) {
                throw new IllegalArgumentException(
                        "Topología no soportada: un 'fork' conecta directo a una 'decision'. Pon una actividad entre ellos.");
            }
            if ("decision".equals(tipoOrigen)) {
                throw new IllegalArgumentException(
                        "Topología no soportada: dos 'decisiones' encadenadas. Pon una actividad entre ellas.");
            }
        }
    }

    public void eliminar(String id) {
        DiagramaWorkflow d = diagramaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if ("publicado".equals(d.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede eliminar un diagrama publicado. Archívalo primero");
        }

        if (d.getPoliticaId() != null) {
            politicaRepository.findById(d.getPoliticaId()).ifPresent(p -> {
                if (id.equals(p.getDiagramaId())) {
                    p.setDiagramaId(null);
                    politicaRepository.save(p);
                }
            });
        }

        flujoRepository.findByDiagramaId(id)
                .forEach(t -> flujoRepository.deleteById(t.getId()));
        nodoRepository.findByDiagramaId(id)
                .forEach(n -> nodoRepository.deleteById(n.getId()));
        diagramaRepository.deleteById(id);
    }
}
