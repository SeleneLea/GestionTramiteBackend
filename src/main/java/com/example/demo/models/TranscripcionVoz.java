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
@Document(collection = "transcripciones_voz")
public class TranscripcionVoz {

    @Id
    private String id;

    private String seccionId;
    private String funcionarioId;
    private String textoTranscrito;
    private float duracionSegundos;
    private float confianzaTranscripcion;

    private LocalDateTime fechaTranscripcion;
}

