package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "flujos_transicion")
public class FlujoTransicion {

    @Id
    private String id;

    private String diagramaId;
    private String nodoOrigenId;
    private String nodoDestinoId;

    private String tipo;
    private String condicion;
    private String etiqueta;
}

