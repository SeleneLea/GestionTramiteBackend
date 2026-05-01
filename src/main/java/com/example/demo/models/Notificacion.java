package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notificaciones")
public class Notificacion {

    @Id
    private String id;

    @Indexed
    private String destinatarioId;

    private String tramiteId;
    private String canal;
    private String tipo;
    private String titulo;
    private String mensaje;

    @Indexed
    private boolean leida;

    private String estadoEnvio;
    private int intentosEnvio;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLeida;
}

