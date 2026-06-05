package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "campos_plantilla")
public class CampoPlantilla {

    @Id
    private String id;

    private String formularioPlantillaId;
    private String nombre;
    private String etiqueta;
    private String tipo;

    private boolean obligatorio;
    private List<String> opciones;
    private String validacionRegex;

    private String formula;

    private int orden;
}
