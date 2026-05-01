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
@Document(collection = "metricas_tiempo")
public class MetricaTiempo {

    @Id
    private String id;

    private String tramiteId;
    private String actividadId;
    private String departamentoId;

    private long tiempoSegundos;
    private boolean superoSla;

    private LocalDateTime fechaInicioActividad;
    private LocalDateTime fechaFinActividad;
}

