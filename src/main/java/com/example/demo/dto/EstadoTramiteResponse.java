package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoTramiteResponse {

    private String tramiteId;
    private String codigo;
    private String estadoActual;

    private String nodoActualId;
    private String nodoActualNombre;
    private String nodoActualTipo;
    private String departamentoActual;

    private boolean enParalelo;
    private List<String> nodosParalellosActivos;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private int prioridad;
}
