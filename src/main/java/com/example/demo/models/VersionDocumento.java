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
 * CU-35 — Versión inmutable de un DocumentoArchivo. El binario vive en S3.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "versiones_documento")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_doc_version_uniq",
                def = "{'documentoArchivoId': 1, 'numeroVersion': -1}",
                unique = true)
})
public class VersionDocumento {

    @Id
    private String id;

    private String documentoArchivoId;
    private int numeroVersion;

    private String s3Bucket;
    private String s3Key;
    private long tamanoBytes;
    private String mimeType;

    @Indexed
    private String hashSha256;

    private String autorId;
    private List<String> coautores;

    private String comentarioCambio;
    private String diffResumen;

    private LocalDateTime fechaCreacion;
}
