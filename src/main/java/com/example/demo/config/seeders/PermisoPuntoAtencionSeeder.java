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

/**
 * Parte 2 — CU-36: permisos documentales por punto de atención.
 *
 * Para evitar que los funcionarios choquen contra DOC_PERMISO_DENEGADO en la
 * demo (regla RN-P01: SOLO_LECTURA por default), seedeamos un permiso amplio
 * (LECTURA_Y_EDICION) por cada par (política activa × actividad reutilizable).
 *
 * En producción, el administrador refina esto desde la UI; este seeder solo
 * garantiza un estado inicial usable. Idempotente: si ya existe un permiso
 * para el par (politicaId, actividadId), lo deja como está.
 */
@Component
@Slf4j
public class PermisoPuntoAtencionSeeder {

    @Autowired private PermisoPuntoAtencionRepository permisoRepo;
    @Autowired private PoliticaNegocioRepository politicaRepo;
    @Autowired private ActividadRepository actividadRepo;

    /**
     * Sin restricción de TIPOS por defecto (lista vacía = todos visibles según
     * la regla del CU-36). Sembrar una lista poblada activaba el filtro por tipo
     * en toda la demo y ocultaba al funcionario los documentos del cliente
     * (cuyo tipoDocumento es el nombre libre del requisito). El admin la
     * configura por punto desde la UI cuando de verdad quiera restringir.
     */
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
