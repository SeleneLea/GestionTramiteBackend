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
     description = "CU-32 — repositorio asociado 1:1 a cada trámite")
public class RepositorioDocumentalController {

    @Autowired
    private RepositorioDocumentalService service;

    @GetMapping("/repositorios/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RepositorioDocumental> buscar(@PathVariable String id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @GetMapping("/tramites/{tramiteId}/repositorio")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener el repositorio documental del trámite")
    public ResponseEntity<RepositorioDocumental> obtenerPorTramite(@PathVariable String tramiteId) {
        return ResponseEntity.ok(service.buscarPorTramite(tramiteId));
    }
}
