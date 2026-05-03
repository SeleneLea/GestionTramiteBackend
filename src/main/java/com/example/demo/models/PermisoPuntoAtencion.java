package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-36 — Nivel de acceso documental por actividad/nodo de una política.
 *
 * Valores válidos de {@code nivelAcceso}:
 *   SOLO_LECTURA | SOLO_EDICION | LECTURA_Y_EDICION.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "permisos_punto_atencion")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_politica_actividad_uniq",
                def = "{'politicaId': 1, 'actividadId': 1}",
                unique = true)
})
public class PermisoPuntoAtencion {

    @Id
    private String id;

    private String politicaId;
    private String actividadId;

    private String nivelAcceso;
    private List<String> tiposDocumentoVisibles;

    private String actualizadoPorId;
    private LocalDateTime fechaActualizacion;
}
