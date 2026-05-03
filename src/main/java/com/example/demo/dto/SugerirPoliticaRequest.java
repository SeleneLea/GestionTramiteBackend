package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SugerirPoliticaRequest {

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    /** Audio en base64 opcional. Cuando exista, el microservicio transcribe primero. */
    private String audioBase64;
}
