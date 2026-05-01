package com.example.demo.controllers;

import com.example.demo.dto.DecisionFinalRequest;
import com.example.demo.dto.DerivarTramiteRequest;
import com.example.demo.dto.DevolverTramiteRequest;
import com.example.demo.models.Tramite;
import com.example.demo.services.TramiteDecisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tramites/{id}")
public class TramiteDecisionController {

    @Autowired
    private TramiteDecisionService decisionService;

    @PostMapping("/derivar")
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> derivarTramite(
            @PathVariable String id,
            @RequestBody DerivarTramiteRequest request,
            Authentication authentication) {
        Tramite t = decisionService.derivarTramite(id, request, authentication.getName());
        return ResponseEntity.ok(t);
    }

    @PostMapping("/devolver")
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> devolverTramite(
            @PathVariable String id,
            @RequestBody DevolverTramiteRequest request,
            Authentication authentication) {
        Tramite t = decisionService.devolverTramite(id, request, authentication.getName());
        return ResponseEntity.ok(t);
    }

    @PostMapping("/decision-final")
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> decisionFinal(
            @PathVariable String id,
            @RequestBody DecisionFinalRequest request,
            Authentication authentication) {
        Tramite t = decisionService.decisionFinal(id, request, authentication.getName());
        return ResponseEntity.ok(t);
    }
}
