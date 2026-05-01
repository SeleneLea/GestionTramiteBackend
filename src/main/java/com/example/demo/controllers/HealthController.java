package com.example.demo.controllers;

import com.example.demo.repositories.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Estado del sistema y conteo de colecciones MongoDB")
public class HealthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private PoliticaNegocioRepository politicaRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    @GetMapping
    @Operation(summary = "Estado del sistema",
               description = "Muestra estado, timestamp y conteo de colecciones principales. No requiere autenticación.")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", LocalDateTime.now());
        result.put("service", "tramites-backend");
        result.put("ciclo", "Ciclo 1 — Completado");

        Map<String, Long> colecciones = new LinkedHashMap<>();
        colecciones.put("usuarios", usuarioRepository.count());
        colecciones.put("roles", rolRepository.count());
        colecciones.put("departamentos", departamentoRepository.count());
        colecciones.put("actividades", actividadRepository.count());
        colecciones.put("politicas_negocio", politicaRepository.count());
        colecciones.put("tramites", tramiteRepository.count());
        result.put("colecciones", colecciones);

        return result;
    }
}
