package com.example.demo.services;

import com.example.demo.dto.NodoDiagramaRequest;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NodoDiagramaService {

    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;

    public NodoDiagrama agregarNodo(String diagramaId, NodoDiagramaRequest req) {
        var diagrama = diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede modificar un diagrama en estado 'borrador'");
        }

        if ("actividad".equals(req.getTipo())) {
            if (req.getDepartamentoId() == null) {
                throw new IllegalArgumentException("Un nodo 'actividad' requiere departamentoId");
            }
            departamentoRepository.findById(req.getDepartamentoId())
                    .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));
        }

        if ("inicio".equals(req.getTipo())) {
            if (!nodoRepository.findByDiagramaIdAndTipo(diagramaId, "inicio").isEmpty()) {
                throw new IllegalArgumentException("Ya existe un nodo de inicio en este diagrama");
            }
        }

        NodoDiagrama n = new NodoDiagrama();
        n.setDiagramaId(diagramaId);
        n.setTipo(req.getTipo());
        n.setNombre(req.getNombre());
        n.setActividadId(req.getActividadId());
        n.setDepartamentoId(req.getDepartamentoId());
        n.setSwimlane(req.getSwimlane());
        n.setFormularioPlantillaId(req.getFormularioPlantillaId());
        n.setPosicion(req.getPosicion());
        n.setOrden(req.getOrden());

        return nodoRepository.save(n);
    }

    public List<NodoDiagrama> listarPorDiagrama(String diagramaId) {
        return nodoRepository.findByDiagramaId(diagramaId);
    }

    public Optional<NodoDiagrama> buscarPorId(String id) {
        return nodoRepository.findById(id);
    }

    public NodoDiagrama actualizar(String id, NodoDiagramaRequest req) {
        NodoDiagrama n = nodoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nodo no encontrado"));

        var diagrama = diagramaRepository.findById(n.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama del nodo no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede modificar nodos de un diagrama en 'borrador'");
        }

        n.setNombre(req.getNombre());
        n.setTipo(req.getTipo());
        n.setActividadId(req.getActividadId());
        n.setDepartamentoId(req.getDepartamentoId());
        n.setSwimlane(req.getSwimlane());
        n.setFormularioPlantillaId(req.getFormularioPlantillaId());
        n.setPosicion(req.getPosicion());
        n.setOrden(req.getOrden());

        return nodoRepository.save(n);
    }

    public NodoDiagrama actualizarParcial(String id, com.example.demo.dto.NodoDiagramaPatchRequest req) {
        NodoDiagrama n = nodoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nodo no encontrado"));

        var diagrama = diagramaRepository.findById(n.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama del nodo no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede modificar nodos de un diagrama en 'borrador'");
        }

        if (req.getNombre() != null) n.setNombre(req.getNombre());
        if (req.getTipo() != null) n.setTipo(req.getTipo());
        if (req.getActividadId() != null) n.setActividadId(req.getActividadId());
        if (req.getDepartamentoId() != null) n.setDepartamentoId(req.getDepartamentoId());
        if (req.getSwimlane() != null) n.setSwimlane(req.getSwimlane());
        if (req.getFormularioPlantillaId() != null) n.setFormularioPlantillaId(req.getFormularioPlantillaId());
        if (req.getPosicion() != null) n.setPosicion(req.getPosicion());
        if (req.getOrden() != null) n.setOrden(req.getOrden());

        return nodoRepository.save(n);
    }

    public void eliminar(String id) {
        NodoDiagrama n = nodoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nodo no encontrado"));

        var diagrama = diagramaRepository.findById(n.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede eliminar nodos de un diagrama en 'borrador'");
        }

        flujoRepository.findByNodoOrigenId(id)
                .forEach(t -> flujoRepository.deleteById(t.getId()));
        flujoRepository.findByNodoDestinoId(id)
                .forEach(t -> flujoRepository.deleteById(t.getId()));

        nodoRepository.deleteById(id);
    }
}
