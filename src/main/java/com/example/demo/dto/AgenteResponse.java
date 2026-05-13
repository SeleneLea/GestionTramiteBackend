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
        /** Dato extra para la acción (p.ej. el id de la política a iniciar). */
        private String dato;
    }
}
