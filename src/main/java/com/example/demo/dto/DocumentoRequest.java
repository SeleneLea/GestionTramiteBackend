package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentoRequest {

    @NotBlank(message = "El nombre del documento es obligatorio")
    private String nombre;

    private String descripcion;
    private boolean activo = true;
}
