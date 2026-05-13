package com.example.demo.controllers;

import com.example.demo.dto.CompartidoConmigoResponse;
import com.example.demo.dto.InvitarColaboradorRequest;
import com.example.demo.dto.ResponderInvitacionRequest;
import com.example.demo.models.ColaboracionDiagrama;
import com.example.demo.services.ColaboracionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/colaboracion")
public class ColaboracionController {

    @Autowired
    private ColaboracionService colaboracionService;

    @PostMapping("/diagrama/{diagramaId}/invitar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ColaboracionDiagrama> invitarColaborador(
            @PathVariable String diagramaId,
            @RequestBody InvitarColaboradorRequest request,
            Authentication authentication) {
        ColaboracionDiagrama c = colaboracionService.invitar(diagramaId, request, authentication.getName());
        return ResponseEntity.ok(c);
    }

    @PostMapping("/{colaboracionId}/responder")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<ColaboracionDiagrama> responderInvitacion(
            @PathVariable String colaboracionId,
            @RequestBody ResponderInvitacionRequest request,
            Authentication authentication) {
        ColaboracionDiagrama c = colaboracionService.responderInvitacion(
                colaboracionId, request.getDecision(), authentication.getName());
        return ResponseEntity.ok(c);
    }

    /** Diagramas compartidos con el usuario autenticado (pendientes + aceptadas). */
    @GetMapping("/compartidos-conmigo")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<List<CompartidoConmigoResponse>> compartidosConmigo(
            Authentication authentication) {
        return ResponseEntity.ok(
                colaboracionService.compartidosConmigo(authentication.getName()));
    }
}
