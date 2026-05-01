package com.example.demo.dto;

import lombok.Data;

@Data
public class InvitarColaboradorRequest {
    private String usuarioInvitadoId;
    private String permisos;
}
