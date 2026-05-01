package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class GuardarSeccionRequest {
    private List<CampoValorDto> campos;
    private String notasOperativas;
}
