package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialTramiteResponse {

    private String id;
    private String codigo;

    private String clienteId;
    private String clienteNombre;

    private String politicaId;
    private String politicaNombre;

    private String estadoActual;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaCierreReal;
}
