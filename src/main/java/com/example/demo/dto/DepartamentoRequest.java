package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartamentoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 5, message = "El código no puede superar 5 caracteres")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "El código solo acepta mayúsculas y números")
    private String codigo;

    private String descripcion;
    private String jefeId;
}
