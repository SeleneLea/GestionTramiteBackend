package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FormularioPlantillaRequest {

    @NotBlank(message = "El nombre del formulario es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String nombre;

    private boolean permiteAdjuntos;
    private boolean permiteDictadoVoz;
}
