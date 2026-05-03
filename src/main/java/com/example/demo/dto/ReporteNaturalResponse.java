package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteNaturalResponse {

    private String reporteId;
    private String collection;
    private List<Map<String, Object>> filasMuestra;
    private long totalFilas;
    private String urlDescarga;
    private String formato;
    /** Pipeline Mongo serializado, conservado para auditoría/explicación. */
    private String queryGenerada;
}
