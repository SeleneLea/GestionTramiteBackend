package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagramaEvento {

    private String tipo;

    private String diagramaId;

    private Object payload;

    private String autorId;

    private long timestamp;

    public static DiagramaEvento of(String tipo, String diagramaId, Object payload, String autorId) {
        return new DiagramaEvento(tipo, diagramaId, payload, autorId, System.currentTimeMillis());
    }
}
