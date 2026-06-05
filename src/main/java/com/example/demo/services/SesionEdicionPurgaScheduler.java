package com.example.demo.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class SesionEdicionPurgaScheduler {

    @Autowired
    private EdicionColaborativaService service;

    @Value("${app.documental.edicion-colaborativa.ttl-sesion-minutos:5}")
    private int ttlMinutos;

    @Value("${app.scheduler.sesion-edicion-purga.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.scheduler.sesion-edicion-purga.cron:0 * * * * *}")
    public void ejecutar() {
        if (!enabled) return;
        try {
            int afectadas = service.purgar(Duration.ofMinutes(ttlMinutos));
            if (afectadas > 0) {
                log.info("[CU-38 purga] {} sesiones/participantes purgados (ttl={}m)",
                        afectadas, ttlMinutos);
            }
        } catch (Exception ex) {
            log.error("[CU-38 purga] Error: {}", ex.getMessage(), ex);
        }
    }
}
