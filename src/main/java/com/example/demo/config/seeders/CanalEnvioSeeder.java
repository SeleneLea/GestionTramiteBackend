package com.example.demo.config.seeders;

import com.example.demo.models.CanalEnvio;
import com.example.demo.repositories.CanalEnvioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class CanalEnvioSeeder {

    @Autowired private CanalEnvioRepository canalEnvioRepository;

    public void seed() {
        crear("EMAIL", Map.of(
                "host", "smtp.cre.bo",
                "port", 587,
                "from", "no-reply@cre.bo",
                "tls", true
        ), true);

        crear("SMS", Map.of(
                "provider", "twilio",
                "from", "+59170000000",
                "region", "BO"
        ), true);

        crear("PUSH", Map.of(
                "provider", "firebase",
                "project_id", "tramites-cre",
                "vapid_key", "VAPID_KEY_PLACEHOLDER"
        ), true);

        crear("IN_APP", Map.of(
                "storage", "mongodb",
                "retention_days", 30
        ), true);

        log.info("[Seeder] Canales de envio OK");
    }

    private void crear(String tipo, Map<String, Object> config, boolean activo) {
        if (!canalEnvioRepository.existsByTipo(tipo)) {
            CanalEnvio c = new CanalEnvio();
            c.setTipo(tipo);
            c.setConfiguracion(config);
            c.setActivo(activo);
            canalEnvioRepository.save(c);
        }
    }
}
