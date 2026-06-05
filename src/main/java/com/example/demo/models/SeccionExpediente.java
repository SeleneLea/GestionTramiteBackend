package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "secciones_expediente")
public class SeccionExpediente {

    @Id
    private String id;

    private String expedienteId;
    private String nodoId;
    private String departamentoId;

    private int ordenSeccion;
    private String estado;
    private String funcionarioId;

    private LocalDateTime fechaAsignacion;
    private LocalDateTime fechaCompletado;

    private List<String> documentosObservados;
}
