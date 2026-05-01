package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FlujoTransicionRequest {

    @NotBlank(message = "nodoOrigenId es obligatorio")
    private String nodoOrigenId;

    @NotBlank(message = "nodoDestinoId es obligatorio")
    private String nodoDestinoId;

    @NotBlank
    @Pattern(regexp = "secuencial|condicional|paralelo|iterativo",
             message = "tipo debe ser: secuencial, condicional, paralelo o iterativo")
    private String tipo;

    private String etiqueta;
    private String condicion;
}
