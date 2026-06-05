package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "repositorios_documentales")
public class RepositorioDocumental {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String tramiteId;

    private String politicaId;

    private String nombre;
    private String bucketKey;

    private long totalArchivos;
    private long totalBytes;

    private boolean activo;
    private LocalDateTime fechaCreacion;
}
