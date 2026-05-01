package com.example.demo.controllers;

import com.example.demo.dto.ActividadRequest;
import com.example.demo.models.Actividad;
import com.example.demo.services.ActividadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/actividades")
@Tag(name = "Actividades", description = "GET: cualquier autenticado · POST/PUT/DELETE: solo administrador")
public class ActividadController {

    @Autowired
    private ActividadService actividadService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar actividades", description = "Con filtros opcionales: departamentoId y reutilizables")
    public ResponseEntity<List<Actividad>> listar(
            @RequestParam(required = false) String departamentoId,
            @RequestParam(required = false, defaultValue = "false") boolean reutilizables) {

        if (departamentoId != null) {
            return ResponseEntity.ok(actividadService.listarPorDepartamento(departamentoId));
        }
        if (reutilizables) {
            return ResponseEntity.ok(actividadService.listarReutilizables());
        }
        return ResponseEntity.ok(actividadService.listarTodas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Actividad> buscar(@PathVariable String id) {
        return actividadService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Actividad> crear(@Valid @RequestBody ActividadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actividadService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Actividad> actualizar(@PathVariable String id,
                                                 @Valid @RequestBody ActividadRequest req) {
        return ResponseEntity.ok(actividadService.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        actividadService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
