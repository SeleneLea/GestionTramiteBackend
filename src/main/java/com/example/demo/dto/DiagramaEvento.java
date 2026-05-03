package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento broadcast a todos los suscriptores de /topic/diagramas/{diagramaId}.
 * Los clientes ignoran los eventos cuyo autorId coincide con el propio para
 * evitar aplicar dos veces sus propios cambios (que ya aplicaron localmente).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagramaEvento {

    /** nodo-creado | nodo-actualizado | nodo-eliminado | trans-creada | trans-actualizada | trans-eliminada */
    private String tipo;

    private String diagramaId;

    /** Entidad afectada para crear/actualizar; el id (String) para eliminar. */
    private Object payload;

    /** userId que originó el cambio. Permite suprimir el echo en el cliente. */
    private String autorId;

    private long timestamp;

    public static DiagramaEvento of(String tipo, String diagramaId, Object payload, String autorId) {
        return new DiagramaEvento(tipo, diagramaId, payload, autorId, System.currentTimeMillis());
    }
}
