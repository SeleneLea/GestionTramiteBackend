package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoEvento {

    private String tipo;
    private String documentoId;

    private Object payload;

    private String autorId;

    private long timestamp;

    public static DocumentoEvento of(String tipo, String documentoId, Object payload, String autorId) {
        return new DocumentoEvento(tipo, documentoId, payload, autorId, System.currentTimeMillis());
    }
}
