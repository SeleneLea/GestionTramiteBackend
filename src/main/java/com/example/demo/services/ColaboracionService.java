package com.example.demo.services;

import com.example.demo.dto.InvitarColaboradorRequest;
import com.example.demo.models.ColaboracionDiagrama;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.Notificacion;
import com.example.demo.repositories.ColaboracionDiagramaRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ColaboracionService {

    @Autowired
    private ColaboracionDiagramaRepository colaboracionRepo;

    @Autowired
    private DiagramaWorkflowRepository diagramaRepo;

    @Autowired
    private NotificacionRepository notificacionRepo;

    public ColaboracionDiagrama invitar(String diagramaId, InvitarColaboradorRequest request, String adminId) {
        DiagramaWorkflow diagrama = diagramaRepo.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado: " + diagramaId));

        long activas = colaboracionRepo.countByInvitadoIdAndEstado(request.getUsuarioInvitadoId(), "aceptada");
        if (activas >= 4) {
            throw new IllegalArgumentException("El usuario ya tiene el maximo permitido de 4 diagramas en edicion activa.");
        }

        ColaboracionDiagrama colab = new ColaboracionDiagrama();
        colab.setDiagramaId(diagramaId);
        colab.setAdminInvitadorId(adminId);
        colab.setInvitadoId(request.getUsuarioInvitadoId());
        colab.setRolColaboracion(request.getPermisos() != null ? request.getPermisos() : "editor");
        colab.setEstado("pendiente");
        colab.setFechaInvitacion(LocalDateTime.now());
        colab = colaboracionRepo.save(colab);

        Notificacion notif = new Notificacion();
        notif.setDestinatarioId(request.getUsuarioInvitadoId());
        notif.setTitulo("Invitacion a colaborar");
        notif.setMensaje("Has sido invitado a colaborar en el diagrama: " + diagrama.getNombre());
        notif.setTipo("asignacion");
        notif.setCanal("web");
        notif.setLeida(false);
        notif.setEstadoEnvio("enviada");
        notif.setIntentosEnvio(0);
        notif.setFechaCreacion(LocalDateTime.now());
        notificacionRepo.save(notif);

        return colab;
    }

    public ColaboracionDiagrama responderInvitacion(String colaboracionId, String decision, String usuarioId) {
        ColaboracionDiagrama colab = colaboracionRepo.findById(colaboracionId)
                .orElseThrow(() -> new IllegalArgumentException("Colaboracion no encontrada: " + colaboracionId));

        if (!colab.getInvitadoId().equals(usuarioId)) {
            throw new IllegalArgumentException("No tienes permiso para responder a esta invitacion.");
        }

        if ("ACEPTAR".equalsIgnoreCase(decision)) {
            long activas = colaboracionRepo.countByInvitadoIdAndEstado(usuarioId, "aceptada");
            if (activas >= 4) {
                throw new IllegalArgumentException("No puedes aceptar: ya tienes 4 diagramas en edicion activa.");
            }
            colab.setEstado("aceptada");
        } else {
            colab.setEstado("rechazada");
        }
        colab.setFechaRespuesta(LocalDateTime.now());

        return colaboracionRepo.save(colab);
    }
}
