package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class DevolverTramiteRequest {
    private String nodoDestinoId;
    private String observaciones;
    /** Ids de DocumentoArchivo que el funcionario marca como "mal" (caso OBSERVADO). */
    private List<String> documentosObservados;
}
