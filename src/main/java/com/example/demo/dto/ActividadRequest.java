package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class ActividadRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;

    @NotBlank(message = "El departamentoId es obligatorio")
    private String departamentoId;

    private String funcionarioResponsableId;

    @NotNull
    @Min(value = 1, message = "El SLA debe ser al menos 1 hora")
    private Integer slaHoras;

    /**
     * Salidas posibles que el funcionario podrá emitir al completar la actividad.
     * Debe contener al menos una entrada y cada valor debe ser uno de:
     * aprobar | rechazar | derivar | observar | completar.
     */
    @NotEmpty(message = "Debe seleccionar al menos una salida posible")
    private List<@Pattern(
            regexp = "aprobar|rechazar|derivar|observar|completar",
            message = "Cada salida debe ser: aprobar, rechazar, derivar, observar o completar"
    ) String> salidasPosibles;

    private List<String> camposRequeridos;
    private List<String> documentoIds;
    private boolean reutilizable;
}
