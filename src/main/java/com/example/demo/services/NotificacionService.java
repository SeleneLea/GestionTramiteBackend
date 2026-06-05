package com.example.demo.services;

import com.example.demo.models.Notificacion;
import com.example.demo.repositories.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private SseService sseService;

    public Notificacion crearNotificacion(String destinatarioId, String tramiteId, String tipo,
                                          String titulo, String mensaje, String canal) {
        Notificacion n = new Notificacion();
        n.setDestinatarioId(destinatarioId);
        n.setTramiteId(tramiteId);
        n.setCanal(canal);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        n.setLeida(false);
        n.setEstadoEnvio("web".equals(canal) ? "enviada" : "pendiente");
        n.setIntentosEnvio(0);
        n.setFechaCreacion(LocalDateTime.now());
        Notificacion guardada = notificacionRepository.save(n);

        sseService.enviar(destinatarioId, "notificacion", guardada);

        return guardada;
    }

    @Scheduled(fixedRate = 60000)
    public void procesarNotificacionesPendientes() {
        List<Notificacion> pendientes = notificacionRepository
                .findByEstadoEnvioAndIntentosEnvioLessThan("pendiente", 3);

        for (Notificacion notif : pendientes) {
            boolean exito = false;
            try {
                if ("email".equals(notif.getCanal())) {
                    exito = enviarEmailSimulado(notif);
                } else if ("push".equals(notif.getCanal())) {
                    exito = enviarPushSimulado(notif);
                }
            } catch (Exception ex) {
                exito = false;
            }

            notif.setIntentosEnvio(notif.getIntentosEnvio() + 1);
            if (exito) {
                notif.setEstadoEnvio("enviada");
            } else if (notif.getIntentosEnvio() >= 3) {
                notif.setEstadoEnvio("fallida");
            }
            notificacionRepository.save(notif);
        }
    }

    private boolean enviarEmailSimulado(Notificacion notif) {
        System.out.println("Enviando EMAIL a " + notif.getDestinatarioId() + ": " + notif.getTitulo());
        return true;
    }

    private boolean enviarPushSimulado(Notificacion notif) {
        System.out.println("Enviando PUSH a " + notif.getDestinatarioId() + ": " + notif.getTitulo());
        return true;
    }

    public Notificacion marcarComoLeida(String id, String usuarioId) {
        Notificacion n = notificacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notificacion no encontrada"));
        if (!usuarioId.equals(n.getDestinatarioId())) {
            throw new IllegalArgumentException("No autorizado");
        }
        n.setLeida(true);
        n.setFechaLeida(LocalDateTime.now());
        return notificacionRepository.save(n);
    }
}
