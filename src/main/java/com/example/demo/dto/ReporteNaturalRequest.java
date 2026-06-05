package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReporteNaturalRequest {

    @NotBlank
    private String consulta;

    private String formatoExport;
}
