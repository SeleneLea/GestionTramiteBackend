package com.example.demo.controllers;

import com.example.demo.models.RepositorioDocumental;
import com.example.demo.services.RepositorioDocumentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Repositorios documentales",
     description = "CU-32 — repositorio asociado 1:1 a cada política")
public class RepositorioDocumentalController {

    @Autowired
    private RepositorioDocumentalService service;

    @PostMapping("/politicas/{politicaId}/repositorio")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear repositorio (reintento manual)",
               description = "Idempotente. El flujo normal lo crea automáticamente al hacer POST /api/politicas. Este endpoint sirve para reintentar si la creación auto falló (p. ej. S3 caído).")
    public ResponseEntity<RepositorioDocumental> crear(@PathVariable String politicaId) {
        return ResponseEntity.ok(service.crearAlGuardarPolitica(politicaId));
    }

    @GetMapping("/politicas/{politicaId}/repositorio")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RepositorioDocumental> obtener(@PathVariable String politicaId) {
        return ResponseEntity.ok(service.buscarPorPolitica(politicaId));
    }

    @GetMapping("/repositorios/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RepositorioDocumental> buscar(@PathVariable String id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }
}
