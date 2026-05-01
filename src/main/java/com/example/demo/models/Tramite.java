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

    @Indexed
    private String estadoActual;

    // Nodo actual para flujos lineales/condicionales/iterativos
    private String nodoActualId;

    // Para flujos paralelos: lista de nodos activos simultÃ¡neamente
    // VacÃ­a en flujos no paralelos. Cuando todos se completen el JOIN puede avanzar.
    private List<String> nodosParalellosActivos = new ArrayList<>();

    private String funcionarioActualId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private LocalDateTime fechaCierreReal;

    private int prioridad;

    // Helper
    public boolean estaEnParalelo() {
        return nodosParalellosActivos != null && !nodosParalellosActivos.isEmpty();
    }
}

