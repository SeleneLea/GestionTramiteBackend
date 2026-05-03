package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-40 — Histórico de sugerencias de política dadas por la IA al cliente.
 * Sirve además como dataset de feedback para reentrenar el clasificador.
 *
 * {@code feedback}: ACEPTADA | CAMBIADA | CANCELADA.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sugerencias_politica")
@CompoundIndexes({
        @CompoundIndex(name = "idx_cliente_fecha", def = "{'clienteId': 1, 'fechaCreacion': -1}")
})
public class SugerenciaPolitica {

    @Id
    private String id;

    private String clienteId;
    private String descripcionOriginal;
    private String audioS3Key;

    private String politicaSugeridaId;
    private float confianza;
    private List<Candidato> top3;

    @Indexed
    private String politicaConfirmadaId;

    @Indexed
    private String feedback;

    private String tramiteCreadoId;

    private LocalDateTime fechaCreacion;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidato {
        private String politicaId;
        private String nombre;
        private float confianza;
    }
}
