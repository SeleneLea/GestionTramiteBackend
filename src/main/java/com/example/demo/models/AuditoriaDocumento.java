package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CU-37 — Evento INMUTABLE de auditoría de un documento.
 *
 * Acciones válidas: LECTURA | DESCARGA | SUBIDA | NUEVA_VERSION
 * | EDICION_EN_VIVO | EDICION_GUARDADA | BLOQUEO | DESBLOQUEO | BORRADO.
 *
 * Solo se permite {@code insert}, nunca update/delete (enforced en el servicio).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "auditoria_documentos")
@CompoundIndexes({
        @CompoundIndex(name = "idx_doc_ts",     def = "{'documentoArchivoId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "idx_user_ts",    def = "{'usuarioId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "idx_accion_ts",  def = "{'accion': 1, 'timestamp': -1}")
})
public class AuditoriaDocumento {

    @Id
    private String id;

    private String documentoArchivoId;
    private String versionId;

    private String usuarioId;
    private String usuarioNombre;
    private String rol;

    private String accion;

    private String ip;
    private String userAgent;

    private LocalDateTime timestamp;

    private Map<String, Object> detalle;
}
