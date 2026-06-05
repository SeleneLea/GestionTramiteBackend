package com.example.demo.controllers;

import com.example.demo.dto.NodoDiagramaRequest;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.services.ColaboracionService;
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
    @Autowired private ColaboracionService colaboracionService;

    @GetMapping("/diagramas/{diagramaId}/nodos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar nodos del diagrama")
    public ResponseEntity<List<NodoDiagrama>> listar(@PathVariable String diagramaId) {
        return ResponseEntity.ok(nodoService.listarPorDiagrama(diagramaId));
    }

    @PostMapping("/diagramas/{diagramaId}/nodos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Agregar nodo al diagrama",
               description = "Tipo válido: inicio, actividad, decision, fork, join, fin")
    public ResponseEntity<NodoDiagrama> crear(@PathVariable String diagramaId,
                                               @Valid @RequestBody NodoDiagramaRequest req,
                                               Authentication auth) {
        validarEdicion(diagramaId, auth);
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
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Reemplazar un nodo (requiere el nodo completo)")
    public ResponseEntity<NodoDiagrama> actualizar(@PathVariable String id,
                                                    @Valid @RequestBody NodoDiagramaRequest req,
                                                    Authentication auth) {
        validarEdicionDeNodo(id, auth);
        NodoDiagrama actualizado = nodoService.actualizar(id, req);
        collab.broadcast(actualizado.getDiagramaId(), "nodo-actualizado",
                actualizado, auth != null ? auth.getName() : null);
        return ResponseEntity.ok(actualizado);
    }

    @PatchMapping("/nodos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Actualización parcial de un nodo",
               description = "Solo actualiza los campos enviados (merge). Ideal para cambios "
                       + "incrementales del editor, p.ej. mover un nodo (solo 'posicion').")
    public ResponseEntity<NodoDiagrama> actualizarParcial(
            @PathVariable String id,
            @Valid @RequestBody com.example.demo.dto.NodoDiagramaPatchRequest req,
            Authentication auth) {
        validarEdicionDeNodo(id, auth);
        NodoDiagrama actualizado = nodoService.actualizarParcial(id, req);
        collab.broadcast(actualizado.getDiagramaId(), "nodo-actualizado",
                actualizado, auth != null ? auth.getName() : null);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/nodos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    public ResponseEntity<Void> eliminar(@PathVariable String id, Authentication auth) {
        String diagramaId = nodoService.buscarPorId(id)
                .map(NodoDiagrama::getDiagramaId)
                .orElse(null);
        if (diagramaId != null) {
            validarEdicion(diagramaId, auth);
        }
        nodoService.eliminar(id);
        if (diagramaId != null) {
            collab.broadcast(diagramaId, "nodo-eliminado",
                    Map.of("id", id), auth != null ? auth.getName() : null);
        }
        return ResponseEntity.noContent().build();
    }

    private void validarEdicionDeNodo(String nodoId, Authentication auth) {
        nodoService.buscarPorId(nodoId)
                .map(NodoDiagrama::getDiagramaId)
                .ifPresent(diagramaId -> validarEdicion(diagramaId, auth));
    }

    private void validarEdicion(String diagramaId, Authentication auth) {
        if (esAdmin(auth)) return;
        colaboracionService.validarEditorDelDiagrama(diagramaId, auth != null ? auth.getName() : null);
    }

    private boolean esAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("ADMINISTRADOR"));
    }
}
