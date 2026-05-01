package com.example.demo.controllers;

import com.example.demo.dto.DepartamentoRequest;
import com.example.demo.models.Departamento;
import com.example.demo.services.DepartamentoService;
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
@RequestMapping("/api/departamentos")
@Tag(name = "Departamentos", description = "GET: cualquier autenticado · POST/PUT/DELETE: solo administrador")
public class DepartamentoController {

    @Autowired
    private DepartamentoService departamentoService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar departamentos", description = "Parámetro opcional: soloActivos=true para filtrar inactivos")
    public ResponseEntity<List<Departamento>> listar(
            @RequestParam(required = false, defaultValue = "false") boolean soloActivos) {
        if (soloActivos) {
            return ResponseEntity.ok(departamentoService.listarActivos());
        }
        return ResponseEntity.ok(departamentoService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Departamento> buscar(@PathVariable String id) {
        return departamentoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear departamento", description = "Código único, máximo 5 caracteres en mayúsculas")
    public ResponseEntity<Departamento> crear(@Valid @RequestBody DepartamentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departamentoService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Departamento> actualizar(@PathVariable String id,
                                                    @Valid @RequestBody DepartamentoRequest req) {
        return ResponseEntity.ok(departamentoService.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> desactivar(@PathVariable String id) {
        departamentoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
