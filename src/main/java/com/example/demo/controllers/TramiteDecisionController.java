package com.example.demo.controllers;

import com.example.demo.dto.DecisionFinalRequest;
import com.example.demo.dto.DerivarTramiteRequest;
import com.example.demo.dto.DevolverTramiteRequest;
import com.example.demo.models.Tramite;
import com.example.demo.services.TramiteDecisionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tramites/{id}")
public class TramiteDecisionController {

    @Autowired
    private TramiteDecisionService decisionService;

    // "Reasignar" = pasar el trámite a otro funcionario del mismo nodo (no avanza).
    // Se mantiene "/derivar" como alias por compatibilidad con clientes existentes.
    @PostMapping({"/reasignar", "/derivar"})
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> reasignarTramite(
            @PathVariable String id,
            @RequestBody DerivarTramiteRequest request,
            Authentication authentication) {
        Tramite t = decisionService.reasignarTramite(id, request, authentication.getName());
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

    // Decisión final en JSON (rechazo, o aprobación sin documento de resolución).
    @PostMapping(value = "/decision-final", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> decisionFinal(
            @PathVariable String id,
            @RequestBody DecisionFinalRequest request,
            Authentication authentication) {
        Tramite t = decisionService.decisionFinal(id, request, authentication.getName());
        return ResponseEntity.ok(t);
    }

    // Decisión final en multipart: permite adjuntar el documento de resolución al aprobar.
    @PostMapping(value = "/decision-final", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> decisionFinalConResolucion(
            @PathVariable String id,
            @RequestParam String decision,
            @RequestParam(required = false) String justificacion,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        DecisionFinalRequest request = new DecisionFinalRequest();
        request.setDecision(decision);
        request.setJustificacion(justificacion);
        String rol = authentication.getAuthorities().stream()
                .findFirst().map(a -> a.getAuthority()).orElse("");
        Tramite t = decisionService.decisionFinal(id, request, authentication.getName(),
                archivo, rol, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(t);
    }
}
