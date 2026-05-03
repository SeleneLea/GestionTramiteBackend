package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmarSugerenciaRequest {

    /** id de la política finalmente elegida (puede diferir de la sugerida). */
    @NotBlank(message = "politicaConfirmadaId es obligatorio")
    private String politicaConfirmadaId;
}
