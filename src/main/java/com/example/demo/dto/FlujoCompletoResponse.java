package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlujoCompletoResponse {

    private String tramiteId;
    private String codigo;
    private String politicaNombre;
    private String nodoActualId;
    private List<NodoFlujoDTO> nodos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodoFlujoDTO {
        private String nodoId;
        private String nombre;
        private String tipo;
        private int orden;
        private String departamentoCodigo;
        private String departamentoNombre;
        private String swimlane;

        private String pregunta;
        private List<Map<String, Object>> opciones;

        private String actividadId;
        private String actividadNombre;
        private String actividadDescripcion;
        private Integer slaHoras;
        private List<String> salidasPosibles;
        private List<DocumentoRequeridoDTO> documentosRequeridos;

        private String estadoSeccion;
        private String observacion;
        private List<String> documentosObservados;
        private boolean esActual;
        private String funcionarioId;
        private String funcionarioNombre;
        private LocalDateTime fechaAsignacion;
        private LocalDateTime fechaCompletado;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentoRequeridoDTO {
        private String id;
        private String nombre;
        private String descripcion;

        private String proveedor;
        private boolean obligatorio;
    }
}
