package com.example.demo.models;

/**
 * Estado de la ACTIVIDAD / NODO — una instancia por cada paso del trámite por un
 * nodo del flujo. Nivel 2 del modelo de estados; se persiste en
 * {@link SeccionExpediente#getEstado()} como {@link #getValor()}.
 *
 * <p>Ciclo: {@link #BLOQUEADA} → {@link #PENDIENTE_RECEPCION} → {@link #EN_EJECUCION}
 * → {@link #DERIVADA}; con {@link #OBSERVADO} como pausa por subsanación.
 * Valores sin acento por seguridad de encoding; la UI muestra etiquetas con tilde.</p>
 */
public enum EstadoSeccion {

    /** Nodo futuro: el trámite aún no llegó. */
    BLOQUEADA("Bloqueada"),
    /** Llegó a la bandeja del responsable, pero aún no lo aceptó. */
    PENDIENTE_RECEPCION("Pendiente de recepcion"),
    /** El responsable lo aceptó y está trabajando en él. */
    EN_EJECUCION("En ejecucion"),
    /** Encontró un problema; la actividad se pausa hasta que se subsane. */
    OBSERVADO("Observado"),
    /** Terminó su tarea y empujó (derivó) el trámite al siguiente nodo. */
    DERIVADA("Derivada");

    private final String valor;

    EstadoSeccion(String valor) {
        this.valor = valor;
    }

    /** Literal canónico que se persiste en {@code SeccionExpediente.estado}. */
    public String getValor() {
        return valor;
    }

    /**
     * Una sección sobre la que se puede trabajar: ni futura ({@link #BLOQUEADA})
     * ni terminada ({@link #DERIVADA}). Incluye {@link #PENDIENTE_RECEPCION},
     * {@link #EN_EJECUCION} y {@link #OBSERVADO} (en subsanación).
     */
    public boolean esActivaParaTrabajo() {
        return this != BLOQUEADA && this != DERIVADA;
    }

    /** ¿El literal persistido corresponde a una sección trabajable? Tolerante a legacy. */
    public static boolean esActivaParaTrabajo(String estado) {
        EstadoSeccion e = from(estado);
        return e != null && e.esActivaParaTrabajo();
    }

    /**
     * Convierte un literal persistido (incluyendo legacy) al enum.
     * Tolerante a mayúsculas/acentos y a los antiguos valores
     * (bloqueada, en_curso, completada/completado).
     *
     * @return el enum, o {@code null} si no se reconoce.
     */
    public static EstadoSeccion from(String estado) {
        if (estado == null) return null;
        String v = estado.trim();
        for (EstadoSeccion e : values()) {
            if (e.valor.equalsIgnoreCase(v) || e.name().equalsIgnoreCase(v)) return e;
        }
        String low = v.toLowerCase();
        if (low.startsWith("bloque")) return BLOQUEADA;
        if (low.startsWith("pendiente")) return PENDIENTE_RECEPCION;
        if (low.startsWith("en_curso") || low.startsWith("en curso")
                || low.startsWith("en_ejec") || low.startsWith("en ejec")) return EN_EJECUCION;
        if (low.startsWith("observ")) return OBSERVADO;
        if (low.startsWith("complet") || low.startsWith("derivad")) return DERIVADA;
        return null;
    }

    /** ¿El literal persistido corresponde a una sección terminada (derivada)? Tolerante a legacy. */
    public static boolean esDerivada(String estado) {
        return from(estado) == DERIVADA;
    }
}
