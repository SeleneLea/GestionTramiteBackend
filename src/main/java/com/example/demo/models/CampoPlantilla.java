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

    /**
     * Solo para tipo "calculado": expresión aritmética sobre otros campos de la
     * misma sección, referenciados por su nombre técnico. Ej: "monto * 0.13".
     * El front la evalúa en vivo y muestra el campo como solo-lectura.
     */
    private String formula;

    private int orden;
}

