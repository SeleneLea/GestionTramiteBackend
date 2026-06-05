package com.example.demo.config.seeders;

import com.example.demo.models.PermisoPuntoAtencion;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.PermisoPuntoAtencionRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class PermisoPuntoAtencionSeeder {

    @Autowired private PermisoPuntoAtencionRepository permisoRepo;
    @Autowired private PoliticaNegocioRepository politicaRepo;
    @Autowired private ActividadRepository actividadRepo;

    private static final List<String> TIPOS_VISIBLES_DEFAULT = List.of();

    public void seed() {
        var politicasActivas = politicaRepo.findAll().stream()
                .filter(p -> "activa".equalsIgnoreCase(p.getEstado()))
                .toList();
        var actividades = actividadRepo.findAll();

        if (politicasActivas.isEmpty() || actividades.isEmpty()) {
            log.info("[Seeder] PermisoPuntoAtencion omitido (sin políticas activas o actividades)");
            return;
        }

        int creados = 0;
        for (var politica : politicasActivas) {
            for (var actividad : actividades) {
                var existente = permisoRepo.findByPoliticaIdAndActividadId(
                        politica.getId(), actividad.getId());
                if (existente.isPresent()) continue;

                PermisoPuntoAtencion p = new PermisoPuntoAtencion();
                p.setPoliticaId(politica.getId());
                p.setActividadId(actividad.getId());
                p.setNivelAcceso("LECTURA_Y_EDICION");
                p.setTiposDocumentoVisibles(TIPOS_VISIBLES_DEFAULT);
                p.setActualizadoPorId("seeder");
                p.setFechaActualizacion(LocalDateTime.now());
                permisoRepo.save(p);
                creados++;
            }
        }
        log.info("[Seeder] PermisoPuntoAtencion OK ({} creados)", creados);
    }
}
