package com.example.demo.services;

import com.example.demo.dto.FlujoTransicionRequest;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FlujoTransicionService {

    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;

    public FlujoTransicion agregarTransicion(String diagramaId, FlujoTransicionRequest req) {
        var diagrama = diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden agregar transiciones en diagramas 'borrador'");
        }

        NodoDiagrama origen = nodoRepository.findById(req.getNodoOrigenId())
                .orElseThrow(() -> new IllegalArgumentException("Nodo origen no encontrado"));
        NodoDiagrama destino = nodoRepository.findById(req.getNodoDestinoId())
                .orElseThrow(() -> new IllegalArgumentException("Nodo destino no encontrado"));

        if (!diagramaId.equals(origen.getDiagramaId()) || !diagramaId.equals(destino.getDiagramaId())) {
            throw new IllegalArgumentException("Origen y destino deben pertenecer al mismo diagrama");
        }

        if ("fin".equals(origen.getTipo())) {
            throw new IllegalArgumentException("Un nodo 'fin' no puede tener transiciones salientes");
        }
        if ("inicio".equals(destino.getTipo())) {
            throw new IllegalArgumentException("No se puede apuntar al nodo de inicio");
        }

        String etiquetaNormalizada = normalizarEtiqueta(req.getEtiqueta());

        if ("decision".equals(origen.getTipo())) {
            if (etiquetaNormalizada == null
                    || !(etiquetaNormalizada.equals("si") || etiquetaNormalizada.equals("no"))) {
                throw new IllegalArgumentException(
                        "Las transiciones desde 'decision' requieren etiqueta 'si' o 'no'");
            }
        }

        FlujoTransicion t = new FlujoTransicion();
        t.setDiagramaId(diagramaId);
        t.setNodoOrigenId(req.getNodoOrigenId());
        t.setNodoDestinoId(req.getNodoDestinoId());
        t.setTipo(req.getTipo());
        t.setEtiqueta(etiquetaNormalizada);
        t.setCondicion(req.getCondicion());

        return flujoRepository.save(t);
    }

    private String normalizarEtiqueta(String etiqueta) {
        if (etiqueta == null) return null;
        String trimmed = etiqueta.trim();
        if (trimmed.isEmpty()) return null;
        String sinTildes = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toLowerCase();
    }

    public FlujoTransicion actualizarTransicion(String id, FlujoTransicionRequest req) {
        FlujoTransicion existente = flujoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transicion no encontrada"));

        var diagrama = diagramaRepository.findById(existente.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden modificar transiciones en diagramas 'borrador'");
        }

        NodoDiagrama origen = nodoRepository.findById(existente.getNodoOrigenId())
                .orElseThrow(() -> new IllegalArgumentException("Nodo origen no encontrado"));

        String etiquetaNormalizada = normalizarEtiqueta(req.getEtiqueta());

        if ("decision".equals(origen.getTipo())) {
            if (etiquetaNormalizada == null
                    || !(etiquetaNormalizada.equals("si") || etiquetaNormalizada.equals("no"))) {
                throw new IllegalArgumentException(
                        "Las transiciones desde 'decision' requieren etiqueta 'si' o 'no'");
            }
        }

        existente.setTipo(req.getTipo());
        existente.setEtiqueta(etiquetaNormalizada);
        existente.setCondicion(req.getCondicion());

        return flujoRepository.save(existente);
    }

    public List<FlujoTransicion> listarPorDiagrama(String diagramaId) {
        return flujoRepository.findByDiagramaId(diagramaId);
    }

    public Optional<FlujoTransicion> buscarPorId(String id) {
        return flujoRepository.findById(id);
    }

    public void eliminar(String id) {
        FlujoTransicion t = flujoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transicion no encontrada"));

        var diagrama = diagramaRepository.findById(t.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden eliminar transiciones en diagramas 'borrador'");
        }

        flujoRepository.deleteById(id);
    }
}
