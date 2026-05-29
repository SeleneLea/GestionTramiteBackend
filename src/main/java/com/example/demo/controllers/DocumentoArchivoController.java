package com.example.demo.controllers;

import com.example.demo.dto.DocumentoArchivoResponse;
import com.example.demo.dto.PreviewDocumentoResponse;
import com.example.demo.dto.VersionDocumentoResponse;
import com.example.demo.models.DocumentoArchivo;
import com.example.demo.services.DocumentoArchivoService;
import com.example.demo.services.VersionadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Endpoints de gestión documental (CU-33, CU-34, CU-35).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Documentos del repositorio",
     description = "CU-33/34/35 — subir, preview y versionar documentos del expediente")
public class DocumentoArchivoController {

    @Autowired private DocumentoArchivoService docService;
    @Autowired private VersionadoService versionadoService;

    // ── CU-33 · Subir documento ──────────────────────────────────────────────

    @PostMapping(value = "/tramites/{tramiteId}/documentos", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR','CLIENTE')")
    @Operation(summary = "Subir documento nuevo (versión 1) al trámite")
    public ResponseEntity<DocumentoArchivoResponse> subir(
            @PathVariable String tramiteId,
            // Opcional cuando viene nodoId: en paralelo el front no conoce la
            // actividad y el servicio la resuelve desde el nodo de la rama.
            @RequestParam(required = false) String actividadId,
            @RequestParam(required = false) String documentoRequeridoId,
            @RequestParam(required = false) String corrigeDocumentoId,
            @RequestParam(required = false) String nodoId,
            @RequestParam String tipoDocumento,
            @RequestParam String nombreLogico,
            @RequestParam(defaultValue = "false") boolean obligatorio,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication auth,
            HttpServletRequest request) {

        DocumentoArchivoResponse resp = docService.subirPorTramite(
                tramiteId, actividadId, documentoRequeridoId, corrigeDocumentoId, nodoId,
                tipoDocumento, nombreLogico, obligatorio,
                archivo,
                auth.getName(), rolDe(auth),
                ipDe(request), request.getHeader("User-Agent"));

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ── CU-34 · Preview ──────────────────────────────────────────────────────

    @GetMapping("/documentos/{id}/preview")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "URL firmada S3 para vista previa")
    public ResponseEntity<PreviewDocumentoResponse> preview(@PathVariable String id,
                                                             Authentication auth,
                                                             HttpServletRequest request) {
        var data = docService.generarPreview(id, auth.getName(), rolDe(auth),
                ipDe(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(new PreviewDocumentoResponse(
                data.urlPreview(), data.mimeType(), data.expiraEn()));
    }

    // ── CU-37 · Descarga (auditada como DESCARGA, no como lectura) ───────────

    @GetMapping("/documentos/{id}/descarga")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "URL firmada S3 para descargar el documento (audita DESCARGA)")
    public ResponseEntity<PreviewDocumentoResponse> descarga(@PathVariable String id,
                                                             Authentication auth,
                                                             HttpServletRequest request) {
        var data = docService.generarDescarga(id, auth.getName(), rolDe(auth),
                ipDe(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(new PreviewDocumentoResponse(
                data.urlPreview(), data.mimeType(), data.expiraEn()));
    }

    // ── Listados ─────────────────────────────────────────────────────────────
    // CU-36: para funcionarios se aplican lectura/visibilidad por punto de atención.

    @GetMapping("/repositorios/{repositorioId}/documentos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar documentos del repositorio")
    public ResponseEntity<List<DocumentoArchivo>> listarPorRepositorio(@PathVariable String repositorioId,
                                                                       Authentication auth) {
        return ResponseEntity.ok(docService.filtrarVisibles(
                docService.listarPorRepositorio(repositorioId), rolDe(auth)));
    }

    @GetMapping("/tramites/{tramiteId}/documentos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar documentos del trámite (opcional filtro por actividadId)")
    public ResponseEntity<List<DocumentoArchivo>> listarPorTramite(
            @PathVariable String tramiteId,
            @RequestParam(required = false) String actividadId,
            Authentication auth) {
        return ResponseEntity.ok(docService.filtrarVisibles(
                docService.listarPorTramite(tramiteId, actividadId), rolDe(auth)));
    }

    // NOTA: NO se expone GET /api/documentos/{id} porque colisiona con el
    // DocumentoController legacy (catálogo de tipos de documento). El detalle
    // de un DocumentoArchivo se reconstruye desde el preview + listarVersiones.

    // ── CU-35 · Versionado ───────────────────────────────────────────────────

    @PostMapping(value = "/documentos/{id}/versiones", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(summary = "Crear nueva versión de un documento existente")
    public ResponseEntity<DocumentoArchivoResponse> nuevaVersion(
            @PathVariable String id,
            @RequestParam(required = false) String comentarioCambio,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication auth,
            HttpServletRequest request) {

        DocumentoArchivoResponse resp = versionadoService.crearNuevaVersion(
                id, comentarioCambio, archivo,
                auth.getName(), rolDe(auth),
                ipDe(request), request.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/documentos/{id}/versiones")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar historial de versiones")
    public ResponseEntity<List<VersionDocumentoResponse>> listarVersiones(@PathVariable String id) {
        return ResponseEntity.ok(versionadoService.listarVersiones(id));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String rolDe(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USUARIO");
    }

    private String ipDe(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
