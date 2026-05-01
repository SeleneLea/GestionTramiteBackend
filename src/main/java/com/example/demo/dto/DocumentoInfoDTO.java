package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentoInfoDTO {
    private String id;
    private String nombre;
    private String descripcion;
}
