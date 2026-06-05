package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CampoPlantillaRequest {

    @NotBlank(message = "El nombre tecnico del campo es obligatorio")
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]{0,49}$",
             message = "nombre debe iniciar con letra/_ y solo contener letras, numeros o _ (max 50)")
    private String nombre;

    @NotBlank(message = "La etiqueta visible es obligatoria")
    @Size(max = 150, message = "La etiqueta no puede exceder 150 caracteres")
    private String etiqueta;

    @NotBlank
    @Pattern(regexp = "texto|textarea|numero|fecha|select|checkbox|radio|archivo|calculado",
             message = "tipo debe ser: texto, textarea, numero, fecha, select, checkbox, radio, archivo o calculado")
    private String tipo;

    private boolean obligatorio;

    private List<String> opciones;

    private String validacionRegex;

    @Size(max = 300, message = "La formula no puede exceder 300 caracteres")
    private String formula;

    @Min(value = 0, message = "El orden no puede ser negativo")
    private int orden;
}
