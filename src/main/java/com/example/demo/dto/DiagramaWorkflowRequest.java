package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DiagramaWorkflowRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String politicaId;
    private List<String> swimlanes;
    private Map<String, Object> canvasData;
}
