package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tramites")
public class Tramite {

    @Id
    private String id;

    @Indexed(unique = true)
    private String codigo;

    private String clienteId;
    private String politicaId;
    private String expedienteId;

    /** FK 1:1 al RepositorioDocumental del trámite. */
    private String repositorioId;

    @Indexed
    private String estadoActual;

    // Nodo actual para flujos lineales/condicionales/iterativos
    private String nodoActualId;

    // Para flujos paralelos: lista de nodos activos simultáneamente
    // Vacía en flujos no paralelos. Cuando todos se completen el JOIN puede avanzar.
    private List<String> nodosParalellosActivos = new ArrayList<>();

    private String funcionarioActualId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private LocalDateTime fechaCierreReal;

    private int prioridad;

    // === Parte 2 · CU-42 / CU-43 ===
    /** CU-42 — Ruta de nodos sugerida por la IA. */
    private List<String> rutaSugerida = new ArrayList<>();

    /** CU-43 — Nivel de riesgo de superar el SLA: desconocido | bajo | medio | alto. */
    private String riesgoDemora;

    /** CU-43 — Probabilidad estimada de superar el SLA, 0..1. */
    private Float probSuperarSla;

    private LocalDateTime ultimaPrediccionRiesgo;

    // === Documento de resolución (lo que el trámite "devuelve" al cliente) ===
    /** FK al DocumentoArchivo con la resolución entregable. Null si aún no hay. */
    private String documentoResolucionId;
    private LocalDateTime fechaResolucion;
    /** Tipo/etiqueta de la resolución (ej. "Resolución", "Estado de deudas"). */
    private String tipoResolucion;

    // Helper
    public boolean estaEnParalelo() {
        return nodosParalellosActivos != null && !nodosParalellosActivos.isEmpty();
    }
}

