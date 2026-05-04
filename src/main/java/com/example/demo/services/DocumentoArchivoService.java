package com.example.demo.services;

import com.example.demo.dto.DocumentoArchivoResponse;
import com.example.demo.models.DocumentoArchivo;
import com.example.demo.models.RepositorioDocumental;
import com.example.demo.models.VersionDocumento;
import com.example.demo.repositories.DocumentoArchivoRepository;
import com.example.demo.repositories.VersionDocumentoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CU-33 — Crea el primer registro de un documento + versión #1 + auditoría SUBIDA.
 *
 * El versionado de archivos existentes se hace en {@link VersionadoService}.
 */
@Service
@Slf4j
public class DocumentoArchivoService {

    @Autowired private DocumentoArchivoRepository docRepo;
    @Autowired private VersionDocumentoRepository versionRepo;
    @Autowired private RepositorioDocumentalService repositorioService;
    @Autowired private AuditoriaDocumentoService auditoria;
    @Autowired private S3StorageService s3;
    /** CU-36 — Valida que el punto de atención permita escritura antes de subir. */
    @Autowired private PermisoDocumentalService permisoService;

    /**
     * Sube un archivo nuevo al repositorio. Si ya hay otro documento del mismo
     * trámite/actividad con hash idéntico, lanza {@link IllegalArgumentException}
     * (mapeará a 409 desde el controller).
     */
    public DocumentoArchivoResponse subir(String repositorioId,
                                          String tramiteId,
                                          String actividadId,
                                          String nodoId,
                                          String tipoDocumento,
                                          String nombreLogico,
                                          boolean obligatorio,
                                          MultipartFile archivo,
                                          String usuarioId,
                                          String rol,
                                          String ip,
                                          String userAgent) {

        RepositorioDocumental repo = repositorioService.buscarPorId(repositorioId);

        // CU-36 — validar permiso de escritura ANTES de leer el archivo o tocar S3
        validarPermisoEscritura(repo.getPoliticaId(), actividadId, rol);

        byte[] bytes = leerBytes(archivo);
        String hash = sha256(bytes);

        // Anti-duplicado dentro del mismo trámite/actividad
        for (DocumentoArchivo existente :
                docRepo.findByTramiteIdAndActividadIdAndActivoTrue(tramiteId, actividadId)) {
            if (existente.getVersionActualId() == null) continue;
            var v = versionRepo.findById(existente.getVersionActualId()).orElse(null);
            if (v != null && hash.equals(v.getHashSha256())) {
                throw new IllegalArgumentException(
                        "DOC_HASH_DUPLICADO: ya existe un documento con el mismo contenido en este trámite/actividad");
            }
        }

        String uuid = UUID.randomUUID().toString();
        String ext = extensionDe(archivo.getOriginalFilename());
        String s3Key = repo.getBucketKey()
                + "tramites/" + tramiteId + "/"
                + uuid + "-v1" + ext;

        // 1) Crear DocumentoArchivo
        DocumentoArchivo doc = new DocumentoArchivo();
        doc.setRepositorioId(repositorioId);
        doc.setPoliticaId(repo.getPoliticaId());
        doc.setTramiteId(tramiteId);
        doc.setActividadId(actividadId);
        doc.setNodoId(nodoId);
        doc.setNombreLogico(nombreLogico);
        doc.setTipoDocumento(tipoDocumento);
        doc.setObligatorio(obligatorio);
        doc.setNumeroVersionActual(1);
        doc.setCreadoPorId(usuarioId);
        doc.setFechaCreacion(LocalDateTime.now());
        doc.setActivo(true);
        doc = docRepo.save(doc);

        // 2) Subir binario a S3
        try {
            s3.upload(s3Key, new ByteArrayInputStream(bytes),
                    archivo.getContentType(), bytes.length);
        } catch (RuntimeException ex) {
            // Rollback de la fila — el cliente recibe 503 / propagación de la excepción
            docRepo.deleteById(doc.getId());
            throw ex;
        }

        // 3) Crear VersionDocumento #1
        VersionDocumento v = new VersionDocumento();
        v.setDocumentoArchivoId(doc.getId());
        v.setNumeroVersion(1);
        v.setS3Bucket(s3.bucket());
        v.setS3Key(s3Key);
        v.setTamanoBytes(bytes.length);
        v.setMimeType(archivo.getContentType());
        v.setHashSha256(hash);
        v.setAutorId(usuarioId);
        v.setFechaCreacion(LocalDateTime.now());
        v = versionRepo.save(v);

        // 4) Apuntar el doc a la versión actual
        doc.setVersionActualId(v.getId());
        docRepo.save(doc);

        // 5) Totales del repositorio
        repositorioService.incrementarTotales(repositorioId, bytes.length);

        // 6) Auditoría SUBIDA
        auditoria.registrar(doc.getId(), v.getId(), usuarioId, rol,
                AuditoriaDocumentoService.SUBIDA, ip, userAgent,
                Map.of("nombreLogico", nombreLogico,
                       "tamanoBytes", bytes.length));

        return toResponse(doc, v, null, null);
    }

