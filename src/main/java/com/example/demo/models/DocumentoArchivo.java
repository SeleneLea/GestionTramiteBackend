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

/**
 * CU-33 — Archivo del repositorio documental. Apunta a la versión actual;
 * el historial completo vive en {@link VersionDocumento}.
 *
 * Valores válidos de {@code tipoDocumento}: PDF | IMAGEN | WORD | EXCEL | AUDIO | VIDEO | OTRO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "documentos_archivo")
@CompoundIndexes({
        @CompoundIndex(name = "idx_tramite_actividad", def = "{'tramiteId': 1, 'actividadId': 1}")
})
public class DocumentoArchivo {

    @Id
    private String id;

    @Indexed
    private String repositorioId;

    @Indexed
    private String politicaId;

    private String tramiteId;
    private String actividadId;
    private String nodoId;

    private String nombreLogico;
    private String tipoDocumento;
    private boolean obligatorio;

    private String versionActualId;
    private int numeroVersionActual;

    /** Si no es null, el documento está bloqueado para edición exclusiva. */
    private String bloqueadoPor;
    private LocalDateTime fechaBloqueo;

    private String creadoPorId;
    private LocalDateTime fechaCreacion;

    private boolean activo;

    /** {@code true} si es el documento de resolución entregable del trámite. */
    private boolean esResolucion;
}
