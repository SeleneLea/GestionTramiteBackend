package com.example.demo.services;

import com.example.demo.dto.PromptFlujoRequest;
import com.example.demo.dto.PromptFlujoResponse;
import com.example.demo.models.Actividad;
import com.example.demo.models.Departamento;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CU-14 — Diseño de flujo por prompt.
 *
 * Intenta primero la IA real (microservicio FastAPI → Gemini), que interpreta el
 * lenguaje natural y devuelve la estructura del diagrama (nodos + transiciones,
 * incluida la topología mixta y qué pasos van en paralelo). El backend la
 * materializa, mapea departamentos y ENLAZA actividades reutilizables. Si la IA
 * no está disponible (provider local / micro caído), cae a una heurística.
 */
@Service
public class PromptFlowService {

    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private IaProxyService iaProxy;

    @SuppressWarnings("unchecked")
    public PromptFlujoResponse generarDesdePrompt(PromptFlujoRequest req, String creadorId) {
        List<Departamento> activos = departamentoRepository.findByActivoTrue();

        // 1) IA real (si está disponible).
        try {
            List<String> nombres = activos.stream().map(Departamento::getNombre).toList();
            Map<String, Object> flujo = iaProxy.generarFlujo(req.getPrompt(), nombres);
            List<Map<String, Object>> nodosIa = (List<Map<String, Object>>) flujo.get("nodos");
            List<Map<String, Object>> transIa = (List<Map<String, Object>>) flujo.getOrDefault("transiciones", List.of());
            if (nodosIa != null && !nodosIa.isEmpty()) {
                return materializarIa(req, creadorId, activos, nodosIa, transIa);
            }
        } catch (RuntimeException e) {
            // IA no disponible (503 IA_NO_DISPONIBLE) o respuesta inválida → heurística.
        }

        // 2) Heurística determinista (fallback que nunca rompe).
        return generarHeuristico(req, creadorId, activos);
    }

    // ── Materialización del flujo devuelto por la IA ───────────────────────────
    private PromptFlujoResponse materializarIa(PromptFlujoRequest req, String creadorId,
                                               List<Departamento> activos,
                                               List<Map<String, Object>> nodosIa,
                                               List<Map<String, Object>> transIa) {
        DiagramaWorkflow d = nuevoDiagrama(req, creadorId);

        List<NodoDiagrama> nodosCreados = new ArrayList<>();
        Set<String> swimlanes = new LinkedHashSet<>();
        int orden = 0;
        for (Map<String, Object> n : nodosIa) {
            String tipo = str(n.get("tipo"), "actividad");
            String nombre = str(n.get("nombre"), tipo);
            String deptNombre = str(n.get("departamento"), "");
            boolean opcional = Boolean.TRUE.equals(n.get("opcional"));

            String departamentoId = null;
            String swimlane = null;
            if ("actividad".equals(tipo) && !deptNombre.isBlank()) {
                Departamento dep = matchDepartamento(deptNombre, activos);
                if (dep != null) {
                    departamentoId = dep.getId();
                    swimlane = dep.getNombre();
                    swimlanes.add(dep.getNombre());
                } else {
                    swimlane = deptNombre;          // nombre tal cual aunque no matchee
                }
            }
            NodoDiagrama nodo = crearNodo(d.getId(), tipo, nombre, departamentoId, swimlane, orden++);
            if (opcional) {
                nodo.setOpcional(true);
                nodo = nodoRepository.save(nodo);
            }
            nodosCreados.add(nodo);
        }
        if (!swimlanes.isEmpty()) {
            d.setSwimlanes(new ArrayList<>(swimlanes));
            d = diagramaRepository.save(d);
        }

        List<FlujoTransicion> transiciones = new ArrayList<>();
        int total = nodosCreados.size();
        for (Map<String, Object> t : transIa) {
            int o = intDe(t.get("origen"), -1);
            int s = intDe(t.get("destino"), -1);
            if (o < 0 || o >= total || s < 0 || s >= total || o == s) continue;
            transiciones.add(crearTransicion(d.getId(),
                    nodosCreados.get(o).getId(), nodosCreados.get(s).getId(),
                    str(t.get("tipo"), "secuencial"), str(t.get("etiqueta"), null)));
        }

        return new PromptFlujoResponse(d, nodosCreados, transiciones, req.getPrompt());
    }

