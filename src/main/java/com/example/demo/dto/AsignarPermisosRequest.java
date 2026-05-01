package com.example.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AsignarPermisosRequest {

    @NotEmpty(message = "Debe especificar al menos un permiso")
    private List<String> permisos;
}
