package com.example.demo.controllers;

import com.example.demo.models.Notificacion;
import com.example.demo.repositories.NotificacionRepository;
import com.example.demo.services.NotificacionService;
import com.example.demo.services.SseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private SseService sseService;

    @GetMapping("/mis-notificaciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Notificacion>> getMisNotificaciones(Authentication authentication) {
        List<Notificacion> notificaciones = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(authentication.getName());
        return ResponseEntity.ok(notificaciones);
    }

    @PutMapping("/{id}/marcar-leida")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Notificacion> marcarLeida(@PathVariable String id, Authentication authentication) {
        Notificacion n = notificacionService.marcarComoLeida(id, authentication.getName());
        return ResponseEntity.ok(n);
    }

    /**
     * CU-28: stream Server-Sent Events. Reemplaza el polling de 30s del cliente
     * móvil — las notificaciones llegan en cuanto se crean en el backend.
     * El cliente debe reconectar automáticamente si la conexión se cierra.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(Authentication authentication) {
        return sseService.abrirStream(authentication.getName());
    }
}
