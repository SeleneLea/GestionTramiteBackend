package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nodos_diagrama")
public class NodoDiagrama {

    @Id
    private String id;

    private String diagramaId;
    private String tipo;
    private String nombre;
    private String actividadId;
    private String departamentoId;
    private String swimlane;

    private Map<String, Object> posicion;
    private String formularioPlantillaId;

    private int orden;

    private boolean opcional;
}
