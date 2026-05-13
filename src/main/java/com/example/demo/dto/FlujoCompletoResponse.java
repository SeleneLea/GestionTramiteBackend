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
        private String tipo;            // inicio | actividad | decision | fork | join | fin
        private int orden;
        private String departamentoCodigo;
        private String departamentoNombre;
        private String swimlane;

        // Datos del nodo de decisión (solo si tipo=decision): la pregunta del if
        // y sus ramas (a dónde lleva cada respuesta), para mostrarlo claro al usuario.
        private String pregunta;
        private List<Map<String, Object>> opciones; // [{valor, etiqueta, destinoNombre}]

        // Datos de la actividad asociada (solo si tipo=actividad)
        private String actividadId;
        private String actividadNombre;
        private String actividadDescripcion;
        private Integer slaHoras;
        private List<String> salidasPosibles;
        private List<DocumentoRequeridoDTO> documentosRequeridos;

        // Estado en este tramite especifico
        private String estadoSeccion;   // completada | en_curso | bloqueada | observado | etc.
        private String observacion;     // ultimo motivo/observacion del historial en este nodo (si hubo)
        private List<String> documentosObservados; // ids de DocumentoArchivo marcados "mal" (caso OBSERVADO)
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
        /** CLIENTE | FUNCIONARIO — quién aporta este documento requerido. */
        private String proveedor;
        private boolean obligatorio;
    }
}
