package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "diagramas_workflow")
public class DiagramaWorkflow {

    @Id
    private String id;

    private String nombre;
    private String politicaId;
    private String creadorId;

    private List<String> swimlanes;
    private Map<String, Object> canvasData;

    private int versionActual;
    private String estado;
    private boolean generadoPorIa;
    private String promptOriginal;

    private LocalDateTime fechaCreacion;
    private LocalDateTime ultimaModificacion;
}

