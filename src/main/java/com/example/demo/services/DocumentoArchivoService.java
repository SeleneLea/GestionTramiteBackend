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
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private com.example.demo.repositories.NodoDiagramaRepository nodoRepository;
    @Autowired private RepositorioDocumentalService repositorioService;
    @Autowired private AuditoriaDocumentoService auditoria;
    @Autowired private S3StorageService s3;
    /** CU-36 — Valida que el punto de atención permita escritura antes de subir. */
    @Autowired private PermisoDocumentalService permisoService;
    /** Compuerta de documentos: tras subir, intenta reanudar si ya no faltan requisitos. */
    @Autowired @org.springframework.context.annotation.Lazy private WorkflowEngineService workflowEngine;

    /**
     * Punto de entrada por trámite (CU-33). Resuelve el repositorio 1:1 del
     * trámite (creándolo de forma idempotente si aún no existe), hace backfill
     * del {@code repositorioId} en el {@link Tramite} y delega en {@link #subir}.
     */
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

        // En flujos PARALELOS el trámite no tiene nodo único (/estado no expone
        // actividad), así que el front puede mandar solo el nodoId de la SECCIÓN
        // de su rama y aquí se resuelve la actividad de ese nodo.
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

    /**
     * Sube un archivo nuevo al repositorio. Si ya hay otro documento del mismo
     * trámite/actividad con hash idéntico, lanza {@link IllegalArgumentException}
     * (mapeará a 409 desde el controller).
     */
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
        // bucketKey YA es "tramites/{tramiteId}/" — no concatenar el prefijo otra vez
        String s3Key = repo.getBucketKey() + uuid + "-v1" + ext;

        // 1) Crear DocumentoArchivo
        DocumentoArchivo doc = new DocumentoArchivo();
        doc.setRepositorioId(repositorioId);
        doc.setPoliticaId(repo.getPoliticaId());
        doc.setTramiteId(tramiteId);
        doc.setActividadId(actividadId);
        // Defensivo: limpiar espacios/saltos accidentales para que el match de la
        // compuerta no falle por whitespace; en blanco se trata como null.
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

        // 7) Compuerta: si esta subida completó los obligatorios del CLIENTE del paso,
        //    el sistema reanuda el trámite (PENDIENTE_DOCUMENTOS → PENDIENTE_RECEPCION).
        try {
            workflowEngine.reanudarPorDocumentos(tramiteId, actividadId);
        } catch (RuntimeException ex) {
            log.warn("Auto-reanudacion por documentos fallo (tramite {} act {}): {}",
                    tramiteId, actividadId, ex.getMessage());
        }

        // 8) Caso OBSERVADO: si esta subida corrige un documento que el funcionario marcó
        //    como "mal", quitarlo de la lista de observados (desaparece de "a corregir")
        //    y REEMPLAZAR el viejo: marcarlo inactivo (sale de las listas; queda en BD
        //    para auditoría). El nuevo documento ocupa su lugar.
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

    /**
     * Crea un documento SEMBRADO (sin MultipartFile, sin validación de permiso ni compuerta):
     * sube los bytes a S3 + DocumentoArchivo + VersionDocumento #1. Solo para el DocumentoSeeder.
     */
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
        // bucketKey YA es "tramites/{tramiteId}/" — solo añadir el subprefijo "resolucion/"
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

    /**
     * CU-36 — Aplica al listado la dimensión de LECTURA/visibilidad del permiso
     * por punto de atención, SOLO para funcionarios (los admins ven todo y el
     * cliente no es un punto de atención): se ocultan los documentos cuya
     * actividad no permite lectura (SOLO_EDICION) o cuyo tipo no está en
     * {@code tiposDocumentoVisibles} cuando la lista está configurada.
     */
    public List<DocumentoArchivo> filtrarVisibles(List<DocumentoArchivo> docs, String rol) {
        if (rol == null || !rol.contains("FUNCIONARIO")) return docs;
        Map<String, com.example.demo.models.PermisoPuntoAtencion> cache = new java.util.HashMap<>();
        return docs.stream().filter(d -> esLegibleParaFuncionario(d, cache)).toList();
    }

    /**
     * Tipos del CATÁLOGO sobre los que opera el filtro de visibilidad (mismos
     * que ofrece la UI del CU-36). El móvil llena {@code tipoDocumento} con el
     * NOMBRE del requisito (texto libre, p.ej. "Carta de solicitud"): esos
     * documentos NO se filtran por tipo — solo aplica el nivel de lectura —
     * porque ocultarlos rompería el flujo recepcionar→validar→observar.
     */
    private static final java.util.Set<String> TIPOS_CATALOGO = java.util.Set.of(
            "PDF", "IMAGEN", "WORD", "EXCEL", "AUDIO", "VIDEO", "OTRO");

    private boolean esLegibleParaFuncionario(
            DocumentoArchivo doc,
            Map<String, com.example.demo.models.PermisoPuntoAtencion> cache) {
        if (doc.getPoliticaId() == null || doc.getActividadId() == null) {
            return true; // sin punto de atención configurable → visible
        }
        var permiso = cache.computeIfAbsent(
                doc.getPoliticaId() + "|" + doc.getActividadId(),
                k -> permisoService.buscarOPorDefecto(doc.getPoliticaId(), doc.getActividadId()));
        if (!permisoService.permiteLectura(permiso.getNivelAcceso())) return false;
        List<String> visibles = permiso.getTiposDocumentoVisibles();
        if (visibles == null || visibles.isEmpty()) return true;
        String tipo = doc.getTipoDocumento();
        // El filtro por tipo solo restringe tipos del catálogo; el texto libre pasa.
        return tipo == null || !TIPOS_CATALOGO.contains(tipo) || visibles.contains(tipo);
    }

    public DocumentoArchivo buscarPorId(String id) {
        return docRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + id));
    }

    /** CU-34 — URL firmada para VER el documento; audita LECTURA. */
    public PreviewData generarPreview(String documentoId, String usuarioId, String rol,
                                      String ip, String userAgent) {
        return generarUrlFirmada(documentoId, usuarioId, rol, ip, userAgent,
                AuditoriaDocumentoService.LECTURA);
    }

    /** CU-37 — URL firmada para DESCARGAR el documento; audita DESCARGA. */
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

        // CU-36 — la dimensión de LECTURA del permiso por punto de atención
        // también aplica al acceso individual (preview/descarga) del funcionario.
        validarPermisoLectura(doc, rol);

        auditoria.registrar(doc.getId(), v.getId(), usuarioId, rol,
                accion, ip, userAgent, null);

        return new PreviewData(
                s3.presignedGet(v.getS3Key()).toString(),
                v.getMimeType(),
                s3.calcularExpiracion());
    }

    /**
     * CU-36 — Si el punto de atención del documento no permite LECTURA para el
     * funcionario (SOLO_EDICION, o tipo fuera de tiposDocumentoVisibles), lanza
     * {@link AccessDeniedException}. Admins y clientes no se restringen aquí.
     */
    private void validarPermisoLectura(DocumentoArchivo doc, String rol) {
        if (rol == null || !rol.contains("FUNCIONARIO")) return;
        if (!esLegibleParaFuncionario(doc, new java.util.HashMap<>())) {
            throw new AccessDeniedException(
                    "DOC_PERMISO_DENEGADO: el punto de atención no permite leer este documento");
        }
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
