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
@Document(collection = "cuellos_botella")
public class CuelloBotella {

    @Id
    private String id;

    private String actividadId;
    private String departamentoId;

    private String periodo;
    private int tramitesAcumulados;
    private float tiempoPromedio;
    private float tiempoEsperado;
    private float desviacionPorcentaje;
    private String causaSugerida;

    private LocalDateTime fechaDeteccion;
}

