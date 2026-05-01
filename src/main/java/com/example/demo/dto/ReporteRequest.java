package com.example.demo.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ReporteRequest {
    private String tipo;
    private String formato;
    private Map<String, Object> filtros;
}
