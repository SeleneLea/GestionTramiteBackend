package com.example.demo.services;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.DecisionFinalRequest;
import com.example.demo.dto.DerivarTramiteRequest;
import com.example.demo.dto.DevolverTramiteRequest;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private NotificacionService notificacionService;

    public Tramite derivarTramite(String tramiteId, DerivarTramiteRequest request, String usuarioQueDeriva) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

        String funcionarioOriginal = tramite.getFuncionarioActualId();
        tramite.setFuncionarioActualId(request.getNuevoFuncionarioId());
        tramite = tramiteRepository.save(tramite);

        trazabilidadService.registrar(tramite.getId(), usuarioQueDeriva, "derivar",
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
                "Tramite derivado a tu bandeja",
                "El tramite " + tramite.getCodigo() + " ha sido derivado a tu responsabilidad. Motivo: "
                        + (request.getMotivo() != null ? request.getMotivo() : "no especificado"),
                "web"
        );

        return tramite;
    }

    public Tramite devolverTramite(String tramiteId, DevolverTramiteRequest request, String usuarioResponsable) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

        String estadoAnterior = tramite.getEstadoActual();
        String nodoAnteriorId = tramite.getNodoActualId();

        tramite.setNodoActualId(request.getNodoDestinoId());
        tramite.setEstadoActual("Observado");
        tramite.setFuncionarioActualId(null);
        tramite = tramiteRepository.save(tramite);

        EstadoHistorico historico = new EstadoHistorico();
        historico.setTramiteId(tramite.getId());
        historico.setEstadoAnterior(estadoAnterior);
        historico.setEstadoNuevo("Observado");
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

        return tramite;
    }

    public Tramite decisionFinal(String tramiteId, DecisionFinalRequest request, String usuarioResponsable) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

        boolean rechazar = "Rechazar".equalsIgnoreCase(request.getDecision());

        trazabilidadService.registrar(tramite.getId(), usuarioResponsable,
                rechazar ? "rechazar" : "aprobar",
                tramite.getNodoActualId(), Map.of(
                        "decision", request.getDecision(),
                        "justificacion", request.getJustificacion() != null ? request.getJustificacion() : ""
                ));

        if (rechazar) {
            tramite.setEstadoActual("Rechazado");
            tramite.setFuncionarioActualId(null);
            tramite.setNodoActualId(null);
            tramite.setFechaCierreReal(LocalDateTime.now());
            return tramiteRepository.save(tramite);
        }

        CompletarNodoRequest engineReq = new CompletarNodoRequest();
        engineReq.setDecision("si");
        engineReq.setFuncionarioId(usuarioResponsable);
        return workflowEngineService.completarNodo(tramite.getId(), engineReq);
    }
}
