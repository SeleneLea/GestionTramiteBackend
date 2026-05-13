package com.example.demo.dto;

import lombok.Data;

@Data
public class ActualizarPerfilRequest {

    private String nombre;
    private String apellido;
    private String telefono;
    private String dni;
    private String direccion;
}
