package com.example.demo.controllers;

import com.example.demo.dto.ActualizarPerfilRequest;
import com.example.demo.dto.CrearUsuarioAdminRequest;
import com.example.demo.models.Usuario;
import com.example.demo.services.AuthService;
import com.example.demo.services.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Gestión de usuarios — GET /me para cualquiera, resto solo administrador")
public class UsuarioController {

    @Autowired private UsuarioService usuarioService;
    @Autowired private AuthService authService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ver mi propio perfil", description = "Devuelve los datos del usuario autenticado")
    public ResponseEntity<Usuario> miPerfil(Authentication auth) {
        return usuarioService.buscarPorId(auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Editar mi propio perfil", description = "Actualiza nombre, apellido, teléfono, DNI y dirección del usuario autenticado. No cambia email, tipo, rol ni contraseña.")
    public ResponseEntity<Usuario> actualizarMiPerfil(@RequestBody ActualizarPerfilRequest req, Authentication auth) {
        return ResponseEntity.ok(usuarioService.actualizarPerfilPropio(auth.getName(), req));
    }

    @PostMapping("/crear")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear usuario", description = "Crea un funcionario o administrador. Solo para administradores.")
    public ResponseEntity<Usuario> crear(@Valid @RequestBody CrearUsuarioAdminRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.crearUsuarioPorAdmin(req));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar usuarios", description = "Devuelve todos los usuarios o filtra por tipo (cliente, funcionario, administrador)")
    public ResponseEntity<List<Usuario>> listar(@RequestParam(required = false) String tipo) {
        if (tipo != null) {
            return ResponseEntity.ok(usuarioService.listarPorTipo(tipo));
        }
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @GetMapping("/funcionarios")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    @Operation(summary = "Listar funcionarios", description = "Devuelve todos los usuarios de tipo funcionario. Accesible por funcionarios y administradores.")
    public ResponseEntity<List<Usuario>> listarFuncionarios() {
        return ResponseEntity.ok(usuarioService.listarPorTipo("funcionario"));
    }

    @GetMapping("/activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<Usuario>> listarActivos() {
        return ResponseEntity.ok(usuarioService.listarActivos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Usuario> buscar(@PathVariable String id) {
        return usuarioService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Usuario> actualizar(@PathVariable String id, @RequestBody Usuario datos) {
        return ResponseEntity.ok(usuarioService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> desactivar(@PathVariable String id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
