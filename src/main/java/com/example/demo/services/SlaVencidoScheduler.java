package com.example.demo.services;

import com.example.demo.models.Actividad;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SlaVencidoScheduler {

    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private SeccionExpedienteRepository seccionRepository;
    @Autowired private NotificacionService notificacionService;

    @Value("${app.scheduler.sla.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.scheduler.sla.cron:0 */10 * * * *}")
    public void ejecutar() {
        if (!enabled) return;
        int alertas = 0;
        for (Tramite t : tramiteRepository.findAll()) {
            if (t.getFechaCierreReal() != null) continue;
            try {
                alertas += revisarTramite(t);
            } catch (Exception ex) {
                log.warn("[SLA job] Error revisando tramite {}: {}", t.getId(), ex.getMessage());
            }
        }
        if (alertas > 0) {
            log.info("[SLA job] {} alerta(s) de SLA vencido emitida(s)", alertas);
        }
    }

    private int revisarTramite(Tramite t) {
        List<String> nodosActivos = new ArrayList<>();
        if (t.estaEnParalelo()) {
            nodosActivos.addAll(t.getNodosParalellosActivos());
        } else if (t.getNodoActualId() != null) {
            nodosActivos.add(t.getNodoActualId());
        }

        int emitidas = 0;
        for (String nodoId : nodosActivos) {
            String avisados = t.getSlaVencidoNotificadoNodoId();
            if (avisados != null && ("," + avisados + ",").contains("," + nodoId + ",")) continue;

            NodoDiagrama nodo = nodoRepository.findById(nodoId).orElse(null);
            if (nodo == null || nodo.getActividadId() == null) continue;
            Actividad act = actividadRepository.findById(nodo.getActividadId()).orElse(null);
            if (act == null || act.getSlaHoras() <= 0) continue;

            SeccionExpediente seccion = seccionDelNodo(t, nodoId);
            LocalDateTime desde = seccion != null && seccion.getFechaAsignacion() != null
                    ? seccion.getFechaAsignacion()
                    : t.getFechaInicio();
            if (desde == null) continue;

            long horas = Duration.between(desde, LocalDateTime.now()).toHours();
            if (horas <= act.getSlaHoras()) continue;

            String destinatario = seccion != null && seccion.getFuncionarioId() != null
                    ? seccion.getFuncionarioId()
                    : t.getFuncionarioActualId();
            if (destinatario == null) continue;

            String codigo = t.getCodigo() != null ? t.getCodigo() : t.getId();
            notificacionService.crearNotificacion(
                    destinatario,
                    t.getId(),
                    "sla_vencido",
                    "Tramite con SLA vencido",
                    "El tramite " + codigo + " lleva " + horas + " h en \""
                            + act.getNombre() + "\" y supero su tiempo limite de "
                            + act.getSlaHoras() + " h.",
                    "web");

            t.setSlaVencidoNotificadoNodoId(
                    avisados == null || avisados.isBlank() ? nodoId : avisados + "," + nodoId);
            tramiteRepository.save(t);
            emitidas++;
        }
        return emitidas;
    }

    private SeccionExpediente seccionDelNodo(Tramite t, String nodoId) {
        if (t.getExpedienteId() == null) return null;
        return seccionRepository.findByExpedienteId(t.getExpedienteId()).stream()
                .filter(s -> nodoId.equals(s.getNodoId()))
                .findFirst()
                .orElse(null);
    }
}
