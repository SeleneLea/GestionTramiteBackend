package com.example.demo.controllers;

import com.example.demo.dto.ConfirmarSugerenciaRequest;
import com.example.demo.dto.SugerirPoliticaRequest;
import com.example.demo.dto.SugerirPoliticaResponse;
import com.example.demo.models.SugerenciaPolitica;
import com.example.demo.services.SugerenciaPoliticaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "IA · Sugerir política",
     description = "CU-40 — el cliente describe su situación y la IA propone la política")
public class SugerenciaPoliticaController {

    @Autowired
    private SugerenciaPoliticaService service;

    @PostMapping("/tramites/sugerir-politica")
    @PreAuthorize("hasAnyRole('CLIENTE','FUNCIONARIO','ADMINISTRADOR')")
    @Operation(summary = "Pedir sugerencia de política a partir de una descripción libre")
    public ResponseEntity<SugerirPoliticaResponse> sugerir(
            @Valid @RequestBody SugerirPoliticaRequest req,
            Authentication auth) {
        return ResponseEntity.ok(service.sugerir(req, auth.getName()));
    }

    @PostMapping("/sugerencias/{id}/confirmar")
    @PreAuthorize("hasAnyRole('CLIENTE','FUNCIONARIO','ADMINISTRADOR')")
    @Operation(summary = "Confirmar la política elegida (puede ser distinta a la sugerida)")
    public ResponseEntity<SugerenciaPolitica> confirmar(
            @PathVariable String id,
            @Valid @RequestBody ConfirmarSugerenciaRequest req) {
        return ResponseEntity.ok(service.confirmar(id, req.getPoliticaConfirmadaId(), null));
    }

    @PostMapping("/sugerencias/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE','FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<SugerenciaPolitica> cancelar(@PathVariable String id) {
        return ResponseEntity.ok(service.cancelar(id));
    }
}
