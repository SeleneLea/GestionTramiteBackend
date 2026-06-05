package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmarSugerenciaRequest {

    @NotBlank(message = "politicaConfirmadaId es obligatorio")
    private String politicaConfirmadaId;
}
