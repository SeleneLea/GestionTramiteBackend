package com.example.demo.controllers;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.IniciarTramiteRequest;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.ExpedienteDigitalRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.UsuarioRepository;
import com.example.demo.services.WorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tramites")
@Tag(name = "Motor de Workflow", description = "Núcleo del sistema — inicia y avanza trámites por el diagrama UML")
public class WorkflowController {

    @Autowired private WorkflowEngineService workflowEngine;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ExpedienteDigitalRepository expedienteRepository;
    @Autowired private SeccionExpedienteRepository seccionRepository;
    @Autowired private EstadoHistoricoRepository historicoRepository;

    @PostMapping("/iniciar")
    @PreAuthorize("hasAnyRole('CLIENTE','FUNCIONARIO','ADMINISTRADOR')")
    @Operation(
        summary = "Iniciar un trámite",
        description = "Crea el trámite, genera el expediente digital con todas las secciones, y avanza automáticamente al primer nodo actividad del diagrama."
    )
    public ResponseEntity<Tramite> iniciar(@Valid @RequestBody IniciarTramiteRequest req,
                                            Authentication auth) {
        boolean isCliente = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
        if (isCliente) {
            req.setClienteId(auth.getName());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowEngine.iniciarTramite(req));
    }

    @PostMapping("/{tramiteId}/completar-nodo")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(
        summary = "Completar nodo actual",
        description = "El funcionario indica que terminó su sección. El motor evalúa el siguiente paso: avanza linealmente, evalúa condición, activa fork o cierra el trámite."
    )
    public ResponseEntity<Tramite> completarNodo(@PathVariable String tramiteId,
                                                  @Valid @RequestBody CompletarNodoRequest req) {
        return ResponseEntity.ok(workflowEngine.completarNodo(tramiteId, req));
    }

    @GetMapping("/{tramiteId}/estado")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Detalle del trámite (nodo actual, progreso e historial)")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable String tramiteId) {
        Tramite t = workflowEngine.buscarTramite(tramiteId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", t.getId());
        resp.put("codigo", t.getCodigo());
        resp.put("estado", t.getEstadoActual());
        resp.put("politicaId", t.getPoliticaId());
        resp.put("clienteId", t.getClienteId());
        resp.put("prioridad", t.getPrioridad());
        resp.put("fechaInicio", t.getFechaInicio());
        resp.put("fechaLimite", t.getFechaEstimadaCierre());
        resp.put("nodoActualId", t.getNodoActualId());
        resp.put("enParalelo", t.estaEnParalelo());
        resp.put("nodosParalellosActivos", t.getNodosParalellosActivos());

        // Política y cliente (nombres resueltos)
        if (t.getPoliticaId() != null) {
            politicaRepository.findById(t.getPoliticaId())
                    .ifPresent(p -> resp.put("politicaNombre", p.getNombre()));
        }
        if (t.getClienteId() != null) {
            usuarioRepository.findById(t.getClienteId())
                    .ifPresent(u -> resp.put("clienteNombre",
                            (u.getNombre() != null ? u.getNombre() : "") +
                            (u.getApellido() != null ? " " + u.getApellido() : "")));
        }

        // Nodo actual (anidado, como espera el frontend)
        if (t.getNodoActualId() != null) {
            nodoRepository.findById(t.getNodoActualId()).ifPresent(nodo -> {
                resp.put("nodoActualNombre", nodo.getNombre());
                Map<String, Object> nodoActual = new HashMap<>();
                nodoActual.put("nodoId", nodo.getId());
                nodoActual.put("nombre", nodo.getNombre());
                nodoActual.put("tipo", nodo.getTipo());
                nodoActual.put("departamentoId", nodo.getDepartamentoId());
                nodoActual.put("actividadId", nodo.getActividadId());
                nodoActual.put("funcionarioId", t.getFuncionarioActualId());
                if (nodo.getDepartamentoId() != null) {
                    departamentoRepository.findById(nodo.getDepartamentoId())
                            .ifPresent(d -> nodoActual.put("departamentoNombre", d.getNombre()));
                }
                if (t.getExpedienteId() != null) {
                    seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId())
                            .stream()
                            .filter(s -> nodo.getId().equals(s.getNodoId()))
                            .findFirst()
                            .ifPresent(s -> {
                                nodoActual.put("estado", s.getEstado());
                                nodoActual.put("fechaInicio", s.getFechaAsignacion());
                            });
                }
                nodoActual.putIfAbsent("estado", "en_curso");
                resp.put("nodoActual", nodoActual);
            });
        }

