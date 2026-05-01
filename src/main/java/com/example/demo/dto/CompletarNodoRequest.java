package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompletarNodoRequest {

    @NotBlank(message = "funcionarioId es obligatorio")
    private String funcionarioId;

    // Para nodos de decisión: "si" o "no" (coincide con la etiqueta del FlujoTransicion)
    // Para nodos normales: null o vacío
    private String decision;

    private String notas;
}
