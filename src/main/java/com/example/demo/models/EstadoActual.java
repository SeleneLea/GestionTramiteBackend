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
@Document(collection = "estados_actuales")
public class EstadoActual {

    @Id
    private String id;

    private String tramiteId;
    private String estado;
    private String nodoId;

    private LocalDateTime desde;
}

