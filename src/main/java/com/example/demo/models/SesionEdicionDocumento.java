package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CU-38 — Sesión activa de edición colaborativa sobre un documento.
 * Una por documento abierto. Se purga tras N minutos sin latido.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sesiones_edicion_documento")
public class SesionEdicionDocumento {

    @Id
    private String id;

    /** Versión optimista (RN-C01) — evita last-write-wins en el roster concurrente. */
    @org.springframework.data.annotation.Version
    private Long version;

    @Indexed(unique = true)
    private String documentoArchivoId;

    private List<Participante> participantes = new ArrayList<>();

    private LocalDateTime iniciada;
    private LocalDateTime ultimoLatido;

    private int versionBase;
    private int cambiosPendientes;

    /**
     * Contenido VIVO de la sesión (texto colaborativo). El servidor lo guarda en
     * cada op para que quien se une reciba un snapshot con el estado actual (y no
     * un editor vacío). Se persiste como nueva versión del documento vía CU-35.
     */
    private String contenido;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participante {
        private String usuarioId;
        private String nombre;
        private String color;
        private int cursorPos;
        private LocalDateTime ultimoLatido;
    }
}
