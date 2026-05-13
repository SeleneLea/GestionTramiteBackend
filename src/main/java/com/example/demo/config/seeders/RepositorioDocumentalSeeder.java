package com.example.demo.config.seeders;

import com.example.demo.models.RepositorioDocumental;
import com.example.demo.repositories.RepositorioDocumentalRepository;
import com.example.demo.repositories.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Parte 2 — CU-32: contenedor del repositorio documental, asociado 1:1 a un
 * Tramite.
 *
 * Las políticas sembradas por {@code PoliticaSeeder} se insertan directo al
 * repo MongoDB, y los trámites sembrados por {@code TramiteSeeder} tampoco
 * pasan por el hook que crea el repositorio al iniciar un trámite. Resultado:
 * {@code GET /api/tramites/{id}/repositorio} devolvería 404 hasta que se sube
 * el primer documento.
 *
 * Este seeder cierra ese gap creando el contenedor (sin documentos, sin
 * archivos físicos en S3 — sólo la entrada en MongoDB) para cada trámite,
 * y enlaza {@code tramite.repositorioId} al id del repositorio. Idempotente:
 * si el repositorio ya existe (por tramiteId) lo deja como está, pero igual
 * asegura el enlace inverso en el trámite.
 */
@Component
@Slf4j
public class RepositorioDocumentalSeeder {

    @Autowired private RepositorioDocumentalRepository repoRepo;
    @Autowired private TramiteRepository tramiteRepo;

    public void seed() {
        var tramites = tramiteRepo.findAll();
        if (tramites.isEmpty()) {
            log.info("[Seeder] RepositorioDocumental omitido (sin trámites)");
            return;
        }

        int creados = 0;
        for (var tramite : tramites) {
            RepositorioDocumental repo = repoRepo.findByTramiteId(tramite.getId()).orElse(null);

            if (repo == null) {
                repo = new RepositorioDocumental();
                repo.setTramiteId(tramite.getId());
                repo.setPoliticaId(tramite.getPoliticaId());
                repo.setNombre("Repositorio - Tramite " + tramite.getId());
                repo.setBucketKey("tramites/" + tramite.getId() + "/");
                repo.setTotalArchivos(0);
                repo.setTotalBytes(0);
                repo.setActivo(true);
                repo.setFechaCreacion(LocalDateTime.now());
                repo = repoRepo.save(repo);
                creados++;
            }

            if (tramite.getRepositorioId() == null) {
                tramite.setRepositorioId(repo.getId());
                tramiteRepo.save(tramite);
            }
        }
        log.info("[Seeder] RepositorioDocumental OK ({} contenedores creados)", creados);
    }
}
