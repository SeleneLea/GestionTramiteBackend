package com.example.demo.controllers;

import com.example.demo.dto.CampoPlantillaRequest;
import com.example.demo.models.CampoPlantilla;
import com.example.demo.services.CampoPlantillaService;
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
@Tag(name = "Campos del Formulario",
     description = "CU-13: agregar, editar, eliminar y reordenar los campos (espacios y checks) del formulario diseñado")
public class CampoPlantillaController {

    @Autowired private CampoPlantillaService campoService;

    @PostMapping("/formularios-plantilla/{formularioId}/campos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Agregar un campo al formulario",
               description = "tipos soportados: texto, textarea, numero, fecha, select, checkbox, radio, archivo. "
                       + "select y radio requieren 'opciones'.")
    public ResponseEntity<CampoPlantilla> agregar(@PathVariable String formularioId,
                                                   @Valid @RequestBody CampoPlantillaRequest req,
                                                   Authentication auth) {
        CampoPlantilla c = campoService.agregar(formularioId, req, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    @PutMapping("/campos-plantilla/{campoId}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Actualizar un campo del formulario")
    public ResponseEntity<CampoPlantilla> actualizar(@PathVariable String campoId,
                                                      @Valid @RequestBody CampoPlantillaRequest req,
                                                      Authentication auth) {
        return ResponseEntity.ok(campoService.actualizar(campoId, req, auth.getName()));
    }

    @DeleteMapping("/campos-plantilla/{campoId}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Eliminar un campo del formulario")
    public ResponseEntity<Void> eliminar(@PathVariable String campoId, Authentication auth) {
        campoService.eliminar(campoId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/formularios-plantilla/{formularioId}/campos/reordenar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','FUNCIONARIO')")
    @Operation(summary = "Reordenar los campos del formulario",
               description = "Enviar la lista completa de IDs de campos en el nuevo orden")
    public ResponseEntity<List<CampoPlantilla>> reordenar(@PathVariable String formularioId,
                                                           @RequestBody List<String> idsEnOrden,
                                                           Authentication auth) {
        return ResponseEntity.ok(campoService.reordenar(formularioId, idsEnOrden, auth.getName()));
    }
}
