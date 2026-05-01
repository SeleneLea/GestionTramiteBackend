package com.example.demo.controllers;

import com.example.demo.dto.FlujoTransicionRequest;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.services.FlujoTransicionService;
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
@Tag(name = "Transiciones del Diagrama", description = "CU-13: conectar nodos del lienzo con flechas")
public class FlujoTransicionController {

    @Autowired private FlujoTransicionService flujoService;

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
                                                  @Valid @RequestBody FlujoTransicionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flujoService.agregarTransicion(diagramaId, req));
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
                                                       @Valid @RequestBody FlujoTransicionRequest req) {
        return ResponseEntity.ok(flujoService.actualizarTransicion(id, req));
    }

    @DeleteMapping("/transiciones/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        flujoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