    /**
     * Sube el documento de RESOLUCIÓN entregable de un trámite (lo que el cliente
     * descarga al finalizar). No valida permiso de punto de atención: lo sube el
     * responsable que aprueba, ya autorizado a cerrar el trámite. Marca
     * {@code esResolucion=true} en el {@link DocumentoArchivo} resultante.
     */
    public DocumentoArchivo subirResolucion(String repositorioId,
                                            String tramiteId,
                                            String nodoId,
                                            String tipoDocumento,
                                            String nombreLogico,
                                            MultipartFile archivo,
                                            String usuarioId,
                                            String rol,
                                            String ip,
                                            String userAgent) {

        RepositorioDocumental repo = repositorioService.buscarPorId(repositorioId);

        byte[] bytes = leerBytes(archivo);
        String hash = sha256(bytes);

        String uuid = UUID.randomUUID().toString();
        String ext = extensionDe(archivo.getOriginalFilename());
        String s3Key = repo.getBucketKey()
                + "tramites/" + tramiteId + "/resolucion/"
                + uuid + "-v1" + ext;

        DocumentoArchivo doc = new DocumentoArchivo();
        doc.setRepositorioId(repositorioId);
        doc.setPoliticaId(repo.getPoliticaId());
        doc.setTramiteId(tramiteId);
        doc.setNodoId(nodoId);
        doc.setNombreLogico(nombreLogico);
        doc.setTipoDocumento(tipoDocumento);
        doc.setObligatorio(false);
        doc.setEsResolucion(true);
        doc.setNumeroVersionActual(1);
        doc.setCreadoPorId(usuarioId);
        doc.setFechaCreacion(LocalDateTime.now());
        doc.setActivo(true);
        doc = docRepo.save(doc);

        try {
            s3.upload(s3Key, new ByteArrayInputStream(bytes),
                    archivo.getContentType(), bytes.length);
        } catch (RuntimeException ex) {
            docRepo.deleteById(doc.getId());
            throw ex;
        }

        VersionDocumento v = new VersionDocumento();
        v.setDocumentoArchivoId(doc.getId());
        v.setNumeroVersion(1);
        v.setS3Bucket(s3.bucket());
        v.setS3Key(s3Key);
        v.setTamanoBytes(bytes.length);
        v.setMimeType(archivo.getContentType());
        v.setHashSha256(hash);
        v.setAutorId(usuarioId);
        v.setFechaCreacion(LocalDateTime.now());
        v = versionRepo.save(v);

        doc.setVersionActualId(v.getId());
        docRepo.save(doc);

        repositorioService.incrementarTotales(repositorioId, bytes.length);

        auditoria.registrar(doc.getId(), v.getId(), usuarioId, rol,
                AuditoriaDocumentoService.SUBIDA, ip, userAgent,
                Map.of("nombreLogico", nombreLogico,
                       "esResolucion", true,
                       "tamanoBytes", bytes.length));

        return doc;
    }

    public List<DocumentoArchivo> listarPorRepositorio(String repositorioId) {
        return docRepo.findByRepositorioIdAndActivoTrue(repositorioId);
    }

    public List<DocumentoArchivo> listarPorTramite(String tramiteId, String actividadId) {
        if (actividadId != null && !actividadId.isBlank()) {
            return docRepo.findByTramiteIdAndActividadIdAndActivoTrue(tramiteId, actividadId);
        }
        return docRepo.findByTramiteIdAndActivoTrue(tramiteId);
    }

    public DocumentoArchivo buscarPorId(String id) {
        return docRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + id));
    }

    public PreviewData generarPreview(String documentoId, String usuarioId, String rol,
                                      String ip, String userAgent) {
        DocumentoArchivo doc = buscarPorId(documentoId);
        VersionDocumento v = versionRepo.findById(doc.getVersionActualId())
                .orElseThrow(() -> new IllegalStateException(
                        "Documento sin versión actual: " + documentoId));

        auditoria.registrar(doc.getId(), v.getId(), usuarioId, rol,
                AuditoriaDocumentoService.LECTURA, ip, userAgent, null);

        return new PreviewData(
                s3.presignedGet(v.getS3Key()).toString(),
                v.getMimeType(),
                s3.calcularExpiracion());
    }

    /** Datos para llenar {@code PreviewDocumentoResponse} sin acoplarse al DTO en el servicio. */
    public record PreviewData(String urlPreview, String mimeType, java.time.Instant expiraEn) {}

    DocumentoArchivoResponse toResponse(DocumentoArchivo doc,
                                        VersionDocumento v,
                                        String urlPreview,
                                        java.time.Instant expira) {
        return new DocumentoArchivoResponse(
                doc.getId(),
                v != null ? v.getId() : null,
                v != null ? v.getNumeroVersion() : doc.getNumeroVersionActual(),
                doc.getNombreLogico(),
                doc.getTipoDocumento(),
                v != null ? v.getTamanoBytes() : 0,
                v != null ? v.getMimeType() : null,
                v != null ? v.getAutorId() : doc.getCreadoPorId(),
                v != null ? v.getFechaCreacion() : doc.getFechaCreacion(),
                urlPreview,
                expira
        );
    }

    /**
     * CU-36 — Si el {@link PermisoPuntoAtencion} de la actividad no permite escritura,
     * lanza {@link AccessDeniedException} (mapea a 403 vía GlobalExceptionHandler).
     *
     * Reglas: admins pasan siempre (RN-P03). Sin permiso configurado el default es
     * SOLO_LECTURA (RN-P01), que también bloquea escritura.
     */
    private void validarPermisoEscritura(String politicaId, String actividadId, String rol) {
        if (rol != null && rol.contains("ADMINISTRADOR")) return;
        if (politicaId == null || actividadId == null) {
            throw new AccessDeniedException(
                    "DOC_PERMISO_DENEGADO: faltan politicaId/actividadId para validar el permiso");
        }
        var permiso = permisoService.buscarOPorDefecto(politicaId, actividadId);
        if (!permisoService.permiteEscritura(permiso.getNivelAcceso())) {
            throw new AccessDeniedException(
                    "DOC_PERMISO_DENEGADO: el nivel " + permiso.getNivelAcceso()
                            + " no permite escritura en esta actividad");
        }
    }

    private byte[] leerBytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage(), e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String extensionDe(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
