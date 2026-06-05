package com.example.demo.config.seeders;

import com.example.demo.models.RepositorioDocumental;
import com.example.demo.repositories.RepositorioDocumentalRepository;
import com.example.demo.repositories.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
