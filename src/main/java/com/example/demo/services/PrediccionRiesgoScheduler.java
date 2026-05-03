package com.example.demo.services;

import com.example.demo.dto.TramiteRiesgoResponse;
import com.example.demo.models.Notificacion;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.NotificacionRepository;
import com.example.demo.repositories.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-43 (job) — Recalcula el riesgo de demora de todos los trámites activos
 * periódicamente y notifica al funcionario+admin cuando un trámite pasa a
 * nivel "alto" (sin duplicar la notificación si ya estaba en alto).
 *
 * Si el microservicio IA está caído, el job degrada limpiamente: cada trámite
 * conserva su {@code riesgoDemora} anterior y se loguea un warning.
 *
 * El job se puede desactivar con {@code app.scheduler.riesgo.enabled=false}.
 */
@Service
@Slf4j
public class PrediccionRiesgoScheduler {

    @Autowired private PrediccionService prediccion;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private NotificacionService notificacionService;

    @Value("${app.scheduler.riesgo.enabled:true}")
    private boolean enabled;

    /**
     * Cada 15 min (cron: segundo, minuto, hora, día, mes, día-semana).
     * Configurable vía {@code app.scheduler.riesgo.cron}.
     */
    @Scheduled(cron = "${app.scheduler.riesgo.cron:0 */15 * * * *}")
    public void ejecutar() {
        if (!enabled) return;

        try {
            List<TramiteRiesgoResponse> resultados = prediccion.calcularRiesgoTramitesActivos(null);
            log.info("[CU-43 job] Procesados {} trámites", resultados.size());

            for (TramiteRiesgoResponse r : resultados) {
                if ("alto".equalsIgnoreCase(r.getNivel())) {
                    notificarRiesgoAltoSiCambio(r);
                }
            }
        } catch (ResponseStatusException ex) {
            // Microservicio IA caído — log y continuar
            log.warn("[CU-43 job] IA no disponible, omitiendo recálculo: {}", ex.getReason());
        } catch (Exception ex) {
            log.error("[CU-43 job] Error inesperado: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Notifica al funcionario actual + admin si y solo si el trámite pasó a
     * "alto" en esta ronda (transición). Si ya estaba en alto y no había
     * notificación abierta sin leer, también se notifica (cubre el caso de
     * arranque fresco con datos pre-existentes).
     */
    private void notificarRiesgoAltoSiCambio(TramiteRiesgoResponse r) {
        tramiteRepository.findById(r.getTramiteId()).ifPresent(t -> {
            // RN-RS02 — si ya hay notificación de riesgo abierta no se duplica.
            boolean yaNotificado = notificacionRepository
                    .findByDestinatarioIdOrderByFechaCreacionDesc(t.getFuncionarioActualId() != null
                            ? t.getFuncionarioActualId() : "")
                    .stream()
                    .filter(n -> !n.isLeida())
                    .anyMatch(n -> "riesgo_demora_alto".equals(n.getTipo())
                            && t.getId().equals(n.getTramiteId()));

            if (yaNotificado) return;

            String mensaje = "El trámite " + (t.getCodigo() != null ? t.getCodigo() : t.getId())
                    + " tiene alta probabilidad de superar el SLA"
                    + (r.getProbSuperarSla() != null
                            ? " (" + Math.round(r.getProbSuperarSla() * 100) + "%)" : "")
                    + ".";

            if (t.getFuncionarioActualId() != null) {
                notificacionService.crearNotificacion(
                        t.getFuncionarioActualId(),
                        t.getId(),
                        "riesgo_demora_alto",
                        "Trámite en riesgo de demora",
                        mensaje,
                        "web");
            }
        });
    }

    /** Solo para tests / disparo manual desde un endpoint admin si se quiere. */
    public int ejecutarManual() {
        if (!enabled) return 0;
        try {
            return prediccion.calcularRiesgoTramitesActivos(null).size();
        } catch (Exception ex) {
            log.error("[CU-43 manual] {}", ex.getMessage());
            return 0;
        }
    }
}
