package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ActividadDocumentosDTO {
    private String actividadId;
    private String actividadNombre;
    private List<DocumentoInfoDTO> documentosRequeridos;
}
