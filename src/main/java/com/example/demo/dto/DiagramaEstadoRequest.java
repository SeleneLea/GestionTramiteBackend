package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DiagramaEstadoRequest {

    @NotBlank
    @Pattern(regexp = "publicado|archivado",
             message = "estado debe ser 'publicado' o 'archivado'")
    private String estado;
}
