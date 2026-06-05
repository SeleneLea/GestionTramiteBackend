package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TramiteRiesgoResponse {

    private String tramiteId;
    private Float probSuperarSla;
    private String nivel;
    private List<String> razones;
}
