package com.example.demo.config.seeders;

import com.example.demo.models.RepositorioDocumental;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.RepositorioDocumentalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Parte 2 — CU-32: contenedor del repositorio documental.
 *
 * El hook automático en {@code PoliticaNegocioService.crear()} crea el
 * repositorio al guardar una política nueva, pero las políticas sembradas
 * por {@code PoliticaSeeder} se insertan directo al repo MongoDB — nunca
 * pasan por ese hook. Resultado: {@code GET /api/politicas/{id}/repositorio}
 * devuelve 404 hasta que el admin pulsa el botón "Crear repositorio ahora".
 *
 * Este seeder cierra ese gap creando el contenedor (sin documentos, sin
 * archivos físicos en S3 — sólo la entrada en MongoDB) para cada política
 * activa. Idempotente: si el repositorio ya existe, lo deja como está.
 */
@Component
@Slf4j
public class RepositorioDocumentalSeeder {

    @Autowired private RepositorioDocumentalRepository repoRepo;
    @Autowired private PoliticaNegocioRepository politicaRepo;

    public void seed() {
        var politicas = politicaRepo.findAll();
        if (politicas.isEmpty()) {
            log.info("[Seeder] RepositorioDocumental omitido (sin políticas)");
            return;
        }

        int creados = 0;
        for (var politica : politicas) {
            if (repoRepo.findByPoliticaId(politica.getId()).isPresent()) continue;

            RepositorioDocumental r = new RepositorioDocumental();
            r.setPoliticaId(politica.getId());
            r.setNombre("Repositorio - " + politica.getNombre());
            r.setBucketKey("politicas/" + politica.getId() + "/");
            r.setTotalArchivos(0);
            r.setTotalBytes(0);
            r.setActivo(true);
            r.setFechaCreacion(LocalDateTime.now());
            repoRepo.save(r);
            creados++;
        }
        log.info("[Seeder] RepositorioDocumental OK ({} contenedores creados)", creados);
    }
}
