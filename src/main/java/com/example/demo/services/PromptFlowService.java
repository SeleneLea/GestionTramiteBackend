package com.example.demo.services;

import com.example.demo.dto.PromptFlujoRequest;
import com.example.demo.dto.PromptFlujoResponse;
import com.example.demo.models.Departamento;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PromptFlowService {

    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;

    public PromptFlujoResponse generarDesdePrompt(PromptFlujoRequest req, String creadorId) {
        String promptLower = req.getPrompt().toLowerCase(Locale.ROOT);

        // 1. Detectar departamentos mencionados (por nombre completo o por código)
        List<Departamento> todos = departamentoRepository.findByActivoTrue();
        List<Departamento> mencionados = new ArrayList<>();
        for (Departamento dep : todos) {
            int idxNombre = promptLower.indexOf(dep.getNombre().toLowerCase(Locale.ROOT));
            int idxCodigo = dep.getCodigo() != null
                    ? promptLower.indexOf(dep.getCodigo().toLowerCase(Locale.ROOT))
                    : -1;
            if ((idxNombre >= 0 || idxCodigo >= 0) && !mencionados.contains(dep)) {
                mencionados.add(dep);
            }
        }
        if (mencionados.isEmpty()) {
            List<String> nombresYCodigos = todos.stream()
                    .map(dep -> dep.getNombre() + (dep.getCodigo() != null ? " (" + dep.getCodigo() + ")" : ""))
                    .toList();
            throw new IllegalArgumentException(
                    "No se detectó ningún departamento en el prompt. " +
                    "Menciona al menos uno por nombre o código. Departamentos disponibles: " +
                    String.join(", ", nombresYCodigos));
        }
        // Ordenar por posición de primera aparición (nombre o código)
        mencionados.sort((a, b) -> Integer.compare(
                primeraAparicion(promptLower, a),
                primeraAparicion(promptLower, b)));

        boolean tieneDecision = promptLower.contains("aprueba") || promptLower.contains("rechaza")
                || promptLower.contains("condición") || promptLower.contains("decisión");
        boolean tieneParalelo = promptLower.contains("paralelo")
                || promptLower.contains("simultaneo") || promptLower.contains("simultáneo");

        // 2. Crear el diagrama
        DiagramaWorkflow d = new DiagramaWorkflow();
        d.setNombre(req.getNombreDiagrama());
        d.setPoliticaId(req.getPoliticaId());
        d.setCreadorId(creadorId);
        d.setSwimlanes(mencionados.stream().map(Departamento::getNombre).toList());
        d.setVersionActual(1);
        d.setEstado("borrador");
        d.setGeneradoPorIa(true);
        d.setFechaCreacion(LocalDateTime.now());
        d.setUltimaModificacion(LocalDateTime.now());
        d = diagramaRepository.save(d);

        // 3. Generar nodos
        List<NodoDiagrama> nodosCreados = new ArrayList<>();
        int orden = 0;
        
        NodoDiagrama inicio = crearNodo(d.getId(), "inicio", "Inicio", null, null, orden++);
        nodosCreados.add(inicio);

        NodoDiagrama fork = null;
        NodoDiagrama join = null;
        List<NodoDiagrama> actividadesParalelas = new ArrayList<>();

        // Si hay paralelo, crear fork y las ramas
        if (tieneParalelo && mencionados.size() >= 2) {
            fork = crearNodo(d.getId(), "fork", "Fork paralelo", null, null, orden++);
            nodosCreados.add(fork);

            // Crear actividades para cada departamento (en paralelo)
            for (Departamento dep : mencionados) {
                NodoDiagrama actividad = crearNodo(d.getId(), "actividad",
                        "Actividad " + dep.getNombre(), dep.getId(), dep.getNombre(), orden++);
                actividadesParalelas.add(actividad);
                nodosCreados.add(actividad);
            }

            join = crearNodo(d.getId(), "join", "Join paralelo", null, null, orden++);
            nodosCreados.add(join);
        } else {
            // Flujo lineal: solo actividades en secuencia
            for (Departamento dep : mencionados) {
                NodoDiagrama actividad = crearNodo(d.getId(), "actividad",
                        "Actividad " + dep.getNombre(), dep.getId(), dep.getNombre(), orden++);
                nodosCreados.add(actividad);
            }
        }

        // Si hay decisión, agregarla antes del fin
        if (tieneDecision) {
            nodosCreados.add(crearNodo(d.getId(), "decision", "¿Aprobar?", null, null, orden++));
        }
        nodosCreados.add(crearNodo(d.getId(), "fin", "Fin", null, null, orden));

        // 4. Generar transiciones
        List<FlujoTransicion> transicionesCreadas = new ArrayList<>();

        if (tieneParalelo && fork != null && join != null) {
            transicionesCreadas.add(crearTransicion(d.getId(), inicio.getId(), fork.getId(), "secuencial", null));

            for (NodoDiagrama act : actividadesParalelas) {
                transicionesCreadas.add(crearTransicion(d.getId(), fork.getId(), act.getId(), "paralelo", null));
            }

            for (NodoDiagrama act : actividadesParalelas) {
                transicionesCreadas.add(crearTransicion(d.getId(), act.getId(), join.getId(), "secuencial", null));
            }

            NodoDiagrama siguiente = null;
            for (NodoDiagrama n : nodosCreados) {
                if ("decision".equals(n.getTipo()) || "fin".equals(n.getTipo())) {
                    siguiente = n;
                    break;
                }
            }
            if (siguiente != null) {
                transicionesCreadas.add(crearTransicion(d.getId(), join.getId(), siguiente.getId(), "secuencial", null));
            }
        } else {
            for (int i = 0; i < nodosCreados.size() - 1; i++) {
                NodoDiagrama actual = nodosCreados.get(i);
                NodoDiagrama siguiente = nodosCreados.get(i + 1);
                String tipo = "decision".equals(actual.getTipo()) ? "condicional" : "secuencial";
                String etiqueta = "decision".equals(actual.getTipo()) ? "si" : null;
                transicionesCreadas.add(crearTransicion(d.getId(), actual.getId(), siguiente.getId(), tipo, etiqueta));
            }
        }

        if (tieneDecision) {
            NodoDiagrama decision = nodosCreados.stream()
                    .filter(n -> "decision".equals(n.getTipo()))
                    .findFirst().orElseThrow();

            NodoDiagrama actividadAnterior = null;
            if (tieneParalelo && join != null) {
                actividadAnterior = join;
            } else {
                int decisionIdx = nodosCreados.indexOf(decision);
                if (decisionIdx > 0) {
                    actividadAnterior = nodosCreados.get(decisionIdx - 1);
                }
            }

            if (actividadAnterior != null) {
                transicionesCreadas.add(crearTransicion(d.getId(), decision.getId(),
                        actividadAnterior.getId(), "iterativo", "no"));
            }
        }

        return new PromptFlujoResponse(d, nodosCreados, transicionesCreadas, req.getPrompt());
    }

    private int primeraAparicion(String promptLower, Departamento dep) {
        int idxNombre = promptLower.indexOf(dep.getNombre().toLowerCase(Locale.ROOT));
        int idxCodigo = dep.getCodigo() != null
                ? promptLower.indexOf(dep.getCodigo().toLowerCase(Locale.ROOT))
                : -1;
        if (idxNombre < 0) return idxCodigo;
        if (idxCodigo < 0) return idxNombre;
        return Math.min(idxNombre, idxCodigo);
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
        return nodoRepository.save(n);
    }

    private FlujoTransicion crearTransicion(String diagramaId, String nodoOrigenId, String nodoDestinoId,
                                            String tipo, String etiqueta) {
        FlujoTransicion t = new FlujoTransicion();
        t.setDiagramaId(diagramaId);
        t.setNodoOrigenId(nodoOrigenId);
        t.setNodoDestinoId(nodoDestinoId);
        t.setTipo(tipo);
        t.setEtiqueta(etiqueta);
        return flujoRepository.save(t);
    }
}
