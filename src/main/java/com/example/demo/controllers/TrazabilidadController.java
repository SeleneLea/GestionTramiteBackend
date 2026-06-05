package com.example.demo.controllers;

import com.example.demo.dto.VerificacionCadenaResponse;
import com.example.demo.services.TrazabilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trazabilidad")
public class TrazabilidadController {

    @Autowired
    private TrazabilidadService trazabilidadService;

    @GetMapping("/{tramiteId}/verificar")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<VerificacionCadenaResponse> verificarCadena(@PathVariable String tramiteId) {
        return ResponseEntity.ok(trazabilidadService.verificarCadena(tramiteId));
    }
}
