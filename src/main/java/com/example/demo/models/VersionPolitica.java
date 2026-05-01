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
@Document(collection = "versiones_politica")
public class VersionPolitica {

    @Id
    private String id;

    private String politicaId;
    private int numeroVersion;
    private String diagramaSnapshotId;
    private String creadorId;
    private String notasCambio;

    private LocalDateTime fechaVersion;
}

