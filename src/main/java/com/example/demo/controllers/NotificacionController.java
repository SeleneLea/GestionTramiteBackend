package com.example.demo.controllers;

import com.example.demo.models.Notificacion;
import com.example.demo.repositories.NotificacionRepository;
import com.example.demo.services.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private NotificacionService notificacionService;

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
}