        // Progreso = secciones completadas / total
        int progreso = 0;
        if (t.getExpedienteId() != null) {
            List<SeccionExpediente> secciones = seccionRepository
                    .findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId());
            if (!secciones.isEmpty()) {
                long completadas = secciones.stream()
                        .filter(s -> "completada".equals(s.getEstado()))
                        .count();
                progreso = (int) Math.round(100.0 * completadas / secciones.size());
            }
        }
        resp.put("progreso", progreso);

        // Historial: combinar EstadoHistorico + secciones para mostrar el recorrido
        List<EstadoHistorico> historicos = historicoRepository.findByTramiteIdOrderByFechaCambioAsc(t.getId());
        List<NodoDiagrama> todosNodos = nodoRepository.findAll();
        List<Map<String, Object>> historial = historicos.stream().map(h -> {
            Map<String, Object> hito = new HashMap<>();
            String nodoId = h.getNodoNuevoId() != null ? h.getNodoNuevoId() : h.getNodoAnteriorId();
            hito.put("nodoId", nodoId);
            String nombre = todosNodos.stream()
                    .filter(n -> n.getId().equals(nodoId))
                    .findFirst().map(NodoDiagrama::getNombre).orElse("—");
            hito.put("nombre", nombre);
            hito.put("estado", h.getEstadoNuevo());
            hito.put("funcionarioId", h.getActorId());
            hito.put("fechaCompletado", h.getFechaCambio());
            hito.put("resultado", h.getEstadoNuevo());
            hito.put("observaciones", h.getMotivo());
            return hito;
        }).toList();
        resp.put("historial", historial);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/mis-tramites")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Listar mis trámites",
        description = "Devuelve los trámites del usuario autenticado (cliente) ordenados por fecha descendente"
    )
    public ResponseEntity<List<Tramite>> misTramites(Authentication auth) {
        String clienteId = auth.getName();
        List<Tramite> tramites = workflowEngine.listarPorCliente(clienteId);
        return ResponseEntity.ok(tramites);
    }

    @GetMapping("/mis-pendientes")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    @Operation(summary = "Bandeja de tramites pendientes del funcionario autenticado")
    public ResponseEntity<List<Map<String, Object>>> misPendientes(Authentication auth) {
        String userId = auth.getName();
        boolean esAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));

        List<Tramite> activos = tramiteRepository.findTramitesActivos();
        List<Map<String, Object>> pendientes = activos.stream()
                .filter(t -> t.getNodoActualId() != null || t.estaEnParalelo())
                .filter(t -> esAdmin || userId.equals(t.getFuncionarioActualId()))
                .map(this::resumenTramite)
                .toList();
        return ResponseEntity.ok(pendientes);
    }

    private Map<String, Object> resumenTramite(Tramite t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("codigo", t.getCodigo());
        m.put("estado", t.getEstadoActual());
        m.put("estadoActual", t.getEstadoActual());
        m.put("politicaId", t.getPoliticaId());
        m.put("clienteId", t.getClienteId());
        m.put("prioridad", t.getPrioridad());
        m.put("fechaInicio", t.getFechaInicio());
        m.put("fechaLimite", t.getFechaEstimadaCierre());
        m.put("nodoActualId", t.getNodoActualId());
        if (t.getPoliticaId() != null) {
            politicaRepository.findById(t.getPoliticaId())
                    .ifPresent(p -> m.put("politicaNombre", p.getNombre()));
        }
        if (t.getClienteId() != null) {
            usuarioRepository.findById(t.getClienteId())
                    .ifPresent(u -> m.put("clienteNombre",
                            (u.getNombre() != null ? u.getNombre() : "") +
                            (u.getApellido() != null ? " " + u.getApellido() : "")));
        }
        if (t.getNodoActualId() != null) {
            nodoRepository.findById(t.getNodoActualId())
                    .ifPresent(n -> m.put("nodoActualNombre", n.getNombre()));
        }
        // progreso
        if (t.getExpedienteId() != null) {
            List<SeccionExpediente> secciones = seccionRepository
                    .findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId());
            int progreso = 0;
            if (!secciones.isEmpty()) {
                long completadas = secciones.stream()
                        .filter(s -> "completada".equals(s.getEstado()))
                        .count();
                progreso = (int) Math.round(100.0 * completadas / secciones.size());
            }
            m.put("progreso", progreso);
        } else {
            m.put("progreso", 0);
        }
        return m;
    }
}
