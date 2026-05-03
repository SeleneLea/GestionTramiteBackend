package com.example.demo.services;

import com.example.demo.models.DocumentoArchivo;
import com.example.demo.models.PermisoPuntoAtencion;
import com.example.demo.models.SesionEdicionDocumento;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.DocumentoArchivoRepository;
import com.example.demo.repositories.SesionEdicionDocumentoRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CU-38 — Orquesta las sesiones de edición colaborativa.
 *
 * Responsabilidades:
 *  - validar permisos al unirse (RN-P01, RN-E04),
 *  - mantener el roster en {@link SesionEdicionDocumento},
 *  - retransmitir operaciones a los demás participantes,
 *  - registrar auditoría resumida (un evento al JOIN y al LEAVE — RN-A01).
 *
 * No mantiene el estado del documento; eso vive en el cliente (Yjs).
 * Los snapshots se hacen vía REST CU-35 ({@code POST /api/documentos/{id}/versiones}).
 */
@Service
@Slf4j
public class EdicionColaborativaService {

    @Autowired private DocumentoArchivoRepository docRepo;
    @Autowired private SesionEdicionDocumentoRepository sesionRepo;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PermisoDocumentalService permisoService;
    @Autowired private AuditoriaDocumentoService auditoria;
    @Autowired private DocumentoCollabBroadcaster broadcaster;

    @Value("${app.documental.edicion-colaborativa.max-participantes:10}")
    private int maxParticipantes;

    /** Paleta para asignar color de cursor a cada participante. */
    private static final String[] COLORES = {
            "#ef4444", "#3b82f6", "#10b981", "#f59e0b",
            "#a855f7", "#06b6d4", "#ec4899", "#84cc16",
            "#f97316", "#6366f1"
    };

    // ── join ─────────────────────────────────────────────────────────────────

    /**
     * Un usuario se une a la sesión de edición de un documento.
     * Si no tiene permiso de edición, se le notifica un KICK y no entra al roster.
     */
    public void unirse(String documentoId, String usuarioId, String rol) {
        DocumentoArchivo doc = docRepo.findById(documentoId).orElse(null);
        if (doc == null) {
            broadcaster.presencia(documentoId, "kick",
                    Map.of("motivo", "documento_no_existe"), usuarioId);
            return;
        }

        if (!puedeEditar(doc, rol)) {
            broadcaster.presencia(documentoId, "kick",
                    Map.of("motivo", "sin_permiso"), usuarioId);
            return;
        }

        SesionEdicionDocumento sesion = upsertSesion(documentoId, doc.getNumeroVersionActual());

        if (sesion.getParticipantes().size() >= maxParticipantes
                && sesion.getParticipantes().stream().noneMatch(p -> usuarioId.equals(p.getUsuarioId()))) {
            broadcaster.presencia(documentoId, "kick",
                    Map.of("motivo", "max_participantes"), usuarioId);
            return;
        }

        // Añadir o actualizar participante
        Optional<SesionEdicionDocumento.Participante> existente = sesion.getParticipantes().stream()
                .filter(p -> usuarioId.equals(p.getUsuarioId()))
                .findFirst();

        SesionEdicionDocumento.Participante p;
        if (existente.isPresent()) {
            p = existente.get();
            p.setUltimoLatido(LocalDateTime.now());
        } else {
            p = new SesionEdicionDocumento.Participante(
                    usuarioId,
                    nombreDe(usuarioId),
                    colorPara(sesion.getParticipantes().size()),
                    0,
                    LocalDateTime.now());
            sesion.getParticipantes().add(p);
        }
        sesion.setUltimoLatido(LocalDateTime.now());
        sesionRepo.save(sesion);

        // Auditoría — un solo evento por sesión (no por op)
        auditoria.registrar(documentoId, doc.getVersionActualId(), usuarioId, rol,
                AuditoriaDocumentoService.EDICION_EN_VIVO, null, null,
                Map.of("accion", "join"));

        broadcaster.presencia(documentoId, "roster",
                Map.of("participantes", sesion.getParticipantes()), usuarioId);

        log.info("[CU-38] {} se unió a doc={} (total participantes: {})",
                usuarioId, documentoId, sesion.getParticipantes().size());
    }

    // ── leave ────────────────────────────────────────────────────────────────

    public void salir(String documentoId, String usuarioId, String rol) {
        SesionEdicionDocumento sesion = sesionRepo.findByDocumentoArchivoId(documentoId).orElse(null);
        if (sesion == null) return;

        sesion.getParticipantes().removeIf(p -> usuarioId.equals(p.getUsuarioId()));
        sesion.setUltimoLatido(LocalDateTime.now());

        if (sesion.getParticipantes().isEmpty()) {
            sesionRepo.delete(sesion);
        } else {
            sesionRepo.save(sesion);
        }

        DocumentoArchivo doc = docRepo.findById(documentoId).orElse(null);
        if (doc != null) {
            auditoria.registrar(documentoId, doc.getVersionActualId(), usuarioId, rol,
                    AuditoriaDocumentoService.EDICION_EN_VIVO, null, null,
                    Map.of("accion", "leave"));
        }

        broadcaster.presencia(documentoId, "roster",
                Map.of("participantes",
                        sesion.getParticipantes() == null ? new ArrayList<>() : sesion.getParticipantes()),
                usuarioId);

        log.info("[CU-38] {} salió de doc={}", usuarioId, documentoId);
    }

