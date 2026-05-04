package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class PoliticaNegocioRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;
    private String categoria;
    private String diagramaId;
    private Map<String, Object> parametros;

    /** Si la política exige adjuntar documento de resolución al aprobar. */
    private boolean requiereDocumentoResolucion;
}
