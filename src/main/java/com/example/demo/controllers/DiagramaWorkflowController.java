package com.example.demo.controllers;

import com.example.demo.dto.DiagramaEstadoRequest;
import com.example.demo.dto.DiagramaWorkflowRequest;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.services.DiagramaWorkflowService;
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

@RestController
@RequestMapping("/api/diagramas")
@Tag(name = "Diagramas de Workflow", description = "CU-12: el administrador diseña los diagramas UML del flujo de trabajo")
public class DiagramaWorkflowController {

    @Autowired private DiagramaWorkflowService diagramaService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar diagramas",
               description = "Filtro opcional: estado=borrador|publicado|archivado")
    public ResponseEntity<List<DiagramaWorkflow>> listar(
            @RequestParam(required = false) String estado) {
        if (estado != null) {
            return ResponseEntity.ok(diagramaService.listarPorEstado(estado));
        }
        return ResponseEntity.ok(diagramaService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DiagramaWorkflow> buscar(@PathVariable String id) {
        return diagramaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear diagrama",
               description = "Inicia en estado 'borrador'. Después agrega nodos y transiciones, y publícalo.")
    public ResponseEntity<DiagramaWorkflow> crear(@Valid @RequestBody DiagramaWorkflowRequest req,
                                                   Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(diagramaService.crear(req, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DiagramaWorkflow> actualizar(@PathVariable String id,
                                                        @Valid @RequestBody DiagramaWorkflowRequest req) {
        return ResponseEntity.ok(diagramaService.actualizar(id, req));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cambiar estado del diagrama",
               description = "Al publicar, valida que tenga inicio, fin y todas las salidas conectadas.")
    public ResponseEntity<DiagramaWorkflow> cambiarEstado(@PathVariable String id,
                                                           @Valid @RequestBody DiagramaEstadoRequest req) {
        return ResponseEntity.ok(diagramaService.cambiarEstado(id, req.getEstado()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Eliminar diagrama",
               description = "Solo se puede eliminar si NO está publicado. Borra en cascada nodos y transiciones.")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        diagramaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
