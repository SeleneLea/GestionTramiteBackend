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

    private static final String[] COLORES = {
            "#ef4444", "#3b82f6", "#10b981", "#f59e0b",
            "#a855f7", "#06b6d4", "#ec4899", "#84cc16",
            "#f97316", "#6366f1"
    };

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

        boolean[] aforoExcedido = { false };

        SesionEdicionDocumento sesion = guardarConReintento(documentoId, base, s -> {
            aforoExcedido[0] = false;
            Optional<SesionEdicionDocumento.Participante> existente = s.getParticipantes().stream()
                    .filter(p -> usuarioId.equals(p.getUsuarioId()))
                    .findFirst();

            if (existente.isPresent()) {
                existente.get().setUltimoLatido(LocalDateTime.now());
            } else if (s.getParticipantes().size() >= maxParticipantes) {
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
            log.warn("[CU-38] sesión de doc={} no disponible al unir a {}", documentoId, usuarioId);
            return;
        }
        if (aforoExcedido[0]) {
            broadcaster.presencia(documentoId, "kick",
                    Map.of("motivo", "max_participantes"), usuarioId);
            return;
        }

        auditoria.registrar(documentoId, doc.getVersionActualId(), usuarioId, rol,
                AuditoriaDocumentoService.EDICION_EN_VIVO, null, null,
                Map.of("accion", "join"));

        broadcaster.presencia(documentoId, "roster",
                Map.of("participantes", sesion.getParticipantes()), usuarioId);

        broadcaster.edicion(documentoId, "snapshot",
                Map.of("contenido", sesion.getContenido() != null ? sesion.getContenido() : ""),
                usuarioId);

        log.info("[CU-38] {} se unió a doc={} (total participantes: {})",
                usuarioId, documentoId, sesion.getParticipantes().size());
    }

    public void salir(String documentoId, String usuarioId, String rol) {
        SesionEdicionDocumento inicial = sesionRepo.findByDocumentoArchivoId(documentoId).orElse(null);
        if (inicial == null) return;

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

    public void aplicarOp(String documentoId, String usuarioId, String rol, Object op) {
        DocumentoArchivo doc = docRepo.findById(documentoId).orElse(null);
        if (doc == null) return;

        if (!puedeEditar(doc, rol)) {
            broadcaster.presencia(documentoId, "kick",
                    Map.of("motivo", "permiso_revocado"), usuarioId);
            return;
        }

        String contenidoNuevo = contenidoDe(op);
        try {
            sesionRepo.findByDocumentoArchivoId(documentoId).ifPresent(base ->
                    guardarConReintento(documentoId, base, s -> {
                        s.getParticipantes().stream()
                                .filter(p -> usuarioId.equals(p.getUsuarioId()))
                                .findFirst()
                                .ifPresent(p -> p.setUltimoLatido(LocalDateTime.now()));
                        s.setUltimoLatido(LocalDateTime.now());
                        s.setCambiosPendientes(s.getCambiosPendientes() + 1);
                        if (contenidoNuevo != null) {
                            s.setContenido(contenidoNuevo);
                        }
                    }));
        } catch (OptimisticLockingFailureException conflicto) {
            log.debug("[CU-38] latido de op no persistido en doc={} por conflicto de versión", documentoId);
        }

        broadcaster.edicion(documentoId, "op", op, usuarioId);
    }

    public void actualizarCursor(String documentoId, String usuarioId, Object payload) {
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

    public int purgar(Duration ttl) {
        LocalDateTime corte = LocalDateTime.now().minus(ttl);
        int afectadas = 0;

        Map<String, SesionEdicionDocumento> candidatas = new LinkedHashMap<>();
        for (SesionEdicionDocumento s : sesionRepo.findByParticipantes_UltimoLatidoBefore(corte)) {
            candidatas.put(s.getId(), s);
        }
        for (SesionEdicionDocumento s : sesionRepo.findByUltimoLatidoBefore(corte)) {
            candidatas.putIfAbsent(s.getId(), s);
        }

        for (SesionEdicionDocumento s : candidatas.values()) {
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

    private static final int MAX_INTENTOS_CONCURRENCIA = 3;

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
                sesion = fresca;
            }
        }
        return sesion;
    }

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
                    return null;
                }
                if (intento == MAX_INTENTOS_CONCURRENCIA) {
                    log.warn("[CU-38] conflicto de versión persistente al salir doc={} tras {} intentos",
                            documentoId, MAX_INTENTOS_CONCURRENCIA);
                    throw conflicto;
                }
                sesion = fresca;
            }
        }
        return sesion;
    }

    private String contenidoDe(Object op) {
        if (op instanceof Map<?, ?> m
                && "replace".equals(String.valueOf(m.get("tipo")))
                && m.get("contenido") instanceof String s) {
            return s;
        }
        return null;
    }

    private boolean puedeEditar(DocumentoArchivo doc, String rol) {
        if (rol != null && rol.contains("ADMINISTRADOR")) return true;

        if (doc.getActividadId() == null || doc.getPoliticaId() == null) {
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

    public List<SesionEdicionDocumento.Participante> participantes(String documentoId) {
        return sesionRepo.findByDocumentoArchivoId(documentoId)
                .map(SesionEdicionDocumento::getParticipantes)
                .orElseGet(ArrayList::new);
    }
}
