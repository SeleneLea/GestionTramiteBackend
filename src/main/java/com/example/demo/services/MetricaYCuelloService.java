package com.example.demo.services;

import com.example.demo.models.Actividad;
import com.example.demo.models.CuelloBotella;
import com.example.demo.models.Departamento;
import com.example.demo.models.MetricaTiempo;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.CuelloBotellaRepository;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.MetricaTiempoRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricaYCuelloService {

    @Autowired
    private MetricaTiempoRepository metricaRepo;

    @Autowired
    private CuelloBotellaRepository cuelloRepo;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private PoliticaNegocioRepository politicaRepository;

    @Autowired
    private NodoDiagramaRepository nodoRepository;

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
        m.setSuperoSla(slaSegundos > 0 && segundos > slaSegundos);

        metricaRepo.save(m);
    }

    /**
     * P1 §7 — Resumen del dashboard de monitoreo en tiempo real: conteo de
     * trámites por estado (+activos/cerrados), tiempo promedio de atención por
     * departamento y por política, y ranking de carga (trámites activos) por
     * departamento. Calcula sobre el estado vivo de la BD en cada llamada.
     */
    public Map<String, Object> resumenDashboard() {
        List<Tramite> tramites = tramiteRepository.findAll();
        List<MetricaTiempo> metricas = metricaRepo.findAll();

        Map<String, String> nombreDepto = new HashMap<>();
        for (Departamento d : departamentoRepository.findAll()) {
            nombreDepto.put(d.getId(), d.getNombre() != null ? d.getNombre() : d.getId());
        }
        Map<String, String> nombrePolitica = new HashMap<>();
        for (PoliticaNegocio p : politicaRepository.findAll()) {
            nombrePolitica.put(p.getId(), p.getNombre() != null ? p.getNombre() : p.getId());
        }

        // ── Conteo por estado + totales ──
        // Estado canónico (EstadoTramite.from normaliza literales legacy) y
        // "activo" según el modelo de estados, no según fechaCierreReal: los
        // cancelados/cerrados sin fecha contarían como activos para siempre.
        Map<String, Integer> porEstado = new LinkedHashMap<>();
        int activos = 0;
        for (Tramite t : tramites) {
            String estado = t.getEstadoActual() != null
                    ? com.example.demo.models.EstadoTramite.from(t.getEstadoActual()).getValor()
                    : "Sin estado";
            porEstado.merge(estado, 1, Integer::sum);
            if (!com.example.demo.models.EstadoTramite.esFinalizado(t.getEstadoActual())) activos++;
        }

        // ── Tiempo promedio (horas) por departamento y por política ──
        Map<String, List<Long>> segPorDepto = new HashMap<>();
        Map<String, List<Long>> segPorPolitica = new HashMap<>();
        Map<String, String> politicaDeTramite = new HashMap<>();
        for (Tramite t : tramites) {
            if (t.getPoliticaId() != null) politicaDeTramite.put(t.getId(), t.getPoliticaId());
        }
        for (MetricaTiempo m : metricas) {
            // Solo actividades COMPLETADAS (con fin): una métrica "en curso"
            // inflaría el promedio de tiempo de atención.
            if (m.getFechaFinActividad() == null) continue;
            if (m.getDepartamentoId() != null) {
                segPorDepto.computeIfAbsent(m.getDepartamentoId(), k -> new ArrayList<>())
                        .add(m.getTiempoSegundos());
            }
            String politicaId = politicaDeTramite.get(m.getTramiteId());
            if (politicaId != null) {
                segPorPolitica.computeIfAbsent(politicaId, k -> new ArrayList<>())
                        .add(m.getTiempoSegundos());
            }
        }

        // ── Carga (trámites activos) por departamento, vía su(s) nodo(s) activo(s).
        //    Deduplicado por trámite: dos ramas paralelas en el mismo depto cuentan
        //    como UN trámite (la tarjeta promete "trámites activos", no tareas). ──
        Map<String, Integer> cargaPorDepto = new HashMap<>();
        for (Tramite t : tramites) {
            if (com.example.demo.models.EstadoTramite.esFinalizado(t.getEstadoActual())) continue;
            List<String> nodos = t.estaEnParalelo()
                    ? t.getNodosParalellosActivos()
                    : (t.getNodoActualId() != null ? List.of(t.getNodoActualId()) : List.of());
            java.util.Set<String> deptosDelTramite = new java.util.LinkedHashSet<>();
            for (String nodoId : nodos) {
                nodoRepository.findById(nodoId)
                        .map(NodoDiagrama::getDepartamentoId)
                        .filter(d -> d != null)
                        .ifPresent(deptosDelTramite::add);
            }
            for (String d : deptosDelTramite) {
                cargaPorDepto.merge(d, 1, Integer::sum);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalTramites", tramites.size());
        out.put("activos", activos);
        out.put("cerrados", tramites.size() - activos);
        out.put("porEstado", listaConteo(porEstado, null));
        out.put("promedioPorDepartamento", listaPromedios(segPorDepto, nombreDepto));
        out.put("promedioPorPolitica", listaPromedios(segPorPolitica, nombrePolitica));
        out.put("cargaPorDepartamento", listaConteo(cargaPorDepto, nombreDepto));
        return out;
    }

    /** [{nombre, total}] ordenado desc; resuelve nombres si se da el mapa. */
    private List<Map<String, Object>> listaConteo(Map<String, Integer> conteo,
                                                  Map<String, String> nombres) {
        List<Map<String, Object>> out = new ArrayList<>();
        conteo.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nombre", nombres != null
                            ? nombres.getOrDefault(e.getKey(), e.getKey()) : e.getKey());
                    row.put("total", e.getValue());
                    out.add(row);
                });
        return out;
    }

    /** [{nombre, promedioHoras, muestras}] ordenado por promedio desc. */
    private List<Map<String, Object>> listaPromedios(Map<String, List<Long>> segundos,
                                                     Map<String, String> nombres) {
        List<Map<String, Object>> out = new ArrayList<>();
        segundos.forEach((id, lista) -> {
            double prom = lista.stream().mapToLong(Long::longValue).average().orElse(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nombre", nombres.getOrDefault(id, id));
            row.put("promedioHoras", Math.round(prom / 3600.0 * 10.0) / 10.0);
            row.put("muestras", lista.size());
            out.add(row);
        });
        out.sort((a, b) -> Double.compare(
                ((Number) b.get("promedioHoras")).doubleValue(),
                ((Number) a.get("promedioHoras")).doubleValue()));
        return out;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void analizarCuellosDeBotella() {
        List<Actividad> actividades = actividadRepository.findAll();

        for (Actividad act : actividades) {
            List<MetricaTiempo> metricas = metricaRepo.findByActividadIdOrderByFechaFinActividadDesc(act.getId());
            if (metricas.size() < 5) {
                continue;
            }
            if (act.getSlaHoras() <= 0) {
                continue;
            }

            double promedio = metricas.stream().mapToLong(MetricaTiempo::getTiempoSegundos).average().orElse(0.0);
            double slaSegundos = act.getSlaHoras() * 3600.0;

            if (promedio > slaSegundos) {
                String periodo = LocalDate.now().toString();
                CuelloBotella cb = cuelloRepo.findByActividadIdAndPeriodo(act.getId(), periodo)
                        .orElseGet(CuelloBotella::new);
                cb.setActividadId(act.getId());
                cb.setDepartamentoId(act.getDepartamentoId());
                cb.setPeriodo(periodo);
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
