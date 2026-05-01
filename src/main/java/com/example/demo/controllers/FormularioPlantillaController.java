package com.example.demo.controllers;

import com.example.demo.dto.FormularioPlantillaRequest;
import com.example.demo.models.CampoPlantilla;
import com.example.demo.models.FormularioPlantilla;
import com.example.demo.services.FormularioPlantillaService;
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
@RequestMapping("/api")
@Tag(name = "Formularios de Nodo",
     description = "CU-13: cada nodo de actividad puede tener un formulario diseñado desde el diagramador")
public class FormularioPlantillaController {

    @Autowired private FormularioPlantillaService formularioService;

    @GetMapping("/nodos/{nodoId}/formulario")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener el formulario asociado a un nodo (si tiene)")
    public ResponseEntity<FormularioPlantilla> obtenerPorNodo(@PathVariable String nodoId) {
        return formularioService.obtenerPorNodo(nodoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/nodos/{nodoId}/formulario")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Crear formulario para un nodo",
               description = "Solo el creador del diagrama o un colaborador con rol 'editor' aceptado. "
                       + "El diagrama debe estar en estado 'borrador' y el nodo ser de tipo 'actividad'.")
    public ResponseEntity<FormularioPlantilla> crear(@PathVariable String nodoId,
                                                      @Valid @RequestBody FormularioPlantillaRequest req,
                                                      Authentication auth) {
        FormularioPlantilla f = formularioService.crearParaNodo(nodoId, req, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(f);
    }

    @GetMapping("/formularios-plantilla/{formularioId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FormularioPlantilla> buscar(@PathVariable String formularioId) {
        return formularioService.obtenerPorId(formularioId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/formularios-plantilla/{formularioId}/campos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar campos del formulario ordenados")
    public ResponseEntity<List<CampoPlantilla>> listarCampos(@PathVariable String formularioId) {
        return ResponseEntity.ok(formularioService.listarCampos(formularioId));
    }

    @PutMapping("/formularios-plantilla/{formularioId}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Actualizar datos generales del formulario (nombre, adjuntos, dictado de voz)")
    public ResponseEntity<FormularioPlantilla> actualizar(@PathVariable String formularioId,
                                                           @Valid @RequestBody FormularioPlantillaRequest req,
                                                           Authentication auth) {
        return ResponseEntity.ok(formularioService.actualizar(formularioId, req, auth.getName()));
    }

    @DeleteMapping("/formularios-plantilla/{formularioId}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Eliminar el formulario del nodo (también borra sus campos)")
    public ResponseEntity<Void> eliminar(@PathVariable String formularioId, Authentication auth) {
        formularioService.eliminar(formularioId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
