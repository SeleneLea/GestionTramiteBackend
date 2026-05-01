package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trazabilidad")
public class Trazabilidad {

    @Id
    private String id;

    @Indexed
    private String tramiteId;

    private String actorId;
    private String accion;
    private String nodoId;

    private Map<String, Object> datosAntes;
    private Map<String, Object> datosDespues;

    private String hashActual;
    private String hashAnterior;

    @Indexed
    private LocalDateTime timestamp;
}

