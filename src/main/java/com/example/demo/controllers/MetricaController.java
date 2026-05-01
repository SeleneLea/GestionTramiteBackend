package com.example.demo.controllers;

import com.example.demo.models.CuelloBotella;
import com.example.demo.models.MetricaTiempo;
import com.example.demo.repositories.CuelloBotellaRepository;
import com.example.demo.repositories.MetricaTiempoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metricas")
public class MetricaController {

    @Autowired
    private MetricaTiempoRepository metricaRepository;

    @Autowired
    private CuelloBotellaRepository cuelloBotellaRepository;

    @GetMapping("/tramite/{tramiteId}")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<List<MetricaTiempo>> getMetricasPorTramite(@PathVariable String tramiteId) {
        return ResponseEntity.ok(metricaRepository.findByTramiteId(tramiteId));
    }

    @GetMapping("/cuellos-botella")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<CuelloBotella>> getCuellosBotella() {
        return ResponseEntity.ok(cuelloBotellaRepository.findAllByOrderByFechaDeteccionDesc());
    }
}
