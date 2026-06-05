package com.example.demo.services;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.DecisionFinalRequest;
import com.example.demo.dto.DerivarTramiteRequest;
import com.example.demo.dto.DevolverTramiteRequest;
import com.example.demo.models.DocumentoArchivo;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.EstadoSeccion;
import com.example.demo.models.EstadoTramite;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TramiteDecisionService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private NodoDiagramaRepository nodoRepository;

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
    private RepositorioDocumentalService repositorioDocumentalService;

    @Autowired
    private NotificacionService notificacionService;

    public Tramite reasignarTramite(String tramiteId, DerivarTramiteRequest request, String usuarioQueReasigna) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El tramite ya esta cerrado");
        }

        String funcionarioOriginal = tramite.getFuncionarioActualId();
        tramite.setFuncionarioActualId(request.getNuevoFuncionarioId());
        tramite = tramiteRepository.save(tramite);

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

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El tramite ya esta cerrado");
        }

        if (request.getDocumentosObservados() == null || request.getDocumentosObservados().isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe seleccionar al menos un documento a corregir para observar el tramite");
        }

        String estadoAnterior = tramite.getEstadoActual();
        String nodoAnteriorId = tramite.getNodoActualId();

        boolean veniaEnParalelo = tramite.estaEnParalelo();

        tramite.setNodoActualId(request.getNodoDestinoId());
        tramite.setEstadoActual(EstadoTramite.OBSERVADO.getValor());
        tramite.setFuncionarioActualId(null);
        tramite.setNodosParalellosActivos(new ArrayList<>());
        tramite = tramiteRepository.save(tramite);

        if (tramite.getExpedienteId() != null && request.getNodoDestinoId() != null) {
            seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId())
                    .stream()
                    .filter(s -> request.getNodoDestinoId().equals(s.getNodoId()))
                    .findFirst()
                    .ifPresent(s -> {
                        s.setEstado(EstadoSeccion.OBSERVADO.getValor());
                        s.setFechaAsignacion(LocalDateTime.now());
                        s.setDocumentosObservados(request.getDocumentosObservados() != null
                                ? new ArrayList<>(request.getDocumentosObservados())
                                : new ArrayList<>());
                        seccionRepository.save(s);
                    });

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

    public Tramite decisionFinal(String tramiteId, DecisionFinalRequest request, String usuarioResponsable) {
        return decisionFinal(tramiteId, request, usuarioResponsable, null, null, null, null);
    }

    public Tramite decisionFinal(String tramiteId, DecisionFinalRequest request, String usuarioResponsable,
                                 MultipartFile archivoResolucion, String rol, String ip, String userAgent) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

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

        if (tramite.estaEnParalelo()) {
            throw new IllegalStateException(
                    "No se puede aprobar: el tramite tiene ramas paralelas pendientes de completar el JOIN");
        }

        PoliticaNegocio politica = politicaRepository.findById(tramite.getPoliticaId()).orElse(null);
        boolean requiere = politica != null && politica.isRequiereDocumentoResolucion();
        boolean hayArchivo = archivoResolucion != null && !archivoResolucion.isEmpty();

        if (requiere && !hayArchivo) {
            throw new IllegalArgumentException(
                    "RESOLUCION_REQUERIDA: esta política exige adjuntar el documento de resolución al aprobar");
        }

        if (hayArchivo) {
            String repositorioId = repositorioDocumentalService
                    .crearAlIniciarTramite(tramite.getId(), tramite.getPoliticaId())
                    .getId();
            DocumentoArchivo resolucion = documentoArchivoService.subirResolucion(
                    repositorioId,
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

            notificarResolucionAlCliente(tramite, resolucion);
        }

        trazabilidadService.registrar(tramite.getId(), usuarioResponsable,
                "aprobar",
                tramite.getNodoActualId(), Map.of(
                        "decision", request.getDecision(),
                        "justificacion", request.getJustificacion() != null ? request.getJustificacion() : ""
                ));

        CompletarNodoRequest engineReq = new CompletarNodoRequest();
        engineReq.setDecision("si");
        engineReq.setFuncionarioId(usuarioResponsable);
        return workflowEngineService.completarNodo(tramite.getId(), engineReq);
    }

    private void notificarResolucionAlCliente(Tramite tramite, DocumentoArchivo resolucion) {
        try {
            if (tramite.getClienteId() == null) return;

            String actividad = nodoRepository.findById(tramite.getNodoActualId())
                    .map(NodoDiagrama::getNombre)
                    .filter(n -> n != null && !n.isBlank())
                    .orElse("la actividad final");

            String mensaje = "En \"" + actividad + "\" te enviaron el documento \""
                    + resolucion.getNombreLogico() + "\". Entra al repositorio del trámite "
                    + tramite.getCodigo() + " para verlo.";

            notificacionService.crearNotificacion(
                    tramite.getClienteId(),
                    tramite.getId(),
                    "documento",
                    "Documento de resolución disponible",
                    mensaje,
                    "push");
        } catch (RuntimeException ex) {
            log.warn("Aviso de resolución al cliente falló (tramite {}): {}",
                    tramite.getId(), ex.getMessage());
        }
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
