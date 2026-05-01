package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HitoDTO {
    private LocalDateTime fecha;
    private String estado;
    private String departamento;
    private String actor;
    private boolean esActual;
}
