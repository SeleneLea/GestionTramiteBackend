package com.example.demo.controllers;

import com.example.demo.dto.FlujoTransicionRequest;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.services.DiagramaCollabBroadcaster;
import com.example.demo.services.FlujoTransicionService;
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
@Tag(name = "Transiciones del Diagrama", description = "CU-13: conectar nodos del lienzo con flechas")
public class FlujoTransicionController {

    @Autowired private FlujoTransicionService flujoService;
    @Autowired private DiagramaCollabBroadcaster collab;

    @GetMapping("/diagramas/{diagramaId}/transiciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FlujoTransicion>> listar(@PathVariable String diagramaId) {
        return ResponseEntity.ok(flujoService.listarPorDiagrama(diagramaId));
    }

    @PostMapping("/diagramas/{diagramaId}/transiciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Conectar dos nodos",
               description = "Tipo: secuencial, condicional, paralelo o iterativo. Para 'decision' la etiqueta debe ser 'si' o 'no'.")
    public ResponseEntity<FlujoTransicion> crear(@PathVariable String diagramaId,
                                                  @Valid @RequestBody FlujoTransicionRequest req,
                                                  Authentication auth) {
        FlujoTransicion creada = flujoService.agregarTransicion(diagramaId, req);
        collab.broadcast(diagramaId, "trans-creada", creada, auth != null ? auth.getName() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping("/transiciones/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FlujoTransicion> buscar(@PathVariable String id) {
        return flujoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/transiciones/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualizar transición (etiqueta, tipo, condición)")
    public ResponseEntity<FlujoTransicion> actualizar(@PathVariable String id,
                                                       @Valid @RequestBody FlujoTransicionRequest req,
                                                       Authentication auth) {
        FlujoTransicion actualizada = flujoService.actualizarTransicion(id, req);
        collab.broadcast(actualizada.getDiagramaId(), "trans-actualizada",
                actualizada, auth != null ? auth.getName() : null);
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/transiciones/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id, Authentication auth) {
        String diagramaId = flujoService.buscarPorId(id)
                .map(FlujoTransicion::getDiagramaId)
                .orElse(null);
        flujoService.eliminar(id);
        if (diagramaId != null) {
            collab.broadcast(diagramaId, "trans-eliminada",
                    Map.of("id", id), auth != null ? auth.getName() : null);
        }
        return ResponseEntity.noContent().build();
    }
}
