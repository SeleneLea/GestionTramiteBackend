package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class DevolverTramiteRequest {
    private String nodoDestinoId;
    private String observaciones;

    private List<String> documentosObservados;
}
