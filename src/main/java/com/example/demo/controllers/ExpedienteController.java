package com.example.demo.controllers;

import com.example.demo.dto.CompletarSeccionRequest;
import com.example.demo.dto.GuardarSeccionRequest;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.services.ExpedienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/expedientes")
@Tag(name = "Expediente Digital", description = "CU-10 Revisar expediente y CU-16 registrar informe")
public class ExpedienteController {

    @Autowired
    private ExpedienteService expedienteService;

    @GetMapping("/tramite/{tramiteId}")
    @Operation(summary = "Obtener expediente completo por tramite")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR', 'CLIENTE')")
    public ResponseEntity<Map<String, Object>> getExpediente(@PathVariable String tramiteId) {
        return ResponseEntity.ok(expedienteService.obtenerExpedienteCompleto(tramiteId));
    }

    @PutMapping("/seccion/{seccionId}")
    @Operation(summary = "Guardar seccion en borrador")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    public ResponseEntity<SeccionExpediente> guardarSeccionBorrador(
            @PathVariable String seccionId,
            @RequestBody GuardarSeccionRequest request,
            Authentication authentication) {

        String usuarioId = authentication.getName();
        return ResponseEntity.ok(expedienteService.guardarSeccion(seccionId, request, usuarioId));
    }

    @PostMapping("/seccion/{seccionId}/completar")
    @Operation(summary = "Completar seccion y avanzar workflow")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    public ResponseEntity<?> completarSeccion(
            @PathVariable String seccionId,
            @RequestBody CompletarSeccionRequest request,
            Authentication authentication) {

        String usuarioId = authentication.getName();
        return ResponseEntity.ok(expedienteService.completarSeccionYAvanzar(seccionId, request, usuarioId));
    }
}
