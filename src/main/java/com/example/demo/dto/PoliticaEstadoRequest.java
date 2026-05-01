package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PoliticaEstadoRequest {

    @NotBlank
    @Pattern(regexp = "borrador|activa|archivada",
             message = "estado debe ser 'borrador', 'activa' o 'archivada'")
    private String estado;
}
