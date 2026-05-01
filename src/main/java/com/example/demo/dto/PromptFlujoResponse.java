package com.example.demo.dto;

import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PromptFlujoResponse {
    private DiagramaWorkflow diagrama;
    private List<NodoDiagrama> nodos;
    private List<FlujoTransicion> transiciones;
    private String promptUsado;
}
