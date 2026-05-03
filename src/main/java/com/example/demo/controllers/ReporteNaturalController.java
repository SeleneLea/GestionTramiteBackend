package com.example.demo.controllers;

import com.example.demo.dto.ReporteNaturalRequest;
import com.example.demo.dto.ReporteNaturalResponse;
import com.example.demo.services.ReporteNaturalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
@Tag(name = "IA · Reportes ad-hoc",
     description = "CU-41 — administrador pide reportes en lenguaje natural")
public class ReporteNaturalController {

    @Autowired
    private ReporteNaturalService service;

    @PostMapping("/consulta-natural")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Generar reporte a partir de una consulta natural")
    public ResponseEntity<ReporteNaturalResponse> generar(@Valid @RequestBody ReporteNaturalRequest req,
                                                           Authentication auth) {
        return ResponseEntity.ok(service.generar(req, auth.getName()));
    }
}
