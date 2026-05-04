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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

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
    @Autowired private MongoTemplate mongoTemplate;

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

        SesionEdicionDocumento base = upsertSesion(documentoId, doc.getNumeroVersionActual());

        // El aforo (RN-E04) se re-evalúa DENTRO de la mutación reaplicada en cada
        // reintento: un conflicto de versión puede traer una lectura fresca ya llena,
        // así que comprobar sobre `base` no basta. Si se excede, no se añade y se marca
        // el kick para emitirlo tras la persistencia.
        boolean[] aforoExcedido = { false };

        // Añadir o actualizar participante (read-modify-write tolerante a concurrencia)
        SesionEdicionDocumento sesion = guardarConReintento(documentoId, base, s -> {
            aforoExcedido[0] = false;
            Optional<SesionEdicionDocumento.Participante> existente = s.getParticipantes().stream()
                    .filter(p -> usuarioId.equals(p.getUsuarioId()))
                    .findFirst();

            if (existente.isPresent()) {
                existente.get().setUltimoLatido(LocalDateTime.now());
            } else if (s.getParticipantes().size() >= maxParticipantes) {
                // Aforo lleno en la lectura vigente: no se incorpora al roster.
                aforoExcedido[0] = true;
            } else {
                s.getParticipantes().add(new SesionEdicionDocumento.Participante(
                        usuarioId,
                        nombreDe(usuarioId),
                        colorPara(s.getParticipantes().size()),
                        0,
                        LocalDateTime.now()));
            }
            s.setUltimoLatido(LocalDateTime.now());
        });
        if (sesion == null) {
            // La sesión desapareció (purga concurrente); nada que retransmitir.
            log.warn("[CU-38] sesión de doc={} no disponible al unir a {}", documentoId, usuarioId);
            return;
        }
        if (aforoExcedido[0]) {
            broadcaster.presencia(documentoId, "kick",
                    Map.of("motivo", "max_participantes"), usuarioId);
            return;
        }

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
        SesionEdicionDocumento inicial = sesionRepo.findByDocumentoArchivoId(documentoId).orElse(null);
        if (inicial == null) return;

        // Quitar del roster (read-modify-write tolerante a concurrencia). Si queda vacía,
        // se elimina; si no, se persiste. Ambas vías reintentan ante conflicto de versión.
        SesionEdicionDocumento sesion = removerConReintento(documentoId, inicial, usuarioId);

        DocumentoArchivo doc = docRepo.findById(documentoId).orElse(null);
        if (doc != null) {
            auditoria.registrar(documentoId, doc.getVersionActualId(), usuarioId, rol,
                    AuditoriaDocumentoService.EDICION_EN_VIVO, null, null,
                    Map.of("accion", "leave"));
        }

        List<SesionEdicionDocumento.Participante> roster =
                (sesion == null || sesion.getParticipantes() == null)
                        ? new ArrayList<>()
                        : sesion.getParticipantes();
        broadcaster.presencia(documentoId, "roster",
                Map.of("participantes", roster), usuarioId);

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

        // Latido implícito al enviar una op (read-modify-write tolerante a concurrencia).
        // El canal STOMP no tiene GlobalExceptionHandler: si tras los reintentos persiste
        // el conflicto de versión, se degrada a no-op (el latido es best-effort y la op se
        // retransmite igualmente) en vez de propagar la excepción al socket.
        try {
            sesionRepo.findByDocumentoArchivoId(documentoId).ifPresent(base ->
                    guardarConReintento(documentoId, base, s -> {
                        s.getParticipantes().stream()
                                .filter(p -> usuarioId.equals(p.getUsuarioId()))
                                .findFirst()
                                .ifPresent(p -> p.setUltimoLatido(LocalDateTime.now()));
                        s.setUltimoLatido(LocalDateTime.now());
                        s.setCambiosPendientes(s.getCambiosPendientes() + 1);
                    }));
        } catch (OptimisticLockingFailureException conflicto) {
            log.debug("[CU-38] latido de op no persistido en doc={} por conflicto de versión", documentoId);
        }

        // Retransmisión sin tocar el contenido — los clientes (Yjs) lo aplican.
        broadcaster.edicion(documentoId, "op", op, usuarioId);
    }

    // ── cursor ───────────────────────────────────────────────────────────────

    public void actualizarCursor(String documentoId, String usuarioId, Object payload) {
        // Evento de altísima frecuencia: NO se hace save() con @Version por cada cursor
        // (provocaría conflictos de versión y contención constantes). Se persiste el latido
        // del participante con una actualización parcial SIN versionado vía MongoTemplate,
        // usando el operador posicional `$` para tocar solo el participante emisor.
        // Si no hay sesión/participante coincidente, updateFirst no afecta a nada (no-op).
        Update update = new Update().set("participantes.$.ultimoLatido", LocalDateTime.now());
        if (payload instanceof Map<?, ?> m && m.get("cursorPos") instanceof Number n) {
            update.set("participantes.$.cursorPos", n.intValue());
        }
        mongoTemplate.updateFirst(
                new Query(Criteria.where("documentoArchivoId").is(documentoId)
                        .and("participantes.usuarioId").is(usuarioId)),
                update,
                SesionEdicionDocumento.class);

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

        // Dos criterios complementarios, deduplicados por id:
        //  (a) latido del PARTICIPANTE vencido: captura sesiones aún "vivas"
        //      (alguien activo mantiene el latido de la sesión fresco) que
        //      arrastran participantes caducados, para podarlos sin borrar la sesión.
        //  (b) latido de la SESIÓN vencido: captura sesiones totalmente inactivas
        //      y las huérfanas/vacías (participantes=[]), que el criterio (a) no
        //      puede ver porque no tienen ningún subdocumento que casar.
        Map<String, SesionEdicionDocumento> candidatas = new LinkedHashMap<>();
        for (SesionEdicionDocumento s : sesionRepo.findByParticipantes_UltimoLatidoBefore(corte)) {
            candidatas.put(s.getId(), s);
        }
        for (SesionEdicionDocumento s : sesionRepo.findByUltimoLatidoBefore(corte)) {
            candidatas.putIfAbsent(s.getId(), s);
        }

        for (SesionEdicionDocumento s : candidatas.values()) {
            // Cada sesión se aísla: un conflicto de versión (edición concurrente que
            // tocó la sesión entre la lectura del lote y este delete/save) no debe
            // abortar el resto de la purga. Se registra en debug y se continúa.
            try {
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
            } catch (OptimisticLockingFailureException conflicto) {
                log.debug("[CU-38] purga de sesión doc={} omitida por conflicto de versión (edición concurrente)",
                        s.getDocumentoArchivoId());
                continue;
            }
        }
        return afectadas;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Número máximo de intentos ante conflicto de versión (RN-C01). */
    private static final int MAX_INTENTOS_CONCURRENCIA = 3;

    /**
     * Aplica una mutación sobre la sesión y la persiste tolerando concurrencia.
     * Ante {@link OptimisticLockingFailureException} relee la sesión por documento
     * y reaplica la mutación, hasta {@value #MAX_INTENTOS_CONCURRENCIA} intentos.
     *
     * @param documentoId clave para releer la sesión tras un conflicto.
     * @param base        sesión recién leída sobre la que se intenta primero.
     * @param mutacion    cambio idempotente a aplicar antes de guardar.
     * @return la sesión persistida, o {@code null} si dejó de existir (p. ej. purga concurrente).
     */
    private SesionEdicionDocumento guardarConReintento(
            String documentoId, SesionEdicionDocumento base, Consumer<SesionEdicionDocumento> mutacion) {
        SesionEdicionDocumento sesion = base;
        for (int intento = 1; intento <= MAX_INTENTOS_CONCURRENCIA; intento++) {
            mutacion.accept(sesion);
            try {
                return sesionRepo.save(sesion);
            } catch (OptimisticLockingFailureException conflicto) {
                SesionEdicionDocumento fresca =
                        sesionRepo.findByDocumentoArchivoId(documentoId).orElse(null);
                if (fresca == null) {
                    log.warn("[CU-38] sesión de doc={} eliminada durante edición concurrente", documentoId);
                    return null;
                }
                if (intento == MAX_INTENTOS_CONCURRENCIA) {
                    log.warn("[CU-38] conflicto de versión persistente en doc={} tras {} intentos",
                            documentoId, MAX_INTENTOS_CONCURRENCIA);
                    throw conflicto;
                }
                sesion = fresca; // releer y reaplicar la mutación
            }
        }
        return sesion;
    }

    /**
     * Quita un participante del roster tolerando concurrencia. Si la sesión queda
     * vacía se elimina (devolviendo {@code null}); en caso contrario se persiste.
     * Reintenta ante conflicto de versión releyendo por documento.
     */
    private SesionEdicionDocumento removerConReintento(
            String documentoId, SesionEdicionDocumento base, String usuarioId) {
        SesionEdicionDocumento sesion = base;
        for (int intento = 1; intento <= MAX_INTENTOS_CONCURRENCIA; intento++) {
            sesion.getParticipantes().removeIf(p -> usuarioId.equals(p.getUsuarioId()));
            sesion.setUltimoLatido(LocalDateTime.now());
            try {
                if (sesion.getParticipantes().isEmpty()) {
                    sesionRepo.delete(sesion);
                    return null;
                }
                return sesionRepo.save(sesion);
            } catch (OptimisticLockingFailureException conflicto) {
                SesionEdicionDocumento fresca =
                        sesionRepo.findByDocumentoArchivoId(documentoId).orElse(null);
                if (fresca == null) {
                    // Otro proceso ya la eliminó: el participante quedó fuera igualmente.
                    return null;
                }
                if (intento == MAX_INTENTOS_CONCURRENCIA) {
                    log.warn("[CU-38] conflicto de versión persistente al salir doc={} tras {} intentos",
                            documentoId, MAX_INTENTOS_CONCURRENCIA);
                    throw conflicto;
                }
                sesion = fresca; // releer y reaplicar la remoción
            }
        }
        return sesion;
    }

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
