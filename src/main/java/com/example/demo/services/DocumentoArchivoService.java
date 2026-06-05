package com.example.demo.services;

import com.example.demo.dto.DocumentoArchivoResponse;
import com.example.demo.models.DocumentoArchivo;
import com.example.demo.models.RepositorioDocumental;
import com.example.demo.models.VersionDocumento;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.DocumentoArchivoRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.VersionDocumentoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DocumentoArchivoService {

    @Autowired private DocumentoArchivoRepository docRepo;
    @Autowired private VersionDocumentoRepository versionRepo;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private com.example.demo.repositories.NodoDiagramaRepository nodoRepository;
    @Autowired private RepositorioDocumentalService repositorioService;
    @Autowired private AuditoriaDocumentoService auditoria;
    @Autowired private S3StorageService s3;
    @Autowired private PermisoDocumentalService permisoService;
    @Autowired @org.springframework.context.annotation.Lazy private WorkflowEngineService workflowEngine;

    public DocumentoArchivoResponse subirPorTramite(String tramiteId,
                                                    String actividadId,
                                                    String documentoRequeridoId,
                                                    String corrigeDocumentoId,
                                                    String nodoId,
                                                    String tipoDocumento,
                                                    String nombreLogico,
                                                    boolean obligatorio,
                                                    MultipartFile archivo,
                                                    String usuarioId,
                                                    String rol,
                                                    String ip,
                                                    String userAgent) {

        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Tramite no encontrado"));

        if ((actividadId == null || actividadId.isBlank())
                && nodoId != null && !nodoId.isBlank()) {
            actividadId = nodoRepository.findById(nodoId)
                    .map(com.example.demo.models.NodoDiagrama::getActividadId)
                    .orElse(null);
        }
        if (actividadId == null || actividadId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo determinar la actividad del documento (envia actividadId o nodoId)");
        }

        RepositorioDocumental repo =
                repositorioService.crearAlIniciarTramite(tramiteId, tramite.getPoliticaId());

        if (tramite.getRepositorioId() == null) {
            tramite.setRepositorioId(repo.getId());
            tramiteRepository.save(tramite);
        }

        return subir(repo.getId(), tramiteId, actividadId, documentoRequeridoId, corrigeDocumentoId, nodoId,
                tipoDocumento, nombreLogico, obligatorio, archivo, usuarioId, rol, ip, userAgent);
    }

    public DocumentoArchivoResponse subir(String repositorioId,
                                          String tramiteId,
                                          String actividadId,
                                          String documentoRequeridoId,
                                          String corrigeDocumentoId,
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

        validarPermisoEscritura(repo.getPoliticaId(), actividadId, rol);

        byte[] bytes = leerBytes(archivo);
        String hash = sha256(bytes);

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
        String s3Key = repo.getBucketKey() + uuid + "-v1" + ext;

        DocumentoArchivo doc = new DocumentoArchivo();
        doc.setRepositorioId(repositorioId);
        doc.setPoliticaId(repo.getPoliticaId());
        doc.setTramiteId(tramiteId);
        doc.setActividadId(actividadId);
        doc.setDocumentoRequeridoId(
                (documentoRequeridoId != null && !documentoRequeridoId.isBlank())
                        ? documentoRequeridoId.trim() : null);
        doc.setNodoId(nodoId);
        doc.setNombreLogico(nombreLogico);
        doc.setTipoDocumento(tipoDocumento);
        doc.setObligatorio(obligatorio);
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
                       "tamanoBytes", bytes.length));

        try {
            workflowEngine.reanudarPorDocumentos(tramiteId, actividadId);
        } catch (RuntimeException ex) {
            log.warn("Auto-reanudacion por documentos fallo (tramite {} act {}): {}",
                    tramiteId, actividadId, ex.getMessage());
        }

        if (corrigeDocumentoId != null && !corrigeDocumentoId.isBlank()) {
            try {
                workflowEngine.limpiarDocumentoObservado(tramiteId, corrigeDocumentoId);
                docRepo.findById(corrigeDocumentoId.trim()).ifPresent(viejo -> {
                    viejo.setActivo(false);
                    docRepo.save(viejo);
                });
            } catch (RuntimeException ex) {
                log.warn("Reemplazo de documento observado fallo (tramite {}): {}", tramiteId, ex.getMessage());
            }
        }

        return toResponse(doc, v, null, null);
    }

    public String seedDocumento(String tramiteId, String politicaId, String actividadId, String nodoId,
                                String documentoRequeridoId, String nombreLogico, String tipoDocumento,
                                byte[] bytes, String mimeType) {
        RepositorioDocumental repo = repositorioService.crearAlIniciarTramite(tramiteId, politicaId);
        String uuid = UUID.randomUUID().toString();
        String s3Key = repo.getBucketKey() + uuid + "-v1.pdf";

        DocumentoArchivo doc = new DocumentoArchivo();
        doc.setRepositorioId(repo.getId());
        doc.setPoliticaId(repo.getPoliticaId());
        doc.setTramiteId(tramiteId);
        doc.setActividadId(actividadId);
        doc.setDocumentoRequeridoId(documentoRequeridoId);
        doc.setNodoId(nodoId);
        doc.setNombreLogico(nombreLogico);
        doc.setTipoDocumento(tipoDocumento);
        doc.setObligatorio(false);
        doc.setNumeroVersionActual(1);
        doc.setCreadoPorId("seed");
        doc.setFechaCreacion(LocalDateTime.now());
        doc.setActivo(true);
        doc = docRepo.save(doc);

        s3.upload(s3Key, new ByteArrayInputStream(bytes), mimeType, bytes.length);

        VersionDocumento v = new VersionDocumento();
        v.setDocumentoArchivoId(doc.getId());
        v.setNumeroVersion(1);
        v.setS3Bucket(s3.bucket());
        v.setS3Key(s3Key);
        v.setTamanoBytes(bytes.length);
        v.setMimeType(mimeType);
        v.setHashSha256(sha256(bytes));
        v.setAutorId("seed");
        v.setFechaCreacion(LocalDateTime.now());
        v = versionRepo.save(v);

        doc.setVersionActualId(v.getId());
        docRepo.save(doc);
        repositorioService.incrementarTotales(repo.getId(), bytes.length);
        return doc.getId();
    }

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
        String s3Key = repo.getBucketKey() + "resolucion/" + uuid + "-v1" + ext;

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

    public List<DocumentoArchivo> filtrarVisibles(List<DocumentoArchivo> docs, String rol) {
        if (rol == null || !rol.contains("FUNCIONARIO")) return docs;
        Map<String, com.example.demo.models.PermisoPuntoAtencion> cache = new java.util.HashMap<>();
        return docs.stream().filter(d -> esLegibleParaFuncionario(d, cache)).toList();
    }

    private static final java.util.Set<String> TIPOS_CATALOGO = java.util.Set.of(
            "PDF", "IMAGEN", "WORD", "EXCEL", "AUDIO", "VIDEO", "OTRO");

    private boolean esLegibleParaFuncionario(
            DocumentoArchivo doc,
            Map<String, com.example.demo.models.PermisoPuntoAtencion> cache) {
        if (doc.getPoliticaId() == null || doc.getActividadId() == null) {
            return true;
        }
        var permiso = cache.computeIfAbsent(
                doc.getPoliticaId() + "|" + doc.getActividadId(),
                k -> permisoService.buscarOPorDefecto(doc.getPoliticaId(), doc.getActividadId()));
        if (!permisoService.permiteLectura(permiso.getNivelAcceso())) return false;
        List<String> visibles = permiso.getTiposDocumentoVisibles();
        if (visibles == null || visibles.isEmpty()) return true;
        String tipo = doc.getTipoDocumento();
        return tipo == null || !TIPOS_CATALOGO.contains(tipo) || visibles.contains(tipo);
    }

    public DocumentoArchivo buscarPorId(String id) {
        return docRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + id));
    }

    public PreviewData generarPreview(String documentoId, String usuarioId, String rol,
                                      String ip, String userAgent) {
        return generarUrlFirmada(documentoId, usuarioId, rol, ip, userAgent,
                AuditoriaDocumentoService.LECTURA);
    }

    public PreviewData generarDescarga(String documentoId, String usuarioId, String rol,
                                       String ip, String userAgent) {
        return generarUrlFirmada(documentoId, usuarioId, rol, ip, userAgent,
                AuditoriaDocumentoService.DESCARGA);
    }

    private PreviewData generarUrlFirmada(String documentoId, String usuarioId, String rol,
                                          String ip, String userAgent, String accion) {
        DocumentoArchivo doc = buscarPorId(documentoId);
        VersionDocumento v = versionRepo.findById(doc.getVersionActualId())
                .orElseThrow(() -> new IllegalStateException(
                        "Documento sin versión actual: " + documentoId));

        validarPermisoLectura(doc, rol);

        auditoria.registrar(doc.getId(), v.getId(), usuarioId, rol,
                accion, ip, userAgent, null);

        return new PreviewData(
                s3.presignedGet(v.getS3Key()).toString(),
                v.getMimeType(),
                s3.calcularExpiracion());
    }

    private void validarPermisoLectura(DocumentoArchivo doc, String rol) {
        if (rol == null || !rol.contains("FUNCIONARIO")) return;
        if (!esLegibleParaFuncionario(doc, new java.util.HashMap<>())) {
            throw new AccessDeniedException(
                    "DOC_PERMISO_DENEGADO: el punto de atención no permite leer este documento");
        }
    }

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
