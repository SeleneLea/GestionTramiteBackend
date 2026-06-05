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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sesiones_edicion_documento")
public class SesionEdicionDocumento {

    @Id
    private String id;

    @org.springframework.data.annotation.Version
    private Long version;

    @Indexed(unique = true)
    private String documentoArchivoId;

    private List<Participante> participantes = new ArrayList<>();

    private LocalDateTime iniciada;
    private LocalDateTime ultimoLatido;

    private int versionBase;
    private int cambiosPendientes;

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
