package com.example.demo.controllers;

import com.example.demo.models.Tramite;
import com.example.demo.services.TramiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tramites")
public class TramiteController {

    @Autowired
    private TramiteService tramiteService;

    // Solo admin puede listar todos los trámites sin filtro
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<Tramite>> listar() {
        return ResponseEntity.ok(tramiteService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Tramite> buscarPorId(@PathVariable String id) {
        return tramiteService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigo}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Tramite> buscarPorCodigo(@PathVariable String codigo) {
        return tramiteService.buscarPorCodigo(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/activos")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<List<Tramite>> activos() {
        return ResponseEntity.ok(tramiteService.obtenerTramitesActivos());
    }

    @GetMapping("/cliente/{clienteId}")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<List<Tramite>> porCliente(@PathVariable String clienteId) {
        return ResponseEntity.ok(tramiteService.listarPorCliente(clienteId));
    }
}
