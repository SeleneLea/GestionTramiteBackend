package com.example.demo.controllers;

import com.example.demo.dto.NodoDiagramaRequest;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.services.DiagramaCollabBroadcaster;
import com.example.demo.services.NodoDiagramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Nodos del Diagrama", description = "CU-13: agregar/editar/eliminar nodos del lienzo")
public class NodoDiagramaController {

    @Autowired private NodoDiagramaService nodoService;
    @Autowired private DiagramaCollabBroadcaster collab;

    @GetMapping("/diagramas/{diagramaId}/nodos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar nodos del diagrama")
    public ResponseEntity<List<NodoDiagrama>> listar(@PathVariable String diagramaId) {
        return ResponseEntity.ok(nodoService.listarPorDiagrama(diagramaId));
    }

    @PostMapping("/diagramas/{diagramaId}/nodos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Agregar nodo al diagrama",
               description = "Tipo válido: inicio, actividad, decision, fork, join, fin")
    public ResponseEntity<NodoDiagrama> crear(@PathVariable String diagramaId,
                                               @Valid @RequestBody NodoDiagramaRequest req,
                                               Authentication auth) {
        NodoDiagrama creado = nodoService.agregarNodo(diagramaId, req);
        collab.broadcast(diagramaId, "nodo-creado", creado, auth != null ? auth.getName() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @GetMapping("/nodos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NodoDiagrama> buscar(@PathVariable String id) {
        return nodoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/nodos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<NodoDiagrama> actualizar(@PathVariable String id,
                                                    @Valid @RequestBody NodoDiagramaRequest req,
                                                    Authentication auth) {
        NodoDiagrama actualizado = nodoService.actualizar(id, req);
        collab.broadcast(actualizado.getDiagramaId(), "nodo-actualizado",
                actualizado, auth != null ? auth.getName() : null);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/nodos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id, Authentication auth) {
        // Resolvemos el diagramaId ANTES de borrar para poder informar a los demás colaboradores.
        String diagramaId = nodoService.buscarPorId(id)
                .map(NodoDiagrama::getDiagramaId)
                .orElse(null);
        nodoService.eliminar(id);
        if (diagramaId != null) {
            collab.broadcast(diagramaId, "nodo-eliminado",
                    Map.of("id", id), auth != null ? auth.getName() : null);
        }
        return ResponseEntity.noContent().build();
    }
}
