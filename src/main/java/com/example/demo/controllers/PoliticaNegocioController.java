package com.example.demo.controllers;

import com.example.demo.dto.ActividadDocumentosDTO;
import com.example.demo.dto.PoliticaEstadoRequest;
import com.example.demo.dto.PoliticaNegocioRequest;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.services.PoliticaNegocioService;
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
@RequestMapping("/api/politicas")
@Tag(name = "Políticas de Negocio",
    description = "Estados: borrador, activa, archivada (se permite cambiar entre cualquier estado distinto)")
public class PoliticaNegocioController {

    @Autowired
    private PoliticaNegocioService politicaService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar políticas", description = "Parámetro opcional: soloActivas=true para filtrar solo activas")
    public ResponseEntity<List<PoliticaNegocio>> listar(
            @RequestParam(required = false, defaultValue = "false") boolean soloActivas) {
        if (soloActivas) {
            return ResponseEntity.ok(politicaService.listarActivas());
        }
        return ResponseEntity.ok(politicaService.listarTodas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Buscar política por ID")
    public ResponseEntity<PoliticaNegocio> buscar(@PathVariable String id) {
        return politicaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear política", description = "Inicia en estado 'borrador'. El creador es registrado automáticamente.")
    public ResponseEntity<PoliticaNegocio> crear(@Valid @RequestBody PoliticaNegocioRequest req,
                                                  Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(politicaService.crear(req, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualizar política", description = "No se puede actualizar si ya está activa o archivada")
    public ResponseEntity<PoliticaNegocio> actualizar(@PathVariable String id,
                                                       @Valid @RequestBody PoliticaNegocioRequest req) {
        return ResponseEntity.ok(politicaService.actualizar(id, req));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cambiar estado de política",
               description = "Permite cambiar entre cualquier estado válido distinto del actual. Al activar, archiva automáticamente cualquier versión anterior activa con el mismo nombre.")
    public ResponseEntity<PoliticaNegocio> cambiarEstado(@PathVariable String id,
                                                          @Valid @RequestBody PoliticaEstadoRequest req) {
        return ResponseEntity.ok(politicaService.cambiarEstado(id, req.getEstado()));
    }

    @GetMapping("/{id}/documentos-requeridos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Documentos requeridos por actividad",
               description = "Retorna la lista de documentos que el ciudadano debe subir, agrupados por actividad")
    public ResponseEntity<List<ActividadDocumentosDTO>> documentosRequeridos(@PathVariable String id) {
        return ResponseEntity.ok(politicaService.obtenerDocumentosRequeridos(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Eliminar política", description = "Solo se pueden eliminar políticas en estado 'borrador' o 'archivada'")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        politicaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
