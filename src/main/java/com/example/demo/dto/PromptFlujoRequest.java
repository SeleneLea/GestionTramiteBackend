package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptFlujoRequest {

    @NotBlank(message = "El prompt es obligatorio")
    @Size(min = 20, max = 2000, message = "El prompt debe tener entre 20 y 2000 caracteres")
    private String prompt;

    @NotBlank(message = "El nombre del diagrama es obligatorio")
    private String nombreDiagrama;

    private String politicaId;
}
