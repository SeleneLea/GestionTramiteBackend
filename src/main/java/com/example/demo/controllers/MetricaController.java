package com.example.demo.controllers;

import com.example.demo.models.CuelloBotella;
import com.example.demo.models.MetricaTiempo;
import com.example.demo.repositories.CuelloBotellaRepository;
import com.example.demo.repositories.MetricaTiempoRepository;
import com.example.demo.services.MetricaYCuelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @Autowired
    private MetricaYCuelloService metricaService;

    /**
     * P1 §7 — Dashboard de monitoreo en tiempo real: trámites por estado
     * (+activos/cerrados), tiempo promedio por departamento y por política, y
     * ranking de carga por departamento.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(metricaService.resumenDashboard());
    }
}
