package com.example.demo.controllers;

import com.example.demo.dto.ReporteNaturalRequest;
import com.example.demo.dto.ReporteNaturalResponse;
import com.example.demo.services.ReporteNaturalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
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

    @PostMapping("/consulta-natural/exportar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Exportar el reporte de una consulta natural a Excel o PDF")
    public ResponseEntity<byte[]> exportar(@Valid @RequestBody ReporteNaturalRequest req,
                                           @RequestParam(defaultValue = "xlsx") String formato) {
        byte[] datos = service.exportar(req.getConsulta(), formato);
        boolean pdf = "pdf".equalsIgnoreCase(formato);
        MediaType tipo = pdf ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = "reporte." + (pdf ? "pdf" : "xlsx");
        return ResponseEntity.ok()
                .contentType(tipo)
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(datos);
    }
}
