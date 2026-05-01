package com.example.demo.controllers;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterClienteRequest;
import com.example.demo.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Registro de clientes y login de todos los roles")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register-cliente")
    @Operation(summary = "Registro de cliente",
               description = "Solo crea usuarios de tipo 'cliente'. Para crear funcionarios o admins usar POST /api/usuarios/crear")
    public ResponseEntity<AuthResponse> registerCliente(@Valid @RequestBody RegisterClienteRequest req) {
        return ResponseEntity.ok(authService.registrarCliente(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Válido para los 3 roles. Devuelve JWT Bearer token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
