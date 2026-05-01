package com.example.demo.dto;

import lombok.Data;

@Data
public class AgenteRequest {
    private String consulta;
    private String moduloActivo;
    private String tramiteIdOpcional;
}
