package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * CU-45 — Anomalía detectada por el modelo IA en el flujo de un trámite.
 *
 * {@code categoria}: tiempo_atipico | secuencia_inusual | loop_derivaciones | salto_no_autorizado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alertas_anomalia")
@CompoundIndexes({
        @CompoundIndex(name = "idx_tramite_fecha",   def = "{'tramiteId': 1, 'fechaDeteccion': -1}"),
        @CompoundIndex(name = "idx_categoria_fecha", def = "{'categoria': 1, 'fechaDeteccion': -1}"),
        @CompoundIndex(name = "idx_falsoPos_fecha",  def = "{'falsoPositivo': 1, 'fechaDeteccion': -1}")
})
public class AlertaAnomalia {

    @Id
    private String id;

    private String tramiteId;
    private String categoria;
    private float score;
    private String descripcion;

    private String revisadaPor;
    private boolean falsoPositivo;

    private LocalDateTime fechaDeteccion;
    private LocalDateTime fechaRevision;
}
