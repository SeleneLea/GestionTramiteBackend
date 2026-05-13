package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "secciones_expediente")
public class SeccionExpediente {

    @Id
    private String id;

    private String expedienteId;
    private String nodoId;
    private String departamentoId;

    private int ordenSeccion;
    private String estado;
    private String funcionarioId;

    private LocalDateTime fechaAsignacion;
    private LocalDateTime fechaCompletado;

    /**
     * Caso OBSERVADO: ids de {@link DocumentoArchivo} que el funcionario marcó como
     * "mal" al devolver el trámite. El cliente ve solo estos para corregir; al re-subir
     * uno (con {@code corrigeDocumentoId}) se quita de esta lista.
     */
    private List<String> documentosObservados;
}

