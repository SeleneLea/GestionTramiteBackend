package com.example.demo.services;

import com.example.demo.dto.RutaOptimaResponse;
import com.example.demo.dto.TramiteRiesgoResponse;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orquesta CU-42 (ruta óptima) y CU-43 (riesgo demora) delegando al microservicio IA.
 */
@Service
public class PrediccionService {

    @Autowired private IaProxyService iaProxy;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;

    // ── CU-42 ────────────────────────────────────────────────────────────────

    public RutaOptimaResponse calcularRutaOptima(String tramiteId) {
        Tramite t = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado: " + tramiteId));

        PoliticaNegocio politica = politicaRepository.findById(t.getPoliticaId())
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        List<NodoDiagrama> nodos = politica.getDiagramaId() != null
                ? nodoRepository.findByDiagramaId(politica.getDiagramaId())
                : List.of();

        List<Map<String, Object>> nodosPayload = new ArrayList<>();
        for (NodoDiagrama n : nodos) {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("id", n.getId());
            entry.put("nombre", n.getNombre());
            entry.put("tipo", n.getTipo());
            entry.put("orden", n.getOrden());
            entry.put("opcional", n.isOpcional());   // CU-42: la IA puede omitir opcionales
            nodosPayload.add(entry);
        }

        Map<String, Object> resp = iaProxy.rutaOptima(
                t.getId(), t.getPoliticaId(),
                Map.of(
                        "clienteId", t.getClienteId() != null ? t.getClienteId() : "",
                        "prioridad", t.getPrioridad()
                ),
                nodosPayload);

        @SuppressWarnings("unchecked")
        List<String> ruta = (List<String>) resp.getOrDefault("ruta_sugerida", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> omitidos = (List<Map<String, Object>>) resp.getOrDefault("pasos_omitidos", List.of());

        // Persistir en el trámite
        t.setRutaSugerida(ruta);
        tramiteRepository.save(t);

        return new RutaOptimaResponse(
                ruta,
                omitidos.stream()
                        .map(o -> new RutaOptimaResponse.PasoOmitido(
                                stringDe(o.get("nodo_id")),
                                stringDe(o.get("motivo"))))
                        .toList(),
                floatDe(resp.get("confianza")),
                stringDe(resp.get("explicacion"))
        );
    }

    // ── CU-43 ────────────────────────────────────────────────────────────────

    public List<TramiteRiesgoResponse> calcularRiesgoBatch(List<Tramite> tramites) {
        if (tramites.isEmpty()) return List.of();

        Map<String, Double> cargaPorDepto = cargaPorDepartamento();
        List<Map<String, Object>> features = new ArrayList<>();
        for (Tramite t : tramites) {
            Map<String, Object> f = new java.util.HashMap<>();
            f.put("tramite_id", t.getId());
            // Feature REAL (no hardcode): carga = trámites activos en el departamento
            // actual del trámite, normalizada (≥ CARGA_BASELINE activos = saturado).
            String depto = departamentoDe(t);
            f.put("carga_departamento", depto != null ? cargaPorDepto.getOrDefault(depto, 0.0) : 0.0);
            f.put("complejidad", Math.min(1.0, t.getPrioridad() / 3.0));
            // Hora/día de INGRESO del trámite (señal determinista de cuándo entró).
            LocalDateTime ref = t.getFechaInicio() != null ? t.getFechaInicio() : LocalDateTime.now();
            f.put("hora_dia", ref.getHour());
            f.put("dia_semana", ref.getDayOfWeek().getValue());
            features.add(f);
        }

        List<Map<String, Object>> resp = iaProxy.riesgoDemora(features);

        // Persistir en cada trámite
        List<TramiteRiesgoResponse> salida = new ArrayList<>();
        for (Map<String, Object> r : resp) {
            String tramiteId = stringDe(r.get("tramite_id"));
            Float prob = floatDe(r.get("prob_superar_sla"));
            String nivel = stringDe(r.get("nivel"));
            @SuppressWarnings("unchecked")
            List<String> razones = (List<String>) r.getOrDefault("razones", List.of());

            tramiteRepository.findById(tramiteId).ifPresent(t -> {
                t.setRiesgoDemora(nivel);
                t.setProbSuperarSla(prob);
                t.setUltimaPrediccionRiesgo(LocalDateTime.now());
                tramiteRepository.save(t);
            });

            salida.add(new TramiteRiesgoResponse(tramiteId, prob, nivel, razones));
        }
        return salida;
    }

    public List<TramiteRiesgoResponse> calcularRiesgoTramitesActivos(String nivelFiltro) {
        // Activos = aquellos que no estén Aprobado / Rechazado / Cancelado / Completado
        List<Tramite> activos = tramiteRepository.findAll().stream()
                .filter(t -> t.getFechaCierreReal() == null)
                .toList();
        List<TramiteRiesgoResponse> todos = calcularRiesgoBatch(activos);
        if (nivelFiltro == null || nivelFiltro.isBlank()) return todos;
        return todos.stream().filter(r -> nivelFiltro.equalsIgnoreCase(r.getNivel())).toList();
    }

    // ── features reales ───────────────────────────────────────────────────────

    /** #trámites activos en un departamento = más alto = más carga; ≥8 = saturado. */
    private static final double CARGA_BASELINE = 8.0;

    /** Carga normalizada [0..1] por departamento, según trámites activos en curso. */
    private Map<String, Double> cargaPorDepartamento() {
        Map<String, Integer> conteo = new java.util.HashMap<>();
        for (Tramite t : tramiteRepository.findAll()) {
            if (t.getFechaCierreReal() != null) continue;   // solo activos
            String depto = departamentoDe(t);
            if (depto != null) conteo.merge(depto, 1, Integer::sum);
        }
        Map<String, Double> carga = new java.util.HashMap<>();
        conteo.forEach((d, c) -> carga.put(d, Math.min(1.0, c / CARGA_BASELINE)));
        return carga;
    }

    /** Departamento actual del trámite, vía su nodo activo (o la 1ª rama paralela). */
    private String departamentoDe(Tramite t) {
        String nodoId = t.getNodoActualId();
        if (nodoId == null && t.getNodosParalellosActivos() != null
                && !t.getNodosParalellosActivos().isEmpty()) {
            nodoId = t.getNodosParalellosActivos().get(0);
        }
        if (nodoId == null) return null;
        return nodoRepository.findById(nodoId).map(NodoDiagrama::getDepartamentoId).orElse(null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String stringDe(Object o) { return o == null ? null : o.toString(); }

    private Float floatDe(Object o) {
        if (o instanceof Number n) return n.floatValue();
        if (o instanceof String s) {
            try { return Float.parseFloat(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
