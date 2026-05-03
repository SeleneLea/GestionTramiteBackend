package com.example.demo.services;

import com.example.demo.models.AlertaAnomalia;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * CU-45 (job) — Diariamente analiza las secuencias de todos los trámites y
 * persiste las anomalías detectadas. Notifica al administrador con un
 * resumen cuando se detectan nuevas.
 *
 * Se puede desactivar con {@code app.scheduler.anomalias.enabled=false}.
 */
@Service
@Slf4j
public class DeteccionAnomaliasScheduler {

    @Autowired private AlertaAnomaliaService anomaliaService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NotificacionService notificacionService;

    @Value("${app.scheduler.anomalias.enabled:true}")
    private boolean enabled;

    /** Cron por defecto: 03:00 todos los días. */
    @Scheduled(cron = "${app.scheduler.anomalias.cron:0 0 3 * * *}")
    public void ejecutar() {
        if (!enabled) return;

        try {
            List<AlertaAnomalia> creadas = anomaliaService.detectarYPersistir();
            log.info("[CU-45 job] {} anomalías detectadas", creadas.size());

            if (creadas.isEmpty()) return;

            // Resumen para administradores
            String resumen = "Se detectaron " + creadas.size()
                    + " anomalías nuevas en trámites. Revisa el panel de anomalías.";
            for (Usuario admin : usuariosAdmin()) {
                notificacionService.crearNotificacion(
                        admin.getId(),
                        null,
                        "anomalia_detectada",
                        "Anomalías detectadas por IA",
                        resumen,
                        "web");
            }
        } catch (ResponseStatusException ex) {
            log.warn("[CU-45 job] IA no disponible: {}", ex.getReason());
        } catch (Exception ex) {
            log.error("[CU-45 job] Error inesperado: {}", ex.getMessage(), ex);
        }
    }

    private List<Usuario> usuariosAdmin() {
        // Los seed crean tipo="administrador" (en minúscula). Aceptamos ambas variantes.
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getTipo() != null
                        && ("administrador".equalsIgnoreCase(u.getTipo())
                                || "superuser".equalsIgnoreCase(u.getTipo())))
                .filter(Usuario::isActivo)
                .toList();
    }
}
