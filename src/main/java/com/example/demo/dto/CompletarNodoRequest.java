package com.example.demo.dto;

import lombok.Data;

@Data
public class CompletarNodoRequest {

    // Lo deriva el controller desde el JWT (auth.getName()); opcional en el payload.
    // Los servicios internos (ExpedienteService, TramiteDecisionService) lo setean directamente.
    private String funcionarioId;

    // Para nodos de decisión: "si" o "no" (coincide con la etiqueta del FlujoTransicion)
    // Para nodos normales: null o vacío
    private String decision;

    private String notas;
}
