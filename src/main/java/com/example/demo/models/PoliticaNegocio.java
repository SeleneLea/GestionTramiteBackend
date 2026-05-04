package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "politicas_negocio")
public class PoliticaNegocio {

    @Id
    private String id;

    @Indexed(unique = true)
    private String nombre;

    private String descripcion;
    private String categoria;
    private String diagramaId;
    private String creadorId;

    /** CU-32 — FK al RepositorioDocumental (1:1). Se rellena al crear la política. */
    private String repositorioId;

    private int versionActual;
    private String estado;
    private Map<String, Object> parametros;

    /**
     * Si es {@code true}, al APROBAR un trámite de esta política el responsable
     * debe adjuntar el documento de resolución que se entrega al cliente
     * (ej. "estado de deudas"). Por defecto {@code false}.
     */
    private boolean requiereDocumentoResolucion;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActivacion;
}

