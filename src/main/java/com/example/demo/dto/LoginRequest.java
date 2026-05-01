package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email es obligatorio")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "Contraseña es obligatoria")
    private String password;
}
