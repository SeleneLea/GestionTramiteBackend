package com.example.demo.models;

/**
 * Estado GLOBAL del trámite (la "foto" que ve el cliente / auditoría).
 *
 * <p>Nivel 1 del modelo de estados. El campo {@link Tramite#getEstadoActual()}
 * almacena el {@link #getValor()} de uno de estos valores. Es deliberadamente
 * sin acentos para evitar problemas de encoding en BD/JSON; la UI puede mostrar
 * etiquetas con tilde.</p>
 *
 * <ul>
 *   <li>{@link #EN_CURSO} — avanzando entre departamentos.</li>
 *   <li>{@link #OBSERVADO} — esperando corrección del cliente o condición externa
 *       (pago, amparo). Visible para el cliente, que subsana sin reempezar.</li>
 *   <li>{@link #APROBADO} / {@link #RECHAZADO} / {@link #CANCELADO} — terminales.</li>
 * </ul>
 */
public enum EstadoTramite {

    EN_CURSO("En curso"),
    OBSERVADO("Observado"),
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado"),
    CANCELADO("Cancelado");

    private final String valor;

    EstadoTramite(String valor) {
        this.valor = valor;
    }

    /** Literal canónico que se persiste en {@code Tramite.estadoActual}. */
    public String getValor() {
        return valor;
    }

    /** Estados terminales: el trámite no puede seguir avanzando ni cancelarse. */
    public boolean esFinalizado() {
        return this == APROBADO || this == RECHAZADO || this == CANCELADO;
    }

    public boolean esActivo() {
        return !esFinalizado();
    }

    /**
     * Convierte un literal persistido (incluyendo los legacy) al enum.
     * Tolerante a mayúsculas/acentos y a los antiguos valores
     * (Nuevo, Iniciado, En proceso, Derivado, Completado, "Cancelado por el usuario").
     *
     * @return el enum, o {@code null} si no se reconoce.
     */
    public static EstadoTramite from(String estado) {
        if (estado == null) return null;
        String v = estado.trim();
        for (EstadoTramite e : values()) {
            if (e.valor.equalsIgnoreCase(v) || e.name().equalsIgnoreCase(v)) return e;
        }
        String low = v.toLowerCase();
        if (low.startsWith("nuevo") || low.startsWith("iniciad")
                || low.startsWith("en proceso") || low.startsWith("en_proceso")
                || low.startsWith("derivad")) return EN_CURSO;
        if (low.startsWith("observ")) return OBSERVADO;
        if (low.startsWith("aprob") || low.startsWith("complet")) return APROBADO;
        if (low.startsWith("rechaz")) return RECHAZADO;
        if (low.startsWith("cancel")) return CANCELADO;
        return null;
    }

    /** ¿El literal persistido corresponde a un estado terminal? Tolerante a legacy. */
    public static boolean esFinalizado(String estado) {
        EstadoTramite e = from(estado);
        return e != null && e.esFinalizado();
    }
}
