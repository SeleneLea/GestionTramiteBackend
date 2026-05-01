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
@Document(collection = "logs_agente")
public class LogAgente {

    @Id
    private String id;

    @Indexed
    private String usuarioId;

    private String contextoModulo;
    private String contextoRol;
    private String contextoTramiteId;

    private String consultaUsuario;
    private String respuestaAgente;
    private float tiempoRespuestaMs;
    private boolean fueUtil;

    private LocalDateTime timestamp;
}

