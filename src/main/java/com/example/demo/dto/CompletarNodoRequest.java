package com.example.demo.dto;

import lombok.Data;

@Data
public class CompletarNodoRequest {

    private String funcionarioId;

    private String decision;

    private String notas;

    private String nodoId;
}
