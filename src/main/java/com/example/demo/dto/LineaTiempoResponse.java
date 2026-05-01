package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class LineaTiempoResponse {
    private String tramiteId;
    private String estadoActual;
    private List<HitoDTO> hitos;
}
