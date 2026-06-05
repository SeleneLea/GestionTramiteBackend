package com.example.demo.dto;

import lombok.Data;

@Data
public class AgenteResponse {
    private String idLogBaseDatos;
    private String respuesta;
    private AccionDirecta accion;
    private String fuente;

    @Data
    public static class AccionDirecta {
        private String label;
        private String ruta;
        private String tipo;

        private String dato;
    }
}
