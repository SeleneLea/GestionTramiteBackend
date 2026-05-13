package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un requisito documental de una {@link Actividad}: qué documento del catálogo
 * ({@link Documento}) se pide, QUIÉN lo aporta y si es obligatorio.
 *
 * <p>{@code proveedor}:
 * <ul>
 *   <li>{@link #CLIENTE} — lo sube el solicitante. La compuerta del motor lo exige
 *       antes de que el funcionario reciba el paso (estado PENDIENTE_DOCUMENTOS).</li>
 *   <li>{@link #FUNCIONARIO} — lo produce/aporta un paso interno; no se le exige al cliente.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisitoDocumento {

    public static final String CLIENTE = "CLIENTE";
    public static final String FUNCIONARIO = "FUNCIONARIO";

    /** FK al catálogo {@link Documento} (el tipo de documento requerido). */
    private String documentoId;

    /** {@link #CLIENTE} | {@link #FUNCIONARIO}. */
    private String proveedor;

    /** Si es obligatorio para poder avanzar el trámite. */
    private boolean obligatorio;
}
