package com.example.demo.controllers;

import com.example.demo.dto.DocumentoRequest;
import com.example.demo.models.Documento;
import com.example.demo.services.DocumentoService;
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
@RequestMapping("/api/documentos")
@Tag(name = "Documentos", description = "GET: cualquier autenticado · POST/PUT/DELETE: solo administrador")
public class DocumentoController {

    @Autowired
    private DocumentoService documentoService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar documentos", description = "Parámetro opcional: soloActivos=true")
    public ResponseEntity<List<Documento>> listar(
            @RequestParam(required = false, defaultValue = "false") boolean soloActivos) {
        if (soloActivos) {
            return ResponseEntity.ok(documentoService.listarActivos());
        }
        return ResponseEntity.ok(documentoService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Documento> buscar(@PathVariable String id) {
        return documentoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Documento> crear(@Valid @RequestBody DocumentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentoService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Documento> actualizar(@PathVariable String id,
                                                @Valid @RequestBody DocumentoRequest req) {
        return ResponseEntity.ok(documentoService.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
