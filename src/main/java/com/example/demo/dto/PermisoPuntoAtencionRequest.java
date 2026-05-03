package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PermisoPuntoAtencionRequest {

    @NotBlank(message = "politicaId es obligatorio")
    private String politicaId;

    @NotBlank(message = "actividadId es obligatorio")
    private String actividadId;

    @NotBlank(message = "nivelAcceso es obligatorio")
    private String nivelAcceso;   // SOLO_LECTURA | SOLO_EDICION | LECTURA_Y_EDICION

    private List<String> tiposDocumentoVisibles;
}
