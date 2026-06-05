package com.example.demo.config.seeders;

import com.example.demo.models.Actividad;
import com.example.demo.models.CuelloBotella;
import com.example.demo.models.MetricaTiempo;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class MetricaSeeder {

    @Autowired private MetricaTiempoRepository metricaRepository;
    @Autowired private CuelloBotellaRepository cuelloBotellaRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DepartamentoRepository departamentoRepository;

    public void seed() {
        if (metricaRepository.count() > 0) {
            log.info("[Seeder] Metricas ya existen, omitiendo");
            return;
        }

        List<Tramite> tramites    = tramiteRepository.findAll();
        List<Actividad> actividades = actividadRepository.findAll();

        String trm001 = tramiteId(tramites, "TRM-2024-001");
        String trm002 = tramiteId(tramites, "TRM-2024-002");
        String trm005 = tramiteId(tramites, "TRM-2024-005");

        String actVerDocs  = actId(actividades, "Verificación de documentos del cliente");
        String actInsp     = actId(actividades, "Inspección técnica en campo");
        String actPresup   = actId(actividades, "Elaboración de presupuesto técnico");
        String actContrato = actId(actividades, "Revisión y aprobación del contrato");
        String actCierre   = actId(actividades, "Ejecución de trabajos técnicos");

        String atcId = deptoId("ATC");
        String tecId = deptoId("TEC");
        String legId = deptoId("LEG");
        String opeId = deptoId("OPE");

        LocalDateTime now = LocalDateTime.now();

        if (trm001 != null) {
            metrica(trm001, actVerDocs,  atcId, 6 * 3600L, false,
                    now.minusDays(30), now.minusDays(30).plusHours(6));
            metrica(trm001, actInsp,     tecId, 4 * 24 * 3600L, false,
                    now.minusDays(29), now.minusDays(25));
            metrica(trm001, actPresup,   tecId, 7 * 24 * 3600L, false,
                    now.minusDays(29), now.minusDays(22));
            metrica(trm001, actContrato, legId, 8 * 24 * 3600L, false,
                    now.minusDays(21), now.minusDays(13));
            metrica(trm001, actCierre,   opeId, 5 * 24 * 3600L, false,
                    now.minusDays(10), now.minusDays(5));
        }

        if (trm002 != null) {
            metrica(trm002, actVerDocs,  atcId, 1 * 3600L,   false,
                    now.minusDays(25), now.minusDays(25).plusHours(1));
            metrica(trm002, actContrato, legId, 12 * 24 * 3600L, true,
                    now.minusDays(22), now.minusDays(10));
        }

        if (trm005 != null) {
            metrica(trm005, actVerDocs,  atcId, 5 * 3600L,    false,
                    now.minusDays(15), now.minusDays(15).plusHours(5));
            metrica(trm005, actInsp,     tecId, 4 * 24 * 3600L, false,
                    now.minusDays(13), now.minusDays(9));

            metrica(trm005, actContrato, legId, 7 * 24 * 3600L,
                    now.minusDays(7));
        }

        cuelloBotella(actContrato, legId, "2024-Q1",
                8, 14.5f, 24f, 39.6f,
                "Acumulacion en revision legal por falta de personal y alta demanda estacional",
                now.minusDays(10));

        cuelloBotella(actInsp, tecId, "2024-Q1",
                5, 5.2f, 16f, 67.5f,
                "Retrasos en inspeccion por distancia geografica de algunos inmuebles",
                now.minusDays(15));

        log.info("[Seeder] Metricas y cuellos de botella OK");
    }

    private void metrica(String tramiteId, String actividadId, String departamentoId,
                         long tiempoSegundos, boolean superoSla,
                         LocalDateTime inicio, LocalDateTime fin) {
        MetricaTiempo m = new MetricaTiempo();
        m.setTramiteId(tramiteId);
        m.setActividadId(actividadId);
        m.setDepartamentoId(departamentoId);
        m.setTiempoSegundos(tiempoSegundos);
        m.setSuperoSla(superoSla);
        m.setFechaInicioActividad(inicio);
        m.setFechaFinActividad(fin);
        metricaRepository.save(m);
    }

    private void metrica(String tramiteId, String actividadId, String departamentoId,
                         long tiempoSegundos, LocalDateTime inicio) {
        metrica(tramiteId, actividadId, departamentoId, tiempoSegundos, false, inicio, null);
    }

    private void cuelloBotella(String actividadId, String departamentoId, String periodo,
                                int tramitesAcumulados, float tiempoPromedio, float tiempoEsperado,
                                float desviacion, String causaSugerida, LocalDateTime deteccion) {
        CuelloBotella cb = new CuelloBotella();
        cb.setActividadId(actividadId);
        cb.setDepartamentoId(departamentoId);
        cb.setPeriodo(periodo);
        cb.setTramitesAcumulados(tramitesAcumulados);
        cb.setTiempoPromedio(tiempoPromedio);
        cb.setTiempoEsperado(tiempoEsperado);
        cb.setDesviacionPorcentaje(desviacion);
        cb.setCausaSugerida(causaSugerida);
        cb.setFechaDeteccion(deteccion);
        cuelloBotellaRepository.save(cb);
    }

    private String tramiteId(List<Tramite> tramites, String codigo) {
        return tramites.stream().filter(t -> codigo.equals(t.getCodigo()))
                .findFirst().map(Tramite::getId).orElse(null);
    }

    private String actId(List<Actividad> acts, String nombre) {
        String id = acts.stream().filter(a -> nombre.equals(a.getNombre()))
                .findFirst().map(Actividad::getId).orElse(null);
        if (id == null) {
            log.warn("[Seeder] Actividad no encontrada: '{}' -> metricas/cuellos quedaran con actividadId=null", nombre);
        }
        return id;
    }

    private String deptoId(String codigo) {
        return departamentoRepository.findByCodigo(codigo).map(d -> d.getId()).orElse(null);
    }
}
