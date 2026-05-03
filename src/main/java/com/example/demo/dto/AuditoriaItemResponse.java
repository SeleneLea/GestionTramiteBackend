package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaItemResponse {
    private String id;
    private String documentoArchivoId;
    private String versionId;
    private String usuarioId;
    private String usuarioNombre;
    private String rol;
    private String accion;
    private String ip;
    private String userAgent;
    private LocalDateTime timestamp;
    private Map<String, Object> detalle;
}