    // ── op (operación CRDT) ──────────────────────────────────────────────────

    public void aplicarOp(String documentoId, String usuarioId, String rol, Object op) {
        DocumentoArchivo doc = docRepo.findById(documentoId).orElse(null);
        if (doc == null) return;

        // Re-verificar permiso (puede haber cambiado en caliente)
        if (!puedeEditar(doc, rol)) {
            broadcaster.presencia(documentoId, "kick",
                    Map.of("motivo", "permiso_revocado"), usuarioId);
            return;
        }

        // Latido implícito al enviar una op
        sesionRepo.findByDocumentoArchivoId(documentoId).ifPresent(s -> {
            s.getParticipantes().stream()
                    .filter(p -> usuarioId.equals(p.getUsuarioId()))
                    .findFirst()
                    .ifPresent(p -> p.setUltimoLatido(LocalDateTime.now()));
            s.setUltimoLatido(LocalDateTime.now());
            s.setCambiosPendientes(s.getCambiosPendientes() + 1);
            sesionRepo.save(s);
        });

        // Retransmisión sin tocar el contenido — los clientes (Yjs) lo aplican.
        broadcaster.edicion(documentoId, "op", op, usuarioId);
    }

    // ── cursor ───────────────────────────────────────────────────────────────

    public void actualizarCursor(String documentoId, String usuarioId, Object payload) {
        sesionRepo.findByDocumentoArchivoId(documentoId).ifPresent(s -> {
            s.getParticipantes().stream()
                    .filter(p -> usuarioId.equals(p.getUsuarioId()))
                    .findFirst()
                    .ifPresent(p -> {
                        if (payload instanceof Map<?, ?> m && m.get("cursorPos") instanceof Number n) {
                            p.setCursorPos(n.intValue());
                        }
                        p.setUltimoLatido(LocalDateTime.now());
                    });
            sesionRepo.save(s);
        });

        broadcaster.presencia(documentoId, "cursor", payload, usuarioId);
    }

    // ── purga (llamada desde scheduler) ──────────────────────────────────────

    /**
     * Purga participantes cuyo latido sea más antiguo que el TTL configurado y
     * elimina sesiones vacías. Emite roster actualizado si hubo cambios.
     */
    public int purgar(Duration ttl) {
        LocalDateTime corte = LocalDateTime.now().minus(ttl);
        int afectadas = 0;

        for (SesionEdicionDocumento s : sesionRepo.findByUltimoLatidoBefore(corte)) {
            int antes = s.getParticipantes().size();
            s.getParticipantes().removeIf(p ->
                    p.getUltimoLatido() == null || p.getUltimoLatido().isBefore(corte));
            int despues = s.getParticipantes().size();

            if (despues == 0) {
                sesionRepo.delete(s);
                broadcaster.presencia(s.getDocumentoArchivoId(), "roster",
                        Map.of("participantes", new ArrayList<>()), null);
                afectadas++;
            } else if (despues < antes) {
                sesionRepo.save(s);
                broadcaster.presencia(s.getDocumentoArchivoId(), "roster",
                        Map.of("participantes", s.getParticipantes()), null);
                afectadas++;
            }
        }
        return afectadas;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean puedeEditar(DocumentoArchivo doc, String rol) {
        // Admins editan siempre (super-permiso, RN-P03)
        if (rol != null && rol.contains("ADMINISTRADOR")) return true;

        if (doc.getActividadId() == null || doc.getPoliticaId() == null) {
            // Sin actividad configurada → default SOLO_LECTURA (RN-P01)
            return false;
        }
        PermisoPuntoAtencion permiso = permisoService.buscarOPorDefecto(
                doc.getPoliticaId(), doc.getActividadId());
        return permisoService.permiteEscritura(permiso.getNivelAcceso());
    }

    private SesionEdicionDocumento upsertSesion(String documentoId, int versionBase) {
        Optional<SesionEdicionDocumento> existente = sesionRepo.findByDocumentoArchivoId(documentoId);
        if (existente.isPresent()) return existente.get();

        SesionEdicionDocumento s = new SesionEdicionDocumento();
        s.setDocumentoArchivoId(documentoId);
        s.setParticipantes(new ArrayList<>());
        s.setIniciada(LocalDateTime.now());
        s.setUltimoLatido(LocalDateTime.now());
        s.setVersionBase(versionBase);
        s.setCambiosPendientes(0);
        try {
            return sesionRepo.save(s);
        } catch (DuplicateKeyException race) {
            // Otro JOIN concurrente la creó primero — releerla
            return sesionRepo.findByDocumentoArchivoId(documentoId)
                    .orElseThrow(() -> race);
        }
    }

    private String nombreDe(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(this::nombreCompleto)
                .orElse(usuarioId);
    }

    private String nombreCompleto(Usuario u) {
        String n = (u.getNombre() != null ? u.getNombre() : "")
                + (u.getApellido() != null ? " " + u.getApellido() : "");
        return n.isBlank() ? u.getEmail() : n.trim();
    }

    private String colorPara(int indice) {
        return COLORES[Math.floorMod(indice, COLORES.length)];
    }

    /** Listar participantes actuales (consumido por un endpoint REST opcional). */
    public List<SesionEdicionDocumento.Participante> participantes(String documentoId) {
        return sesionRepo.findByDocumentoArchivoId(documentoId)
                .map(SesionEdicionDocumento::getParticipantes)
                .orElseGet(ArrayList::new);
    }
}
