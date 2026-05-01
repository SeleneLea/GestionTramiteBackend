package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "adjuntos")
public class Adjunto {

    @Id
    private String id;

    private String seccionId;
    private String tramiteId;
    private String actividadId;
    private String documentoNombre;

    private String nombreArchivo;
    private String tipoMime;
    private String urlAlmacenamiento;
    private long tamanoBytes;
    private String subidoPorId;

    private LocalDateTime fechaSubida;
}

