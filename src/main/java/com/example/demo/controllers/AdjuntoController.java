package com.example.demo.controllers;

import com.example.demo.models.Adjunto;
import com.example.demo.repositories.AdjuntoRepository;
import com.example.demo.repositories.TramiteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tramites/{tramiteId}/adjuntos")
@Tag(name = "Adjuntos de Trámite", description = "Subir y consultar documentos por trámite y actividad")
public class AdjuntoController {

    @Autowired
    private AdjuntoRepository adjuntoRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    @Value("${app.uploads.path:uploads}")
    private String uploadsPath;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Subir documento",
               description = "Sube la imagen de un documento requerido para una actividad del trámite")
    public ResponseEntity<Adjunto> subir(
            @PathVariable String tramiteId,
            @RequestParam String actividadId,
            @RequestParam String documentoNombre,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication auth) throws IOException {

        if (!tramiteRepository.existsById(tramiteId)) {
            return ResponseEntity.notFound().build();
        }

        String extension = "";
        String originalName = archivo.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String nombreGuardado = UUID.randomUUID() + extension;
        Path destino = Paths.get(uploadsPath, "tramites", tramiteId, nombreGuardado);
        Files.createDirectories(destino.getParent());
        archivo.transferTo(destino.toFile());

        Adjunto adjunto = new Adjunto();
        adjunto.setTramiteId(tramiteId);
        adjunto.setActividadId(actividadId);
        adjunto.setDocumentoNombre(documentoNombre);
        adjunto.setNombreArchivo(originalName != null ? originalName : nombreGuardado);
        adjunto.setTipoMime(archivo.getContentType());
        adjunto.setUrlAlmacenamiento("/uploads/tramites/" + tramiteId + "/" + nombreGuardado);
        adjunto.setTamanoBytes(archivo.getSize());
        adjunto.setSubidoPorId(auth.getName());
        adjunto.setFechaSubida(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(adjuntoRepository.save(adjunto));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar adjuntos del trámite",
               description = "Filtra opcionalmente por actividadId")
    public ResponseEntity<List<Adjunto>> listar(
            @PathVariable String tramiteId,
            @RequestParam(required = false) String actividadId) {

        List<Adjunto> resultado = actividadId != null && !actividadId.isBlank()
                ? adjuntoRepository.findByTramiteIdAndActividadId(tramiteId, actividadId)
                : adjuntoRepository.findByTramiteId(tramiteId);

        return ResponseEntity.ok(resultado);
    }
}
