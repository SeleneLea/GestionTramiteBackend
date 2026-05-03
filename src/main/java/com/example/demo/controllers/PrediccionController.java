package com.example.demo.controllers;

import com.example.demo.dto.RutaOptimaResponse;
import com.example.demo.dto.TramiteRiesgoResponse;
import com.example.demo.services.PrediccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tramites")
@Tag(name = "IA · Predicción",
     description = "CU-42 (ruta óptima) y CU-43 (riesgo de demora) — proxy al microservicio IA")
public class PrediccionController {

    @Autowired
    private PrediccionService service;

    @PostMapping("/{id}/ruta-optima")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "CU-42 — Calcular y persistir la ruta sugerida para el trámite")
    public ResponseEntity<RutaOptimaResponse> rutaOptima(@PathVariable String id) {
        return ResponseEntity.ok(service.calcularRutaOptima(id));
    }

    @GetMapping("/en-riesgo")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(summary = "CU-43 — Trámites en riesgo de superar el SLA",
               description = "Calcula el riesgo de todos los trámites activos (filtrable por nivel)")
    public ResponseEntity<List<TramiteRiesgoResponse>> enRiesgo(
            @RequestParam(required = false) String nivel) {
        return ResponseEntity.ok(service.calcularRiesgoTramitesActivos(nivel));
    }
}
