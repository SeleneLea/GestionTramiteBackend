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

    /**
     * Documentos del catálogo requeridos en esta actividad (legacy: lista plana de IDs).
     * Se conserva por compatibilidad; el detalle por requisito vive en
     * {@link #documentosRequeridos}. Si {@code documentosRequeridos} está poblado, manda ese.
     */
    private List<String> documentoIds;

    /**
     * Requisitos documentales con su proveedor (CLIENTE/FUNCIONARIO) y obligatoriedad.
     * Reemplaza progresivamente a {@link #documentoIds}. Si está vacío/null, se deriva
     * de {@code documentoIds} tratando cada doc como CLIENTE + obligatorio (fallback legacy).
     */
    private List<RequisitoDocumento> documentosRequeridos;

    private boolean reutilizable;

    private LocalDateTime fechaCreacion;
}
