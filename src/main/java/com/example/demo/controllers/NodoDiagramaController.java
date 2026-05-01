package com.example.demo.controllers;

import com.example.demo.dto.NodoDiagramaRequest;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.services.NodoDiagramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Nodos del Diagrama", description = "CU-13: agregar/editar/eliminar nodos del lienzo")
public class NodoDiagramaController {

    @Autowired private NodoDiagramaService nodoService;

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
                                               @Valid @RequestBody NodoDiagramaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nodoService.agregarNodo(diagramaId, req));
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
                                                    @Valid @RequestBody NodoDiagramaRequest req) {
        return ResponseEntity.ok(nodoService.actualizar(id, req));
    }

    @DeleteMapping("/nodos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        nodoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
