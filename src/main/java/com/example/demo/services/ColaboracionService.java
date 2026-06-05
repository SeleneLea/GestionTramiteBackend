package com.example.demo.services;

import com.example.demo.dto.CompartidoConmigoResponse;
import com.example.demo.dto.InvitarColaboradorRequest;
import com.example.demo.models.ColaboracionDiagrama;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.Notificacion;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.repositories.ColaboracionDiagramaRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.NotificacionRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ColaboracionService {

    @Autowired
    private ColaboracionDiagramaRepository colaboracionRepo;

    @Autowired
    private DiagramaWorkflowRepository diagramaRepo;

    @Autowired
    private NotificacionRepository notificacionRepo;

    @Autowired
    private PoliticaNegocioRepository politicaRepo;

    @Autowired
    private UsuarioRepository usuarioRepo;

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

    public void validarEditorDelDiagrama(String diagramaId, String usuarioId) {
        DiagramaWorkflow diagrama = diagramaRepo.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado: " + diagramaId));
        if (usuarioId != null && usuarioId.equals(diagrama.getCreadorId())) {
            return;
        }
        boolean editorAceptado = colaboracionRepo.findByDiagramaId(diagramaId).stream()
                .anyMatch(c -> usuarioId != null && usuarioId.equals(c.getInvitadoId())
                        && "editor".equalsIgnoreCase(c.getRolColaboracion())
                        && "aceptada".equalsIgnoreCase(c.getEstado()));
        if (!editorAceptado) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo el creador o un colaborador editor (invitacion aceptada) puede modificar este diagrama");
        }
    }

    public List<CompartidoConmigoResponse> compartidosConmigo(String usuarioId) {
        List<CompartidoConmigoResponse> out = new ArrayList<>();
        for (ColaboracionDiagrama c : colaboracionRepo.findByInvitadoId(usuarioId)) {
            if ("rechazada".equalsIgnoreCase(c.getEstado())) continue;

            DiagramaWorkflow dia = diagramaRepo.findById(c.getDiagramaId()).orElse(null);
            String diagramaNombre = dia != null && dia.getNombre() != null
                    ? dia.getNombre() : "(diagrama eliminado)";

            String politicaNombre = null;
            if (dia != null && dia.getPoliticaId() != null) {
                politicaNombre = politicaRepo.findById(dia.getPoliticaId())
                        .map(PoliticaNegocio::getNombre).orElse(null);
            }

            String invitadoPor = usuarioRepo.findById(c.getAdminInvitadorId())
                    .map(u -> ((u.getNombre() != null ? u.getNombre() : "") + " "
                            + (u.getApellido() != null ? u.getApellido() : "")).trim())
                    .filter(s -> !s.isEmpty())
                    .orElse("—");

            out.add(new CompartidoConmigoResponse(
                    c.getId(), c.getDiagramaId(), diagramaNombre, politicaNombre,
                    c.getRolColaboracion(), c.getEstado(), invitadoPor,
                    c.getFechaInvitacion(), c.getFechaRespuesta()));
        }
        return out;
    }
}
