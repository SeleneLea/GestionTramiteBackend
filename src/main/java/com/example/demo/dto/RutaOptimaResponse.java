package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RutaOptimaResponse {

    private List<String> rutaSugerida;
    private List<PasoOmitido> pasosOmitidos;
    private Float confianza;
    private String explicacion;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasoOmitido {
        private String nodoId;
        private String motivo;
    }
}
