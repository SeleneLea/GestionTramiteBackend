package com.example.demo.dto;

import java.time.LocalDateTime;

/**
 * Un diagrama compartido CON el usuario autenticado (lo que ve en
 * "Compartidos conmigo"): qué diagrama/política es, con qué permiso
 * (editor/visualizador), su estado (pendiente/aceptada) y quién lo invitó.
 */
public record CompartidoConmigoResponse(
        String colaboracionId,
        String diagramaId,
        String diagramaNombre,
        String politicaNombre,
        String permisos,
        String estado,
        String invitadoPor,
        LocalDateTime fechaInvitacion,
        LocalDateTime fechaRespuesta
) {}
