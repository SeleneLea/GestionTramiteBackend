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

    private String repositorioId;

    @Indexed
    private String estadoActual;

    private String nodoActualId;

    private List<String> nodosParalellosActivos = new ArrayList<>();

    private String funcionarioActualId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private LocalDateTime fechaCierreReal;

    private int prioridad;

    private List<String> rutaSugerida = new ArrayList<>();

    private String riesgoDemora;

    private Float probSuperarSla;

    private LocalDateTime ultimaPrediccionRiesgo;

    private String slaVencidoNotificadoNodoId;

    private String documentoResolucionId;
    private LocalDateTime fechaResolucion;

    private String tipoResolucion;

    public boolean estaEnParalelo() {
        return nodosParalellosActivos != null && !nodosParalellosActivos.isEmpty();
    }
}
