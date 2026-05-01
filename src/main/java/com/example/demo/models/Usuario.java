package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    private String nombre;
    private String apellido;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private String rolId;
    private List<String> departamentosIds;

    private String tipo;
    private String telefono;
    private boolean activo;

    private LocalDateTime fechaRegistro;
    private LocalDateTime ultimoAcceso;
}

