package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "versiones_diagrama")
public class VersionDiagrama {

    @Id
    private String id;

    private String diagramaId;
    private int numeroVersion;
    private Map<String, Object> snapshot;
    private String modificadoPorId;
    private String descripcionCambio;

    private LocalDateTime fechaVersion;
}

