package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

/**
 * Cuerpo para la actualización PARCIAL de un nodo ({@code PATCH /nodos/{id}}).
 * A diferencia de {@link NodoDiagramaRequest} (usado en POST/PUT, que exige el
 * nodo completo), aquí todos los campos son opcionales: solo se actualizan los
 * que vengan presentes (no nulos). Pensado para los cambios incrementales del
 * editor de diagramas, p.ej. mover un nodo (solo {@code posicion}).
 */
@Data
public class NodoDiagramaPatchRequest {

    private String nombre;

    // @Pattern no valida null → solo se comprueba el formato si el tipo viene.
    @Pattern(regexp = "inicio|actividad|decision|fork|join|fin",
             message = "tipo debe ser: inicio, actividad, decision, fork, join o fin")
    private String tipo;

    private String actividadId;
    private String departamentoId;
    private String swimlane;
    private String formularioPlantillaId;
    private Map<String, Object> posicion;

    // Integer (no int) para distinguir "no enviado" (null) de 0.
    @Min(value = 0, message = "El orden no puede ser negativo")
    private Integer orden;
}
