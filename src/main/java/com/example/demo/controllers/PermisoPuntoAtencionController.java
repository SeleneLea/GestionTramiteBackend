package com.example.demo.controllers;

import com.example.demo.dto.PermisoPuntoAtencionRequest;
import com.example.demo.models.PermisoPuntoAtencion;
import com.example.demo.services.PermisoDocumentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Permisos por punto de atención",
     description = "CU-36 — nivel de acceso documental por actividad/nodo")
public class PermisoPuntoAtencionController {

    @Autowired
    private PermisoDocumentalService service;

    @PutMapping("/actividades/{actividadId}/permiso-documental")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear o actualizar permiso documental de un punto de atención",
               description = "Niveles válidos: SOLO_LECTURA, SOLO_EDICION, LECTURA_Y_EDICION")
    public ResponseEntity<PermisoPuntoAtencion> upsert(@PathVariable String actividadId,
                                                        @Valid @RequestBody PermisoPuntoAtencionRequest req,
                                                        Authentication auth) {
        req.setActividadId(actividadId);
        return ResponseEntity.ok(service.upsert(req, auth.getName()));
    }

    @GetMapping("/politicas/{politicaId}/permisos-documentales")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar permisos documentales de una política")
    public ResponseEntity<List<PermisoPuntoAtencion>> listarPorPolitica(@PathVariable String politicaId) {
        return ResponseEntity.ok(service.listarPorPolitica(politicaId));
    }

    @GetMapping("/politicas/{politicaId}/actividades/{actividadId}/permiso-documental")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener permiso (o default SOLO_LECTURA si no existe)")
    public ResponseEntity<PermisoPuntoAtencion> obtener(@PathVariable String politicaId,
                                                         @PathVariable String actividadId) {
        return ResponseEntity.ok(service.buscarOPorDefecto(politicaId, actividadId));
    }
}
