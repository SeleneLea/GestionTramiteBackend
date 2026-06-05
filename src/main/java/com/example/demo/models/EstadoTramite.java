package com.example.demo.models;

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

    public String getValor() {
        return valor;
    }

    public boolean esFinalizado() {
        return this == APROBADO || this == RECHAZADO || this == CANCELADO;
    }

    public boolean esActivo() {
        return !esFinalizado();
    }

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

    public static boolean esFinalizado(String estado) {
        EstadoTramite e = from(estado);
        return e != null && e.esFinalizado();
    }
}
