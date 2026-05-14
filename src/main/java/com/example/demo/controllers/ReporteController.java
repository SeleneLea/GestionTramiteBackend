package com.example.demo.controllers;

import com.example.demo.dto.ReporteRequest;
import com.example.demo.models.Reporte;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.services.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private ReporteRepository reporteRepository;

    @PostMapping("/generar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Reporte> generarReporte(@RequestBody ReporteRequest request,
                                                  Authentication authentication) throws Exception {
        Reporte r = reporteService.generarReporte(request, authentication.getName());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/{id}/descargar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> descargarReporte(@PathVariable String id) throws Exception {
        byte[] archivo = reporteService.descargarReporte(id);

        Reporte reporte = reporteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado: " + id));

        String formato = reporte.getFormato();
        String extension;
        String contentType;
        if ("EXCEL".equalsIgnoreCase(formato) || "XLSX".equalsIgnoreCase(formato)) {
            extension = "xlsx";
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if ("PDF".equalsIgnoreCase(formato)) {
            extension = "pdf";
            contentType = "application/pdf";
        } else {
            extension = "csv";
            contentType = "text/csv";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte_" + id + "." + extension + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(archivo);
    }
}
