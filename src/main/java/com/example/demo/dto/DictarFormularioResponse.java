package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictarFormularioResponse {

    private String transcripcionId;
    private String textoTranscrito;
    private List<CampoSugerido> campos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampoSugerido {
        private String campo;
        private String valor;
        private Float confianza;
    }
}
