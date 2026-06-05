package com.example.demo.controllers;

import com.example.demo.services.OnlyOfficeService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/documentos")
public class OnlyOfficeController {

    @Autowired private OnlyOfficeService onlyOffice;

    @GetMapping("/{id}/onlyoffice/config")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(summary = "Config del editor OnlyOffice para co-editar un documento Office")
    public ResponseEntity<Map<String, Object>> config(@PathVariable String id, Authentication auth) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("serverUrl", onlyOffice.serverUrl());
        resp.put("config", onlyOffice.construirConfig(id, auth.getName()));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/onlyoffice/callback")
    public ResponseEntity<Map<String, Object>> callback(@PathVariable String id,
                                                        @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(onlyOffice.procesarCallback(id, body));
    }
}
