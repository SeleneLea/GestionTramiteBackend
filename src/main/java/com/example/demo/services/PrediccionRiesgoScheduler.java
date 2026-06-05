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

@Service
@Slf4j
public class PrediccionRiesgoScheduler {

    @Autowired private PrediccionService prediccion;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private NotificacionService notificacionService;

    @Value("${app.scheduler.riesgo.enabled:true}")
    private boolean enabled;

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
            log.warn("[CU-43 job] IA no disponible, omitiendo recálculo: {}", ex.getReason());
        } catch (Exception ex) {
            log.error("[CU-43 job] Error inesperado: {}", ex.getMessage(), ex);
        }
    }

    private void notificarRiesgoAltoSiCambio(TramiteRiesgoResponse r) {
        tramiteRepository.findById(r.getTramiteId()).ifPresent(t -> {
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
