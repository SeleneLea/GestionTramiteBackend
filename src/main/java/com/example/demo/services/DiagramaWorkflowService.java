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

        // Backfill: asociar el diagrama a la política para que pueda activarse
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
        return diagramaRepository.save(d);
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
    }

    public void eliminar(String id) {
        DiagramaWorkflow d = diagramaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if ("publicado".equals(d.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede eliminar un diagrama publicado. Archívalo primero");
        }

        flujoRepository.findByDiagramaId(id)
                .forEach(t -> flujoRepository.deleteById(t.getId()));
        nodoRepository.findByDiagramaId(id)
                .forEach(n -> nodoRepository.deleteById(n.getId()));
        diagramaRepository.deleteById(id);
    }
}
