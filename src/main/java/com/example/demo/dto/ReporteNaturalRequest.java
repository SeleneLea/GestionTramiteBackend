package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReporteNaturalRequest {

    @NotBlank
    private String consulta;

    /** csv | xlsx | json — si se omite se devuelve solo la muestra en JSON. */
    private String formatoExport;
}
