package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

@Data
public class NodoDiagramaRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank
    @Pattern(regexp = "inicio|actividad|decision|fork|join|fin",
             message = "tipo debe ser: inicio, actividad, decision, fork, join o fin")
    private String tipo;

    private String actividadId;
    private String departamentoId;
    private String swimlane;
    private String formularioPlantillaId;
    private Map<String, Object> posicion;

    @Min(value = 0, message = "El orden no puede ser negativo")
    private int orden;
}
