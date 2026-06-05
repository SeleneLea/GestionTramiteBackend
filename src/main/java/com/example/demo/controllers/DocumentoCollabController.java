package com.example.demo.controllers;

import com.example.demo.models.SesionEdicionDocumento;
import com.example.demo.services.EdicionColaborativaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping
@Tag(name = "Edición colaborativa de documentos",
     description = "CU-38 — STOMP /app/documento/{id}/* — ver SwaggerDoc en WebSocketConfig")
public class DocumentoCollabController {

    @Autowired
    private EdicionColaborativaService service;

    @MessageMapping("/documento/{id}/join")
    public void join(@DestinationVariable("id") String documentoId,
                     SimpMessageHeaderAccessor headers) {
        Ctx ctx = ctx(headers);
        if (ctx == null) return;
        service.unirse(documentoId, ctx.userId, ctx.rol);
    }

    @MessageMapping("/documento/{id}/leave")
    public void leave(@DestinationVariable("id") String documentoId,
                      SimpMessageHeaderAccessor headers) {
        Ctx ctx = ctx(headers);
        if (ctx == null) return;
        service.salir(documentoId, ctx.userId, ctx.rol);
    }

    @MessageMapping("/documento/{id}/op")
    public void op(@DestinationVariable("id") String documentoId,
                   @Payload Map<String, Object> op,
                   SimpMessageHeaderAccessor headers) {
        Ctx ctx = ctx(headers);
        if (ctx == null) return;
        service.aplicarOp(documentoId, ctx.userId, ctx.rol, op);
    }

    @MessageMapping("/documento/{id}/cursor")
    public void cursor(@DestinationVariable("id") String documentoId,
                       @Payload Map<String, Object> payload,
                       SimpMessageHeaderAccessor headers) {
        Ctx ctx = ctx(headers);
        if (ctx == null) return;
        service.actualizarCursor(documentoId, ctx.userId, payload);
    }

    @GetMapping("/api/documentos/{id}/sesion-edicion")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Roster actual de la sesión de edición (CU-38)")
    @ResponseBody
    public ResponseEntity<List<SesionEdicionDocumento.Participante>> roster(@PathVariable("id") String documentoId) {
        return ResponseEntity.ok(service.participantes(documentoId));
    }

    private Ctx ctx(SimpMessageHeaderAccessor headers) {
        var attrs = headers.getSessionAttributes();
        if (attrs == null) return null;
        Object userId = attrs.get("userId");
        Object rol = attrs.get("rol");
        if (userId == null) return null;
        return new Ctx(userId.toString(),
                rol != null ? "ROLE_" + rol.toString().toUpperCase() : "ROLE_USUARIO");
    }

    private record Ctx(String userId, String rol) {}
}
