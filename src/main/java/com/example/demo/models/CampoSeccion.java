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
@Document(collection = "campos_seccion")
public class CampoSeccion {

    @Id
    private String id;

    private String seccionId;
    private String campoPlantillaId;

    private String nombre;
    private String valor;
    private String tipo;
    private boolean fueDictado;

    private LocalDateTime fechaGuardado;
}

