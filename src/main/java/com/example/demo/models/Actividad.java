package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "actividades")
public class Actividad {

    @Id
    private String id;

    private String nombre;
    private String descripcion;
    private String departamentoId;
    private String funcionarioResponsableId;

    private int slaHoras;

    /**
     * Salidas posibles que el funcionario puede emitir al completar esta actividad.
     * Cada elemento debe ser: aprobar | rechazar | derivar | observar | completar.
     * Antes era un único {@code tipoSalida}; ahora es una lista para permitir que una
     * misma actividad ofrezca varias rutas (p.ej. aprobar / rechazar / observar).
     */
    private List<String> salidasPosibles = new ArrayList<>();

    private List<String> camposRequeridos;
    private List<String> documentoIds;
    private boolean reutilizable;

    private LocalDateTime fechaCreacion;
}
