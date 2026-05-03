package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SugerirPoliticaResponse {

    private String sugerenciaId;
    private String politicaSugeridaId;
    private Float confianza;
    private List<Candidato> top3;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidato {
        private String politicaId;
        private String nombre;
        private Float confianza;
    }
}
