package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SugerirPoliticaRequest {

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    private String audioBase64;
}
