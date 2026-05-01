package com.example.demo.controllers;

import com.example.demo.dto.AsignarPermisosRequest;
import com.example.demo.dto.RolRequest;
import com.example.demo.models.Rol;
import com.example.demo.services.RolService;
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
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "CU-03: gestión de roles y permisos")
public class RolController {

    @Autowired
    private RolService rolService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar roles", description = "Devuelve los 4 roles del sistema: SuperUser, Administrador, Funcionario, Cliente")
    public ResponseEntity<List<Rol>> listar() {
        return ResponseEntity.ok(rolService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Buscar rol por ID")
    public ResponseEntity<Rol> buscar(@PathVariable String id) {
        return rolService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear un rol nuevo (personalizado)")
    public ResponseEntity<Rol> crear(@Valid @RequestBody RolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rolService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualizar rol",
               description = "No se pueden actualizar los roles del sistema (SuperUser, Administrador, Funcionario, Cliente)")
    public ResponseEntity<Rol> actualizar(@PathVariable String id,
                                          @Valid @RequestBody RolRequest req) {
        return ResponseEntity.ok(rolService.actualizar(id, req));
    }

    @PatchMapping("/{id}/permisos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Asignar permisos a un rol")
    public ResponseEntity<Rol> asignarPermisos(@PathVariable String id,
                                                @Valid @RequestBody AsignarPermisosRequest req) {
        return ResponseEntity.ok(rolService.asignarPermisos(id, req.getPermisos()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Eliminar rol",
               description = "No se pueden eliminar los roles del sistema")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        rolService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
