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
@Document(collection = "departamentos")
public class Departamento {

    @Id
    private String id;

    @Indexed(unique = true)
    private String nombre;

    @Indexed(unique = true)
    private String codigo;

    private String descripcion;
    private String jefeId;
    private List<String> actividadesIds;
    private boolean activo;

    private LocalDateTime fechaCreacion;
}

