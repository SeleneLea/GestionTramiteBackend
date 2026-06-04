package com.example.demo.services;

import com.example.demo.dto.DocumentoArchivoResponse;
import com.example.demo.dto.VersionDocumentoResponse;
import com.example.demo.models.DocumentoArchivo;
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
 * CU-35 — Crea nuevas versiones de un documento existente. Mantiene el historial.
 *
 * Reglas:
 *  - Hash idéntico al actual → IllegalArgumentException (mapea a 409, RN-V05).
 *  - Documento bloqueado por otro → IllegalStateException con prefijo "DOC_BLOQUEADO" (mapea a 423).
 */
@Service
@Slf4j
public class VersionadoService {

    @Autowired private DocumentoArchivoRepository docRepo;
    @Autowired private VersionDocumentoRepository versionRepo;
    @Autowired private RepositorioDocumentalService repositorioService;
    @Autowired private AuditoriaDocumentoService auditoria;
    @Autowired private S3StorageService s3;
    /** CU-36 — valida nivel de acceso de la actividad antes de versionar. */
    @Autowired private PermisoDocumentalService permisoService;

    public DocumentoArchivoResponse crearNuevaVersion(String documentoId,
                                                      String comentarioCambio,
                                                      MultipartFile archivo,
                                                      String usuarioId,
                                                      String rol,
                                                      String ip,
                                                      String userAgent) {

        DocumentoArchivo doc = docRepo.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentoId));

        // CU-36 — validar permiso de escritura ANTES de leer el archivo o tocar S3
        validarPermisoEscritura(doc.getPoliticaId(), doc.getActividadId(), rol);

        // Bloqueo exclusivo de otro usuario
        if (doc.getBloqueadoPor() != null && !doc.getBloqueadoPor().equals(usuarioId)) {
            throw new IllegalStateException(
                    "DOC_BLOQUEADO: el documento está bloqueado por " + doc.getBloqueadoPor());
        }

        byte[] bytes = leerBytes(archivo);
        String hash = sha256(bytes);

        VersionDocumento actual = versionRepo.findById(doc.getVersionActualId())
                .orElseThrow(() -> new IllegalStateException(
                        "Documento sin versión actual: " + documentoId));

        if (hash.equals(actual.getHashSha256())) {
            throw new IllegalArgumentException(
                    "DOC_VERSION_DUPLICADA: el contenido es idéntico a la versión actual");
        }

        int nuevoNumero = actual.getNumeroVersion() + 1;
        String uuid = UUID.randomUUID().toString();
        String ext = extensionDe(archivo.getOriginalFilename());
        // s3Key derivada del bucketKey del repo (ya es "tramites/{tramiteId}/")
        var repo = repositorioService.buscarPorId(doc.getRepositorioId());
        String s3Key = repo.getBucketKey() + uuid + "-v" + nuevoNumero + ext;

        // Sube primero a S3; si falla, no se modifica nada en Mongo
        s3.upload(s3Key, new ByteArrayInputStream(bytes),
                archivo.getContentType(), bytes.length);

        VersionDocumento v = new VersionDocumento();
        v.setDocumentoArchivoId(doc.getId());
        v.setNumeroVersion(nuevoNumero);
        v.setS3Bucket(s3.bucket());
        v.setS3Key(s3Key);
        v.setTamanoBytes(bytes.length);
        v.setMimeType(archivo.getContentType());
        v.setHashSha256(hash);
        v.setAutorId(usuarioId);
        v.setComentarioCambio(comentarioCambio);
        v.setFechaCreacion(LocalDateTime.now());
        v = versionRepo.save(v);

        doc.setVersionActualId(v.getId());
        doc.setNumeroVersionActual(nuevoNumero);
        docRepo.save(doc);

        repositorioService.incrementarTotales(doc.getRepositorioId(), bytes.length);

        auditoria.registrar(doc.getId(), v.getId(), usuarioId, rol,
                AuditoriaDocumentoService.NUEVA_VERSION, ip, userAgent,
                Map.of("numeroVersion", nuevoNumero,
                       "comentarioCambio", comentarioCambio != null ? comentarioCambio : ""));

        return new DocumentoArchivoResponse(
                doc.getId(), v.getId(), v.getNumeroVersion(),
                doc.getNombreLogico(), doc.getTipoDocumento(),
                v.getTamanoBytes(), v.getMimeType(),
                v.getAutorId(), v.getFechaCreacion(),
                null, null
        );
    }

    /**
     * Variante por BYTES (sin MultipartFile): la usa el callback de OnlyOffice al
     * guardar un documento co-editado. No re-valida permiso (OnlyOffice ya controló
     * el acceso al abrir el editor) ni exige IP/UserAgent. Si el contenido es
     * idéntico a la versión actual, NO crea versión (devuelve null).
     */
    public DocumentoArchivoResponse crearNuevaVersionDesdeBytes(
            String documentoId, byte[] bytes, String mimeType, String ext,
            String comentarioCambio, String autorId) {

        DocumentoArchivo doc = docRepo.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentoId));
        VersionDocumento actual = versionRepo.findById(doc.getVersionActualId())
                .orElseThrow(() -> new IllegalStateException("Documento sin versión actual: " + documentoId));

        String hash = sha256(bytes);
        if (hash.equals(actual.getHashSha256())) {
            return null; // OnlyOffice a veces dispara guardado sin cambios reales
        }

        int nuevoNumero = actual.getNumeroVersion() + 1;
        String extNorm = (ext == null || ext.isBlank()) ? "" : (ext.startsWith(".") ? ext : "." + ext);
        var repo = repositorioService.buscarPorId(doc.getRepositorioId());
        String s3Key = repo.getBucketKey() + UUID.randomUUID() + "-v" + nuevoNumero + extNorm;

        s3.upload(s3Key, new ByteArrayInputStream(bytes), mimeType, bytes.length);

        VersionDocumento v = new VersionDocumento();
        v.setDocumentoArchivoId(doc.getId());
        v.setNumeroVersion(nuevoNumero);
        v.setS3Bucket(s3.bucket());
        v.setS3Key(s3Key);
        v.setTamanoBytes(bytes.length);
        v.setMimeType(mimeType);
        v.setHashSha256(hash);
        v.setAutorId(autorId);
        v.setComentarioCambio(comentarioCambio);
        v.setFechaCreacion(LocalDateTime.now());
        v = versionRepo.save(v);

        doc.setVersionActualId(v.getId());
        doc.setNumeroVersionActual(nuevoNumero);
        docRepo.save(doc);

        repositorioService.incrementarTotales(doc.getRepositorioId(), bytes.length);
        auditoria.registrar(doc.getId(), v.getId(), autorId, "FUNCIONARIO",
                AuditoriaDocumentoService.NUEVA_VERSION, null, "OnlyOffice",
                Map.of("numeroVersion", nuevoNumero, "fuente", "onlyoffice-coedicion"));

        return new DocumentoArchivoResponse(
                doc.getId(), v.getId(), v.getNumeroVersion(),
                doc.getNombreLogico(), doc.getTipoDocumento(),
                v.getTamanoBytes(), v.getMimeType(),
                v.getAutorId(), v.getFechaCreacion(), null, null);
    }

    public List<VersionDocumentoResponse> listarVersiones(String documentoId) {
        DocumentoArchivo doc = docRepo.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentoId));
        String versionActualId = doc.getVersionActualId();
        return versionRepo.findByDocumentoArchivoIdOrderByNumeroVersionDesc(documentoId)
                .stream()
                .map(v -> new VersionDocumentoResponse(
                        v.getId(),
                        v.getNumeroVersion(),
                        v.getAutorId(),
                        v.getCoautores(),
                        v.getComentarioCambio(),
                        v.getTamanoBytes(),
                        v.getMimeType(),
                        v.getHashSha256(),
                        v.getFechaCreacion(),
                        v.getId().equals(versionActualId)
                ))
                .toList();
    }

    /** Misma regla que {@link DocumentoArchivoService}: SOLO_LECTURA bloquea escritura. */
    private void validarPermisoEscritura(String politicaId, String actividadId, String rol) {
        if (rol != null && rol.contains("ADMINISTRADOR")) return;
        if (politicaId == null || actividadId == null) {
            throw new AccessDeniedException(
                    "DOC_PERMISO_DENEGADO: faltan politicaId/actividadId del documento");
        }
        var permiso = permisoService.buscarOPorDefecto(politicaId, actividadId);
        if (!permisoService.permiteEscritura(permiso.getNivelAcceso())) {
            throw new AccessDeniedException(
                    "DOC_PERMISO_DENEGADO: el nivel " + permiso.getNivelAcceso()
                            + " no permite versionar en esta actividad");
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
