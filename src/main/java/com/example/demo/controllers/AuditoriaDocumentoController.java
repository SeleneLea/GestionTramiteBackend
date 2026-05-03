package com.example.demo.controllers;

import com.example.demo.dto.AuditoriaItemResponse;
import com.example.demo.services.AuditoriaDocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/documentos")
@Tag(name = "Auditoría documental",
     description = "CU-37 — quién leyó, subió, modificó cada documento")
public class AuditoriaDocumentoController {

    @Autowired
    private AuditoriaDocumentoService service;

    @GetMapping("/{documentoId}/auditoria")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Timeline de auditoría del documento (paginado)")
    public ResponseEntity<Page<AuditoriaItemResponse>> listar(
            @PathVariable String documentoId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<AuditoriaItemResponse> resultado;
        if (desde != null && hasta != null) {
            resultado = service.listarPorDocumentoEntreFechas(documentoId, desde, hasta, page, size);
        } else {
            resultado = service.listarPorDocumento(documentoId, page, size);
        }
        return ResponseEntity.ok(resultado);
    }
}
