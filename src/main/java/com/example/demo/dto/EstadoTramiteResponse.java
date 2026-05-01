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

    // Info del nodo actual
    private String nodoActualId;
    private String nodoActualNombre;
    private String nodoActualTipo;
    private String departamentoActual;

    // Para flujos paralelos
    private boolean enParalelo;
    private List<String> nodosParalellosActivos;

    // Línea de tiempo
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private int prioridad;
}