    // ── Heurística determinista (fallback) ─────────────────────────────────────
    private PromptFlujoResponse generarHeuristico(PromptFlujoRequest req, String creadorId,
                                                  List<Departamento> todos) {
        String promptLower = req.getPrompt().toLowerCase(Locale.ROOT);

        List<Departamento> mencionados = new ArrayList<>();
        for (Departamento dep : todos) {
            int idxNombre = promptLower.indexOf(dep.getNombre().toLowerCase(Locale.ROOT));
            int idxCodigo = dep.getCodigo() != null
                    ? promptLower.indexOf(dep.getCodigo().toLowerCase(Locale.ROOT)) : -1;
            if ((idxNombre >= 0 || idxCodigo >= 0) && !mencionados.contains(dep)) {
                mencionados.add(dep);
            }
        }
        if (mencionados.isEmpty()) {
            List<String> nombresYCodigos = todos.stream()
                    .map(dep -> dep.getNombre() + (dep.getCodigo() != null ? " (" + dep.getCodigo() + ")" : ""))
                    .toList();
            throw new IllegalArgumentException(
                    "No se detectó ningún departamento en el prompt. Menciona al menos uno por nombre o "
                            + "código. Departamentos disponibles: " + String.join(", ", nombresYCodigos));
        }
        mencionados.sort((a, b) -> Integer.compare(primeraAparicion(promptLower, a), primeraAparicion(promptLower, b)));

        boolean tieneDecision = promptLower.contains("aprueba") || promptLower.contains("rechaza")
                || promptLower.contains("condición") || promptLower.contains("decisión");
        boolean tieneParalelo = promptLower.contains("paralelo")
                || promptLower.contains("simultaneo") || promptLower.contains("simultáneo");

        DiagramaWorkflow d = nuevoDiagrama(req, creadorId);
        d.setSwimlanes(mencionados.stream().map(Departamento::getNombre).toList());
        d = diagramaRepository.save(d);

        List<NodoDiagrama> nodosCreados = new ArrayList<>();
        int orden = 0;
        NodoDiagrama inicio = crearNodo(d.getId(), "inicio", "Inicio", null, null, orden++);
        nodosCreados.add(inicio);

        NodoDiagrama fork = null, join = null;
        List<NodoDiagrama> actividadesParalelas = new ArrayList<>();
        if (tieneParalelo && mencionados.size() >= 2) {
            fork = crearNodo(d.getId(), "fork", "Fork paralelo", null, null, orden++);
            nodosCreados.add(fork);
            for (Departamento dep : mencionados) {
                NodoDiagrama act = crearNodo(d.getId(), "actividad",
                        "Actividad " + dep.getNombre(), dep.getId(), dep.getNombre(), orden++);
                actividadesParalelas.add(act);
                nodosCreados.add(act);
            }
            join = crearNodo(d.getId(), "join", "Join paralelo", null, null, orden++);
            nodosCreados.add(join);
        } else {
            for (Departamento dep : mencionados) {
                nodosCreados.add(crearNodo(d.getId(), "actividad",
                        "Actividad " + dep.getNombre(), dep.getId(), dep.getNombre(), orden++));
            }
        }
        if (tieneDecision) {
            nodosCreados.add(crearNodo(d.getId(), "decision", "¿Aprobar?", null, null, orden++));
        }
        nodosCreados.add(crearNodo(d.getId(), "fin", "Fin", null, null, orden));

        List<FlujoTransicion> transiciones = new ArrayList<>();
        if (tieneParalelo && fork != null && join != null) {
            transiciones.add(crearTransicion(d.getId(), inicio.getId(), fork.getId(), "secuencial", null));
            for (NodoDiagrama act : actividadesParalelas) {
                transiciones.add(crearTransicion(d.getId(), fork.getId(), act.getId(), "paralelo", null));
            }
            for (NodoDiagrama act : actividadesParalelas) {
                transiciones.add(crearTransicion(d.getId(), act.getId(), join.getId(), "secuencial", null));
            }
            NodoDiagrama siguiente = nodosCreados.stream()
                    .filter(n -> "decision".equals(n.getTipo()) || "fin".equals(n.getTipo()))
                    .findFirst().orElse(null);
            if (siguiente != null) {
                transiciones.add(crearTransicion(d.getId(), join.getId(), siguiente.getId(), "secuencial", null));
            }
        } else {
            for (int i = 0; i < nodosCreados.size() - 1; i++) {
                NodoDiagrama actual = nodosCreados.get(i), siguiente = nodosCreados.get(i + 1);
                String tipo = "decision".equals(actual.getTipo()) ? "condicional" : "secuencial";
                String etiqueta = "decision".equals(actual.getTipo()) ? "si" : null;
                transiciones.add(crearTransicion(d.getId(), actual.getId(), siguiente.getId(), tipo, etiqueta));
            }
        }
        if (tieneDecision) {
            NodoDiagrama decision = nodosCreados.stream()
                    .filter(n -> "decision".equals(n.getTipo())).findFirst().orElseThrow();
            NodoDiagrama anterior;
            if (tieneParalelo && fork != null) {
                anterior = fork;
            } else {
                int idx = nodosCreados.indexOf(decision);
                anterior = idx > 0 ? nodosCreados.get(idx - 1) : null;
            }
            if (anterior != null) {
                transiciones.add(crearTransicion(d.getId(), decision.getId(), anterior.getId(), "iterativo", "no"));
            }
            if (tieneParalelo) {
                NodoDiagrama fin = nodosCreados.stream()
                        .filter(n -> "fin".equals(n.getTipo())).findFirst().orElse(null);
                if (fin != null) {
                    transiciones.add(crearTransicion(d.getId(), decision.getId(), fin.getId(), "condicional", "si"));
                }
            }
        }
        return new PromptFlujoResponse(d, nodosCreados, transiciones, req.getPrompt());
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private DiagramaWorkflow nuevoDiagrama(PromptFlujoRequest req, String creadorId) {
        DiagramaWorkflow d = new DiagramaWorkflow();
        d.setNombre(req.getNombreDiagrama());
        d.setPoliticaId(req.getPoliticaId());
        d.setCreadorId(creadorId);
        d.setVersionActual(1);
        d.setEstado("borrador");
        d.setGeneradoPorIa(true);
        d.setFechaCreacion(LocalDateTime.now());
        d.setUltimaModificacion(LocalDateTime.now());
        return diagramaRepository.save(d);
    }

    private int primeraAparicion(String promptLower, Departamento dep) {
        int idxNombre = promptLower.indexOf(dep.getNombre().toLowerCase(Locale.ROOT));
        int idxCodigo = dep.getCodigo() != null
                ? promptLower.indexOf(dep.getCodigo().toLowerCase(Locale.ROOT)) : -1;
        if (idxNombre < 0) return idxCodigo;
        if (idxCodigo < 0) return idxNombre;
        return Math.min(idxNombre, idxCodigo);
    }

    /** Empareja el nombre de departamento de la IA con un departamento real (difuso). */
    private Departamento matchDepartamento(String nombre, List<Departamento> activos) {
        String n = norm(nombre);
        for (Departamento dep : activos) {
            if (norm(dep.getNombre()).equals(n)) return dep;
        }
        for (Departamento dep : activos) {
            if (norm(dep.getNombre()).contains(n) || n.contains(norm(dep.getNombre()))) return dep;
        }
        return null;
    }

    /** Actividad REUTILIZABLE del departamento (CU: reuso de componentes); null si no hay. */
    private String actividadReutilizableDe(String departamentoId) {
        if (departamentoId == null) return null;
        List<Actividad> delDepto = actividadRepository.findByDepartamentoId(departamentoId);
        return delDepto.stream().filter(Actividad::isReutilizable).map(Actividad::getId).findFirst()
                .orElseGet(() -> delDepto.stream().map(Actividad::getId).findFirst().orElse(null));
    }

    private NodoDiagrama crearNodo(String diagramaId, String tipo, String nombre,
                                   String departamentoId, String swimlane, int orden) {
        NodoDiagrama n = new NodoDiagrama();
        n.setDiagramaId(diagramaId);
        n.setTipo(tipo);
        n.setNombre(nombre);
        n.setDepartamentoId(departamentoId);
        n.setSwimlane(swimlane);
        n.setOrden(orden);
        // Reuso de actividades: enlaza una actividad reutilizable del departamento.
        if ("actividad".equals(tipo) && departamentoId != null) {
            n.setActividadId(actividadReutilizableDe(departamentoId));
        }
        return nodoRepository.save(n);
    }

    private FlujoTransicion crearTransicion(String diagramaId, String origenId, String destinoId,
                                            String tipo, String etiqueta) {
        FlujoTransicion t = new FlujoTransicion();
        t.setDiagramaId(diagramaId);
        t.setNodoOrigenId(origenId);
        t.setNodoDestinoId(destinoId);
        t.setTipo(tipo);
        t.setEtiqueta(etiqueta);
        return flujoRepository.save(t);
    }

    private String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private int intDe(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return o != null ? Integer.parseInt(o.toString()) : def; } catch (NumberFormatException e) { return def; }
    }

    private String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
