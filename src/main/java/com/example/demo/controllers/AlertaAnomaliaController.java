package com.example.demo.controllers;

import com.example.demo.models.AlertaAnomalia;
import com.example.demo.services.AlertaAnomaliaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alertas-anomalias")
@Tag(name = "IA · Anomalías",
     description = "CU-45 — anomalías detectadas en el flujo de trámites")
public class AlertaAnomaliaController {

    @Autowired
    private AlertaAnomaliaService service;

    @PostMapping("/detectar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Disparar detección de anomalías (mientras no exista el scheduler)")
    public ResponseEntity<List<AlertaAnomalia>> detectar() {
        return ResponseEntity.ok(service.detectarYPersistir());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar anomalías abiertas (no marcadas como falso positivo)")
    public ResponseEntity<List<AlertaAnomalia>> listar() {
        return ResponseEntity.ok(service.listarNoRevisadas());
    }

    @PostMapping("/{id}/marcar-falso-positivo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Marcar una anomalía como falso positivo")
    public ResponseEntity<AlertaAnomalia> marcarFalsoPositivo(@PathVariable String id,
                                                               Authentication auth) {
        return ResponseEntity.ok(service.marcarFalsoPositivo(id, auth.getName()));
    }
}
