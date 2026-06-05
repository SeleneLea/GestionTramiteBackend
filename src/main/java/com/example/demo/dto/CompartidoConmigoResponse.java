package com.example.demo.dto;

import java.time.LocalDateTime;

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
