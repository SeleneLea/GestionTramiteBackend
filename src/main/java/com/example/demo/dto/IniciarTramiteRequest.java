package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IniciarTramiteRequest {

    @NotBlank(message = "clienteId es obligatorio")
    private String clienteId;

    @NotBlank(message = "politicaId es obligatorio")
    private String politicaId;

    private int prioridad;
}
