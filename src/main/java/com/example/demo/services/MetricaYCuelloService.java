package com.example.demo.services;

import com.example.demo.models.Actividad;
import com.example.demo.models.CuelloBotella;
import com.example.demo.models.MetricaTiempo;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.CuelloBotellaRepository;
import com.example.demo.repositories.MetricaTiempoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MetricaYCuelloService {

    @Autowired
    private MetricaTiempoRepository metricaRepo;

    @Autowired
    private CuelloBotellaRepository cuelloRepo;

    @Autowired
    private ActividadRepository actividadRepository;

    public void registrarMetricaActividad(String tramiteId, String actividadId, String departamentoId,
                                          LocalDateTime inicio, LocalDateTime fin) {
        Actividad act = actividadRepository.findById(actividadId).orElse(null);
        if (act == null || inicio == null || fin == null) {
            return;
        }

        long segundos = Duration.between(inicio, fin).getSeconds();
        int slaSegundos = act.getSlaHoras() * 3600;

        MetricaTiempo m = new MetricaTiempo();
        m.setTramiteId(tramiteId);
        m.setActividadId(actividadId);
        m.setDepartamentoId(departamentoId);
        m.setFechaInicioActividad(inicio);
        m.setFechaFinActividad(fin);
        m.setTiempoSegundos(segundos);
        m.setSuperoSla(segundos > slaSegundos);

        metricaRepo.save(m);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void analizarCuellosDeBotella() {
        List<Actividad> actividades = actividadRepository.findAll();

        for (Actividad act : actividades) {
            List<MetricaTiempo> metricas = metricaRepo.findByActividadIdOrderByFechaFinActividadDesc(act.getId());
            if (metricas.size() < 5) {
                continue;
            }

            double promedio = metricas.stream().mapToLong(MetricaTiempo::getTiempoSegundos).average().orElse(0.0);
            double slaSegundos = act.getSlaHoras() * 3600.0;

            if (promedio > slaSegundos) {
                CuelloBotella cb = new CuelloBotella();
                cb.setActividadId(act.getId());
                cb.setDepartamentoId(act.getDepartamentoId());
                cb.setPeriodo("dia");
                cb.setTramitesAcumulados(metricas.size());
                cb.setTiempoPromedio((float) promedio);
                cb.setTiempoEsperado((float) slaSegundos);
                cb.setDesviacionPorcentaje((float) (((promedio - slaSegundos) / slaSegundos) * 100));
                cb.setCausaSugerida("El promedio supera el SLA. Posible falta de personal o procesos ineficientes.");
                cb.setFechaDeteccion(LocalDateTime.now());

                cuelloRepo.save(cb);
            }
        }
    }
}
