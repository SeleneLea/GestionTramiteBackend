package com.example.demo.config;

import com.example.demo.config.seeders.MasterSeeder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada del seed automatico al iniciar la aplicacion.
 *
 * Comportamiento:
 *  - app.seed.reset = true  → DROPEA todas las colecciones y re-siembra desde cero.
 *  - app.seed.reset = false → solo siembra si faltan datos (idempotente).
 *
 * @DependsOn("mongoIndexConfig"): la migracion de indices (drop del indice unico
 * viejo politicaId de repositorios_documentales) DEBE correr ANTES de sembrar,
 * o el seeder de repositorios-por-tramite choca con E11000 al crear 2 repos de
 * la misma politica. El orden de @PostConstruct entre beans no esta garantizado
 * sin esta dependencia explicita.
 */
@Component
@Slf4j
@DependsOn("mongoIndexConfig")
public class DataSeeder {

    @Autowired private MasterSeeder masterSeeder;
    @Autowired private MongoTemplate mongoTemplate;

    @Value("${app.seed.reset:false}")
    private boolean reset;

    @PostConstruct
    public void seed() {
        if (reset) {
            log.warn("========================================");
            log.warn("  app.seed.reset=true → DROP DB COMPLETO");
            log.warn("========================================");
            mongoTemplate.getCollectionNames().forEach(name -> {
                mongoTemplate.getCollection(name).drop();
                log.info("  ↳ Coleccion '{}' eliminada", name);
            });
        }
        masterSeeder.seedAll();
    }
}
