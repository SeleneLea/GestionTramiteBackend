package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento broadcast a los suscriptores de un documento (CU-38).
 *
 * Tipos de evento:
 *   op            — operación CRDT/textual recibida del cliente, retransmitida al resto.
 *   join          — anuncio de que un usuario entró a la sesión.
 *   leave         — anuncio de salida.
 *   kick          — el backend expulsa a un usuario (perdió permiso o sesión expiró).
 *   cursor        — posición del cursor remoto (alta frecuencia).
 *   roster        — lista actualizada de participantes activos.
 *   snapshot      — el backend persistió una nueva versión por inactividad.
 *
 * Los clientes ignoran eventos cuyo {@code autorId} coincide con el propio
 * (para evitar aplicar dos veces sus propias operaciones).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoEvento {

    private String tipo;
    private String documentoId;

    /** Payload variable según {@code tipo}. */
    private Object payload;

    /** userId que originó el evento — para suprimir echo en cliente. */
    private String autorId;

    private long timestamp;

    public static DocumentoEvento of(String tipo, String documentoId, Object payload, String autorId) {
        return new DocumentoEvento(tipo, documentoId, payload, autorId, System.currentTimeMillis());
    }
}
