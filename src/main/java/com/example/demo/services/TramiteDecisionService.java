package com.example.demo.services;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.DecisionFinalRequest;
import com.example.demo.dto.DerivarTramiteRequest;
import com.example.demo.dto.DevolverTramiteRequest;
import com.example.demo.models.DocumentoArchivo;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.EstadoSeccion;
import com.example.demo.models.EstadoTramite;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TramiteDecisionService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private WorkflowEngineService workflowEngineService;

    @Autowired
    private TrazabilidadService trazabilidadService;

    @Autowired
    private EstadoHistoricoRepository estadoHistoricoRepository;

    @Autowired
    private SeccionExpedienteRepository seccionRepository;

    @Autowired
    private PoliticaNegocioRepository politicaRepository;

    @Autowired
    private DocumentoArchivoService documentoArchivoService;

    @Autowired
    private NotificacionService notificacionService;

    /**
     * REASIGNAR (antes "derivar"): pasa el trámite a otro funcionario del MISMO
     * nodo, sin avanzar el flujo. No confundir con "Derivar" (= completar el nodo
     * y pasar al siguiente), que es {@code WorkflowEngineService.completarNodo}.
     */
    public Tramite reasignarTramite(String tramiteId, DerivarTramiteRequest request, String usuarioQueReasigna) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

        // BK3-04: no se puede reasignar un trámite ya cerrado (terminal).
        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El tramite ya esta cerrado");
        }

        String funcionarioOriginal = tramite.getFuncionarioActualId();
        tramite.setFuncionarioActualId(request.getNuevoFuncionarioId());
        tramite = tramiteRepository.save(tramite);

        // BK4-02: la reasignación debe reflejarse en la(s) sección(es) del nodo
        // activo; si no, el expediente/flujo sigue mostrando al funcionario
        // anterior hasta que el nuevo acepte. Lineal: la sección del nodoActual.
        // Paralelo: todas las secciones activas de las ramas en curso.
        if (tramite.getExpedienteId() != null) {
            LocalDateTime now = LocalDateTime.now();
            String nuevo = request.getNuevoFuncionarioId();
            if (tramite.estaEnParalelo()) {
                List<String> ramas = tramite.getNodosParalellosActivos();
                for (SeccionExpediente s : seccionRepository.findByExpedienteId(tramite.getExpedienteId())) {
                    if (EstadoSeccion.esActivaParaTrabajo(s.getEstado())
                            && ramas != null && ramas.contains(s.getNodoId())) {
                        s.setFuncionarioId(nuevo);
                        s.setFechaAsignacion(now);
                        seccionRepository.save(s);
                    }
                }
            } else if (tramite.getNodoActualId() != null) {
                final String nodoActual = tramite.getNodoActualId();
                seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId())
                        .stream()
                        .filter(s -> nodoActual.equals(s.getNodoId()))
                        .findFirst()
                        .ifPresent(s -> {
                            s.setFuncionarioId(nuevo);
                            s.setFechaAsignacion(now);
                            seccionRepository.save(s);
                        });
            }
        }

        trazabilidadService.registrar(tramite.getId(), usuarioQueReasigna, "reasignar",
                tramite.getNodoActualId(), Map.of(
                        "funcionarioAnterior", funcionarioOriginal != null ? funcionarioOriginal : "",
                        "funcionarioNuevo", request.getNuevoFuncionarioId(),
                        "motivo", request.getMotivo() != null ? request.getMotivo() : ""
                ));

        // CU-11: notificacion al nuevo funcionario receptor.
        notificacionService.crearNotificacion(
                request.getNuevoFuncionarioId(),
                tramite.getId(),
                "asignacion",
                "Tramite reasignado a tu bandeja",
                "El tramite " + tramite.getCodigo() + " ha sido reasignado a tu responsabilidad. Motivo: "
                        + (request.getMotivo() != null ? request.getMotivo() : "no especificado"),
                "web"
        );

        return tramite;
    }

    public Tramite devolverTramite(String tramiteId, DevolverTramiteRequest request, String usuarioResponsable) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

        // BK3-04: no se puede observar/devolver un trámite ya cerrado (terminal).
        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El tramite ya esta cerrado");
        }

        String estadoAnterior = tramite.getEstadoActual();
        String nodoAnteriorId = tramite.getNodoActualId();

        // BK3-03: recordar si venía en paralelo ANTES de vaciar la lista, para
        // luego cerrar las otras ramas activas al observar.
        boolean veniaEnParalelo = tramite.estaEnParalelo();

        tramite.setNodoActualId(request.getNodoDestinoId());
        tramite.setEstadoActual(EstadoTramite.OBSERVADO.getValor());
        tramite.setFuncionarioActualId(null);
        // WF-05: limpiar el paralelismo para que estaEnParalelo() sea false y
        // resolverNodoActivo() use el nodo de subsanación destino.
        tramite.setNodosParalellosActivos(new ArrayList<>());
        tramite = tramiteRepository.save(tramite);

        // Nivel nodo: la sección destino queda Observado (en espera de subsanación).
        if (tramite.getExpedienteId() != null && request.getNodoDestinoId() != null) {
            seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId())
                    .stream()
                    .filter(s -> request.getNodoDestinoId().equals(s.getNodoId()))
                    .findFirst()
                    .ifPresent(s -> {
                        s.setEstado(EstadoSeccion.OBSERVADO.getValor());
                        s.setFechaAsignacion(LocalDateTime.now());
                        seccionRepository.save(s);
                    });

            // BK3-03: si venía en paralelo, cerrar las OTRAS ramas activas
            // (Derivada) para que no queden abiertas tras la observación.
            if (veniaEnParalelo) {
                LocalDateTime now = LocalDateTime.now();
                for (SeccionExpediente s : seccionRepository.findByExpedienteId(tramite.getExpedienteId())) {
                    if (request.getNodoDestinoId().equals(s.getNodoId())) {
                        continue;
                    }
                    if (EstadoSeccion.esActivaParaTrabajo(s.getEstado())) {
                        s.setEstado(EstadoSeccion.DERIVADA.getValor());
                        s.setFechaCompletado(now);
                        seccionRepository.save(s);
                    }
                }
            }
        }

        EstadoHistorico historico = new EstadoHistorico();
        historico.setTramiteId(tramite.getId());
        historico.setEstadoAnterior(estadoAnterior);
        historico.setEstadoNuevo(EstadoTramite.OBSERVADO.getValor());
        historico.setNodoAnteriorId(nodoAnteriorId);
        historico.setNodoNuevoId(request.getNodoDestinoId());
        historico.setActorId(usuarioResponsable);
        historico.setMotivo(request.getObservaciones());
        historico.setFechaCambio(LocalDateTime.now());
        estadoHistoricoRepository.save(historico);

        trazabilidadService.registrar(tramite.getId(), usuarioResponsable, "observar",
                tramite.getNodoActualId(), Map.of(
                        "nodoDestino", request.getNodoDestinoId(),
                        "motivo", request.getObservaciones() != null ? request.getObservaciones() : ""
                ));

        // CU-17: avisar al cliente que debe corregir (sin reempezar el trámite).
        notificacionService.crearNotificacion(
                tramite.getClienteId(),
                tramite.getId(),
                "cambio_estado",
                "Tu trámite fue observado",
                "El trámite " + tramite.getCodigo() + " tiene observaciones que debes corregir: "
                        + (request.getObservaciones() != null ? request.getObservaciones() : "revisa el detalle"),
                "push"
        );

        return tramite;
    }

    /** Variante sin archivo (JSON): rechazo, o aprobación de políticas que no exigen resolución. */
    public Tramite decisionFinal(String tramiteId, DecisionFinalRequest request, String usuarioResponsable) {
        return decisionFinal(tramiteId, request, usuarioResponsable, null, null, null, null);
    }

    /**
     * Decisión final del responsable. Al APROBAR, si la política
     * {@code requiereDocumentoResolucion}, exige adjuntar el documento de
     * resolución; si llega un archivo (obligatorio u opcional) se sube al
     * repositorio y se enlaza en el trámite para que el cliente lo descargue.
     */
    public Tramite decisionFinal(String tramiteId, DecisionFinalRequest request, String usuarioResponsable,
                                 MultipartFile archivoResolucion, String rol, String ip, String userAgent) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

        // BK3-01: no se puede decidir sobre un trámite ya cerrado (terminal).
        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El tramite ya esta cerrado");
        }

        boolean rechazar = "Rechazar".equalsIgnoreCase(request.getDecision());

        if (rechazar) {
            String estadoAnterior = tramite.getEstadoActual();
            String nodoAnteriorId = tramite.getNodoActualId();

            trazabilidadService.registrar(tramite.getId(), usuarioResponsable,
                    "rechazar",
                    tramite.getNodoActualId(), Map.of(
                            "decision", request.getDecision(),
                            "justificacion", request.getJustificacion() != null ? request.getJustificacion() : ""
                    ));

            // WF-02 / BK3-02: cerrar TODAS las secciones activas del expediente
            // (no solo la del nodo actual) antes de nullificar el nodo, para que
            // el rechazo no deje ramas paralelas abiertas.
            String expedienteId = tramite.getExpedienteId();
            if (expedienteId != null) {
                LocalDateTime now = LocalDateTime.now();
                for (SeccionExpediente s : seccionRepository.findByExpedienteId(expedienteId)) {
                    if (EstadoSeccion.esActivaParaTrabajo(s.getEstado())) {
                        s.setEstado(EstadoSeccion.DERIVADA.getValor());
                        s.setFechaCompletado(now);
                        seccionRepository.save(s);
                    }
                }
            }

            tramite.setEstadoActual(EstadoTramite.RECHAZADO.getValor());
            tramite.setFuncionarioActualId(null);
            tramite.setNodoActualId(null);
            tramite.setNodosParalellosActivos(new ArrayList<>());
            tramite.setFechaCierreReal(LocalDateTime.now());
            Tramite guardado = tramiteRepository.save(tramite);

            // BK4-04: registrar el cambio en el historial (igual que devolver),
            // para que el rechazo no quede ausente del historial del trámite.
            EstadoHistorico historico = new EstadoHistorico();
            historico.setTramiteId(guardado.getId());
            historico.setEstadoAnterior(estadoAnterior);
            historico.setEstadoNuevo(EstadoTramite.RECHAZADO.getValor());
            historico.setNodoAnteriorId(nodoAnteriorId);
            historico.setNodoNuevoId(null);
            historico.setActorId(usuarioResponsable);
            historico.setMotivo(request.getJustificacion());
            historico.setFechaCambio(LocalDateTime.now());
            estadoHistoricoRepository.save(historico);

            return guardado;
        }

        // BK4-03: aprobar un trámite con ramas paralelas aún abiertas solo
        // completaría una rama y dejaría el trámite "En curso" sin aprobar
        // (nunca se alcanza el JOIN). Exigir cerrar el paralelo antes de aprobar.
        if (tramite.estaEnParalelo()) {
            throw new IllegalStateException(
                    "No se puede aprobar: el tramite tiene ramas paralelas pendientes de completar el JOIN");
        }

        // APROBAR → gestionar el documento de resolución entregable.
        PoliticaNegocio politica = politicaRepository.findById(tramite.getPoliticaId()).orElse(null);
        boolean requiere = politica != null && politica.isRequiereDocumentoResolucion();
        boolean hayArchivo = archivoResolucion != null && !archivoResolucion.isEmpty();

        // BK-A2: validar ANTES de registrar la traza para no dejar una traza
        // fantasma de "aprobar" si la aprobación aborta por validación.
        if (requiere && !hayArchivo) {
            throw new IllegalArgumentException(
                    "RESOLUCION_REQUERIDA: esta política exige adjuntar el documento de resolución al aprobar");
        }

        if (hayArchivo && (politica == null || politica.getRepositorioId() == null)) {
            throw new IllegalStateException(
                    "La política no tiene repositorio documental para almacenar la resolución");
        }

        // BK4-01: subir la resolución ANTES de registrar la traza "aprobar". Si
        // S3 falla, la excepción aborta aquí y no deja una traza fantasma de
        // aprobación sobre un trámite cuya resolución nunca llegó a almacenarse.
        if (hayArchivo) {
            DocumentoArchivo resolucion = documentoArchivoService.subirResolucion(
                    politica.getRepositorioId(),
                    tramite.getId(),
                    tramite.getNodoActualId(),
                    inferirTipoDocumento(archivoResolucion),
                    "Resolución del trámite " + tramite.getCodigo(),
                    archivoResolucion,
                    usuarioResponsable,
                    rol,
                    ip,
                    userAgent);
            tramite.setDocumentoResolucionId(resolucion.getId());
            tramite.setFechaResolucion(LocalDateTime.now());
            tramite.setTipoResolucion("Resolución");
            tramiteRepository.save(tramite);
        }

        trazabilidadService.registrar(tramite.getId(), usuarioResponsable,
                "aprobar",
                tramite.getNodoActualId(), Map.of(
                        "decision", request.getDecision(),
                        "justificacion", request.getJustificacion() != null ? request.getJustificacion() : ""
                ));

        // Avanzar el motor: completa el último nodo y cierra el trámite → Aprobado.
        CompletarNodoRequest engineReq = new CompletarNodoRequest();
        engineReq.setDecision("si");
        engineReq.setFuncionarioId(usuarioResponsable);
        return workflowEngineService.completarNodo(tramite.getId(), engineReq);
    }

    private String inferirTipoDocumento(MultipartFile archivo) {
        String ct = archivo.getContentType() != null ? archivo.getContentType().toLowerCase() : "";
        if (ct.contains("pdf")) return "PDF";
        if (ct.startsWith("image/")) return "IMAGEN";
        if (ct.contains("word") || ct.contains("msword") || ct.contains("wordprocessing")) return "WORD";
        if (ct.contains("excel") || ct.contains("spreadsheet")) return "EXCEL";
        return "OTRO";
    }
}
