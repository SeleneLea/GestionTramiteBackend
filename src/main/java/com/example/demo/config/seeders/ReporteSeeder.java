package com.example.demo.config.seeders;

import com.example.demo.models.Reporte;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
public class ReporteSeeder {

    @Autowired private ReporteRepository reporteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (reporteRepository.count() > 0) {
            log.info("[Seeder] Reportes ya existen, omitiendo");
            return;
        }

        String adminId = userId("admin@cre.bo");
        String superUserId = userId("superuser@cre.bo");

        LocalDateTime now = LocalDateTime.now();

        crear(adminId, "tramites_por_estado",
                Map.of("periodo", "2024-Q1", "incluir_cancelados", true),
                "PDF", "/reportes/tramites_estado_2024Q1.pdf",
                now.minusDays(5));

        crear(adminId, "metricas_departamento",
                Map.of("departamento", "TEC", "periodo", "2024-03", "incluir_sla", true),
                "EXCEL", "/reportes/metricas_tec_2024_03.xlsx",
                now.minusDays(3));

        crear(superUserId, "sla_cumplimiento",
                Map.of("periodo", "2024-Q1", "todos_departamentos", true),
                "PDF", "/reportes/sla_cumplimiento_2024Q1.pdf",
                now.minusDays(1));

        crear(adminId, "tramites_por_estado",
                Map.of("periodo", "2024-Q2", "fecha_inicio", "2024-04-01", "fecha_fin", "2024-04-25"),
                "CSV", "/reportes/tramites_estado_2024Q2_parcial.csv",
                now);

        log.info("[Seeder] Reportes OK (4 reportes de distintos tipos y formatos)");
    }

    private void crear(String generadoPorId, String tipo, Map<String, Object> filtros,
                       String formato, String urlArchivo, LocalDateTime fechaGeneracion) {
        Reporte r = new Reporte();
        r.setGeneradoPorId(generadoPorId);
        r.setTipo(tipo);
        r.setFiltros(filtros);
        r.setFormato(formato);
        r.setUrlArchivo(urlArchivo);
        r.setFechaGeneracion(fechaGeneracion);
        reporteRepository.save(r);
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
