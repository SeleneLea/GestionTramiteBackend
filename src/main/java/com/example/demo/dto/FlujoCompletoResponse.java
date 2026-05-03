package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
        private String tipo;            // inicio | actividad | decision | fork | join | fin
        private int orden;
        private String departamentoCodigo;
        private String departamentoNombre;
        private String swimlane;

        // Datos de la actividad asociada (solo si tipo=actividad)
        private String actividadId;
        private String actividadNombre;
        private String actividadDescripcion;
        private Integer slaHoras;
        private List<String> salidasPosibles;
        private List<DocumentoRequeridoDTO> documentosRequeridos;

        // Estado en este tramite especifico
        private String estadoSeccion;   // completada | en_curso | bloqueada | observado | etc.
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
    }
}
