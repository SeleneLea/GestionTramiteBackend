package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "documentos")
public class Documento {

    @Id
    private String id;

    private String nombre;
    private String descripcion;
    private boolean activo;

    private LocalDateTime fechaCreacion;
}
