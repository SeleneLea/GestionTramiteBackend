package com.example.demo.models;

public enum EstadoSeccion {

    BLOQUEADA("Bloqueada"),

    PENDIENTE_DOCUMENTOS("Pendiente de documentos"),

    PENDIENTE_RECEPCION("Pendiente de recepcion"),

    EN_EJECUCION("En ejecucion"),

    OBSERVADO("Observado"),

    DERIVADA("Derivada");

    private final String valor;

    EstadoSeccion(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public boolean esActivaParaTrabajo() {
        return this != BLOQUEADA && this != DERIVADA;
    }

    public static boolean esActivaParaTrabajo(String estado) {
        EstadoSeccion e = from(estado);
        return e != null && e.esActivaParaTrabajo();
    }

    public static EstadoSeccion from(String estado) {
        if (estado == null) return null;
        String v = estado.trim();
        for (EstadoSeccion e : values()) {
            if (e.valor.equalsIgnoreCase(v) || e.name().equalsIgnoreCase(v)) return e;
        }
        String low = v.toLowerCase();
        if (low.startsWith("bloque")) return BLOQUEADA;
        if (low.startsWith("pendiente de doc") || low.startsWith("pendiente_doc")) return PENDIENTE_DOCUMENTOS;
        if (low.startsWith("pendiente")) return PENDIENTE_RECEPCION;
        if (low.startsWith("en_curso") || low.startsWith("en curso")
                || low.startsWith("en_ejec") || low.startsWith("en ejec")) return EN_EJECUCION;
        if (low.startsWith("observ")) return OBSERVADO;
        if (low.startsWith("complet") || low.startsWith("derivad")) return DERIVADA;
        return null;
    }

    public static boolean esDerivada(String estado) {
        return from(estado) == DERIVADA;
    }
}
