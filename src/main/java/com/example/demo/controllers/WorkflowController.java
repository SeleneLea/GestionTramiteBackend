package com.example.demo.controllers;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.FlujoCompletoResponse;
import com.example.demo.dto.IniciarTramiteRequest;
import com.example.demo.models.Actividad;
import com.example.demo.models.Documento;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.EstadoSeccion;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.DocumentoRepository;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.ExpedienteDigitalRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.UsuarioRepository;
import com.example.demo.services.DocumentoArchivoService;
import com.example.demo.services.RequisitoDocumentoService;
import com.example.demo.services.IaProxyService;
import com.example.demo.services.WorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private EstadoHistoricoRepository historicoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private DocumentoArchivoService documentoArchivoService;
    @Autowired private RequisitoDocumentoService requisitoDocumentoService;
    /** CU-44 — proxy al microservicio para ordenar la bandeja por IA. Opcional. */
    @Autowired(required = false) private IaProxyService iaProxy;

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
                                                  @Valid @RequestBody CompletarNodoRequest req,
                                                  Authentication auth) {
        req.setFuncionarioId(auth.getName());
        return ResponseEntity.ok(workflowEngine.completarNodo(tramiteId, req));
    }

    @PostMapping("/{tramiteId}/aceptar")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(
        summary = "Aceptar (recepcionar) el trámite",
        description = "El responsable acepta el trámite que llegó a su bandeja: la sección activa pasa de 'Pendiente de recepción' a 'En ejecución' y queda a su cargo."
    )
    public ResponseEntity<Tramite> aceptar(@PathVariable String tramiteId,
                                           Authentication auth) {
        return ResponseEntity.ok(workflowEngine.aceptarTramite(tramiteId, auth.getName()));
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
        // Documento de resolución entregable (si el trámite ya lo produjo).
        resp.put("documentoResolucionId", t.getDocumentoResolucionId());
        resp.put("tipoResolucion", t.getTipoResolucion());
        resp.put("fechaResolucion", t.getFechaResolucion());

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
                // Salidas posibles DERIVADAS de la posición del nodo en el flujo
                // (no se configuran a mano): si su siguiente es un cierre (fin) salen
                // 'aprobar'/'rechazar'; si avanza a otro paso sale 'completar'.
                // 'observar' (devolver) está siempre disponible.
                {
                    List<FlujoTransicion> salidasNodo = flujoRepository.findByNodoOrigenId(nodo.getId());
                    // Es un nodo de cierre si no tiene salida (último paso) o si alguna
                    // de sus salidas lleva directamente a un 'fin'.
                    boolean haciaCierre = salidasNodo.isEmpty() || salidasNodo.stream()
                            .anyMatch(tr -> nodoRepository.findById(tr.getNodoDestinoId())
                                    .map(nd -> "fin".equals(nd.getTipo()))
                                    .orElse(false));
                    // Rechazar y Observar SIEMPRE disponibles (un trámite puede
                    // rechazarse o devolverse en cualquier punto). Lo que cambia por
                    // posición es el avance: 'aprobar' (cierre) vs 'completar'.
                    nodoActual.put("salidasPosibles", haciaCierre
                            ? List.of("aprobar", "rechazar", "observar")
                            : List.of("completar", "rechazar", "observar"));
                }
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
                nodoActual.putIfAbsent("estado", EstadoSeccion.EN_EJECUCION.getValor());
                resp.put("nodoActual", nodoActual);
            });
        }

        // Look-ahead: si el nodo siguiente a la actividad actual es un 'decision'
        // (if), exponer su pregunta + ramas para que el funcionario responda Sí/No
        // al avanzar. El motor (avanzarDesde case 'decision') enruta luego por la
        // etiqueta de la transición elegida. El funcionario solo ve la pregunta,
        // nunca el formulario del paso posterior.
        if (t.getNodoActualId() != null) {
            List<FlujoTransicion> salientes = flujoRepository.findByNodoOrigenId(t.getNodoActualId());
            // El motor (avanzarDesde case actividad) toma la primera transición; usamos
            // la misma selección para que el look-ahead coincida con el ruteo real.
            if (!salientes.isEmpty()) {
                nodoRepository.findById(salientes.get(0).getNodoDestinoId())
                        .filter(dest -> "decision".equals(dest.getTipo()))
                        .ifPresent(decision -> {
                            List<FlujoTransicion> ramas = flujoRepository.findByNodoOrigenId(decision.getId());
                            Map<String, Object> dec = new HashMap<>();
                            dec.put("nodoId", decision.getId());
                            dec.put("pregunta", decision.getNombre());
                            List<Map<String, Object>> opciones = ramas
                                    .stream()
                                    .map(tr -> {
                                        Map<String, Object> op = new HashMap<>();
                                        String etiqueta = tr.getEtiqueta() != null ? tr.getEtiqueta() : "";
                                        op.put("valor", etiqueta);
                                        op.put("etiqueta", capitalizarRama(etiqueta));
                                        nodoRepository.findById(tr.getNodoDestinoId())
                                                .ifPresent(nd -> op.put("destinoNombre", nd.getNombre()));
                                        return op;
                                    })
                                    .toList();
                            dec.put("opciones", opciones);
                            resp.put("decisionSiguiente", dec);
                        });
            }
        }

        // Progreso = secciones completadas / total. De paso, exponemos el
        // expediente con sus secciones (una por paso/nodo) para la pestaña
        // "Secciones" del cliente (web y móvil leen expediente.secciones).
        int progreso = 0;
        if (t.getExpedienteId() != null) {
            List<SeccionExpediente> secciones = seccionRepository
                    .findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId());
            if (!secciones.isEmpty()) {
                long completadas = secciones.stream()
                        .filter(s -> EstadoSeccion.esDerivada(s.getEstado()))
                        .count();
                progreso = (int) Math.round(100.0 * completadas / secciones.size());
            }

            List<Map<String, Object>> seccionesDto = secciones.stream().map(s -> {
                Map<String, Object> sm = new HashMap<>();
                sm.put("id", s.getId());
                sm.put("estado", s.getEstado());
                sm.put("fechaInicio", s.getFechaAsignacion());
                sm.put("fechaCompletacion", s.getFechaCompletado());
                // El "nombre" del paso es el nombre del nodo; resolvemos también
                // la actividad y el departamento para mostrarlos en la sección.
                nodoRepository.findById(s.getNodoId()).ifPresent(n -> {
                    sm.put("nombre", n.getNombre());
                    if (n.getActividadId() != null) {
                        actividadRepository.findById(n.getActividadId())
                                .ifPresent(a -> sm.put("actividad", a.getNombre()));
                    }
                });
                sm.putIfAbsent("nombre", "Paso");
                if (s.getDepartamentoId() != null) {
                    departamentoRepository.findById(s.getDepartamentoId())
                            .ifPresent(d -> sm.put("departamento", d.getNombre()));
                }
                return sm;
            }).toList();

            Map<String, Object> expedienteDto = new HashMap<>();
            expedienteDto.put("id", t.getExpedienteId());
            expedienteDto.put("secciones", seccionesDto);
            resp.put("expediente", expedienteDto);
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

    /** Etiqueta legible de una rama del nodo decisión (si/no → Sí/No). */
    private String capitalizarRama(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) return etiqueta;
        String e = etiqueta.trim().toLowerCase();
        if (e.equals("si")) return "Sí";
        if (e.equals("no")) return "No";
        return etiqueta.substring(0, 1).toUpperCase() + etiqueta.substring(1);
    }

    @GetMapping("/{tramiteId}/flujo-completo")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Camino completo del trámite",
        description = "Devuelve TODO el flujo del diagrama de la política con: nodos en orden, departamento, actividad, documentos requeridos y estado de cada nodo en este trámite específico."
    )
    public ResponseEntity<FlujoCompletoResponse> flujoCompleto(@PathVariable String tramiteId) {
        Tramite t = workflowEngine.buscarTramite(tramiteId);

        FlujoCompletoResponse resp = new FlujoCompletoResponse();
        resp.setTramiteId(t.getId());
        resp.setCodigo(t.getCodigo());
        resp.setNodoActualId(t.getNodoActualId());

        if (t.getPoliticaId() != null) {
            politicaRepository.findById(t.getPoliticaId())
                    .ifPresent(p -> resp.setPoliticaNombre(p.getNombre()));
        }

        // Resolver diagrama desde la política
        String diagramaId = t.getPoliticaId() != null
                ? politicaRepository.findById(t.getPoliticaId())
                        .map(p -> p.getDiagramaId()).orElse(null)
                : null;

        if (diagramaId == null) {
            resp.setNodos(java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }

        // Nodos del diagrama ordenados
        List<NodoDiagrama> nodos = nodoRepository.findAll().stream()
                .filter(n -> diagramaId.equals(n.getDiagramaId()))
                .sorted(java.util.Comparator.comparingInt(NodoDiagrama::getOrden))
                .toList();

        // Secciones del expediente del trámite, indexadas por nodoId
        Map<String, SeccionExpediente> seccionesPorNodo = new HashMap<>();
        if (t.getExpedienteId() != null) {
            seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId())
                    .forEach(s -> {
                        if (s.getNodoId() != null) {
                            seccionesPorNodo.put(s.getNodoId(), s);
                        }
                    });
        }

        // Última observación/motivo por nodo (del historial), para mostrarla en
        // el paso correspondiente del flujo (vista unificada).
        Map<String, String> obsPorNodo = new HashMap<>();
        historicoRepository.findByTramiteIdOrderByFechaCambioAsc(t.getId()).forEach(h -> {
            if (h.getMotivo() != null && !h.getMotivo().isBlank()) {
                String nid = h.getNodoNuevoId() != null ? h.getNodoNuevoId() : h.getNodoAnteriorId();
                if (nid != null) obsPorNodo.put(nid, h.getMotivo()); // orden asc → gana el último
            }
        });

        List<FlujoCompletoResponse.NodoFlujoDTO> nodosDto = nodos.stream()
                .map(n -> {
                    FlujoCompletoResponse.NodoFlujoDTO dto =
                            construirNodoFlujoDto(n, t, seccionesPorNodo.get(n.getId()));
                    dto.setObservacion(obsPorNodo.get(n.getId()));
                    return dto;
                })
                .toList();

        resp.setNodos(nodosDto);
        return ResponseEntity.ok(resp);
    }

    private FlujoCompletoResponse.NodoFlujoDTO construirNodoFlujoDto(NodoDiagrama nodo,
                                                                     Tramite tramite,
                                                                     SeccionExpediente seccion) {
        FlujoCompletoResponse.NodoFlujoDTO dto = new FlujoCompletoResponse.NodoFlujoDTO();
        dto.setNodoId(nodo.getId());
        dto.setNombre(nodo.getNombre());
        dto.setTipo(nodo.getTipo());
        dto.setOrden(nodo.getOrden());
        dto.setSwimlane(nodo.getSwimlane());
        dto.setEsActual(nodo.getId().equals(tramite.getNodoActualId()));

        if (nodo.getDepartamentoId() != null) {
            departamentoRepository.findById(nodo.getDepartamentoId()).ifPresent(d -> {
                dto.setDepartamentoCodigo(d.getCodigo());
                dto.setDepartamentoNombre(d.getNombre());
            });
        }

        // Nodo de decisión (if): exponemos la pregunta y a dónde lleva cada rama,
        // para que en la vista del flujo se muestre la pregunta (no "decisión") y
        // el usuario entienda por qué camino sigue el trámite según la respuesta.
        if ("decision".equals(nodo.getTipo())) {
            dto.setPregunta(nodo.getNombre());
            List<FlujoTransicion> ramas = flujoRepository.findByNodoOrigenId(nodo.getId());
            List<Map<String, Object>> opciones = ramas.stream().map(rama -> {
                Map<String, Object> op = new HashMap<>();
                op.put("valor", rama.getEtiqueta());
                op.put("etiqueta", capitalizarRama(rama.getEtiqueta()));
                nodoRepository.findById(rama.getNodoDestinoId())
                        .ifPresent(nd -> op.put("destinoNombre", nd.getNombre()));
                return op;
            }).toList();
            dto.setOpciones(opciones);
        }

        if (nodo.getActividadId() != null) {
            Actividad act = actividadRepository.findById(nodo.getActividadId()).orElse(null);
            if (act != null) {
                dto.setActividadId(act.getId());
                dto.setActividadNombre(act.getNombre());
                dto.setActividadDescripcion(act.getDescripcion());
                dto.setSlaHoras(act.getSlaHoras());
                dto.setSalidasPosibles(act.getSalidasPosibles());

                List<FlujoCompletoResponse.DocumentoRequeridoDTO> docs =
                        requisitoDocumentoService.requisitosDe(act).stream()
                                .map(req -> {
                                    Documento d = documentoRepository.findById(req.getDocumentoId()).orElse(null);
                                    if (d == null) return null;
                                    var rd = new FlujoCompletoResponse.DocumentoRequeridoDTO();
                                    rd.setId(d.getId());
                                    rd.setNombre(d.getNombre());
                                    rd.setDescripcion(d.getDescripcion());
                                    rd.setProveedor(req.getProveedor());
                                    rd.setObligatorio(req.isObligatorio());
                                    return rd;
                                })
                                .filter(java.util.Objects::nonNull)
                                .toList();
                dto.setDocumentosRequeridos(docs);
            }
        }

        if (seccion != null) {
            dto.setEstadoSeccion(seccion.getEstado());
            dto.setDocumentosObservados(seccion.getDocumentosObservados());
            dto.setFechaAsignacion(seccion.getFechaAsignacion());
            dto.setFechaCompletado(seccion.getFechaCompletado());
            if (seccion.getFuncionarioId() != null) {
                dto.setFuncionarioId(seccion.getFuncionarioId());
                usuarioRepository.findById(seccion.getFuncionarioId()).ifPresent(u -> {
                    String nombre = (u.getNombre() != null ? u.getNombre() : "")
                            + (u.getApellido() != null ? " " + u.getApellido() : "");
                    dto.setFuncionarioNombre(nombre.trim());
                });
            }
        } else {
            dto.setEstadoSeccion(EstadoSeccion.BLOQUEADA.getValor());
        }

        return dto;
    }

    private FlujoCompletoResponse.DocumentoRequeridoDTO toDocumentoRequeridoDto(Documento d) {
        FlujoCompletoResponse.DocumentoRequeridoDTO dto = new FlujoCompletoResponse.DocumentoRequeridoDTO();
        dto.setId(d.getId());
        dto.setNombre(d.getNombre());
        dto.setDescripcion(d.getDescripcion());
        return dto;
    }

    @GetMapping("/{tramiteId}/resolucion")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Descargar el documento de resolución del trámite",
        description = "Devuelve una URL firmada al documento de resolución que el trámite entregó al finalizar (lo que el cliente descarga)."
    )
    public ResponseEntity<Map<String, Object>> resolucion(@PathVariable String tramiteId,
                                                          Authentication auth,
                                                          HttpServletRequest httpRequest) {
        Tramite t = workflowEngine.buscarTramite(tramiteId);
        if (t.getDocumentoResolucionId() == null) {
            return ResponseEntity.notFound().build();
        }
        String rol = auth.getAuthorities().stream()
                .findFirst().map(a -> a.getAuthority()).orElse("");
        DocumentoArchivoService.PreviewData preview = documentoArchivoService.generarPreview(
                t.getDocumentoResolucionId(), auth.getName(), rol,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));

        Map<String, Object> resp = new HashMap<>();
        resp.put("url", preview.urlPreview());
        resp.put("mimeType", preview.mimeType());
        resp.put("expiraEn", preview.expiraEn());
        resp.put("tipoResolucion", t.getTipoResolucion());
        resp.put("fechaResolucion", t.getFechaResolucion());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/mis-tramites")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Listar mis trámites",
        description = "Devuelve los trámites del usuario autenticado (cliente) enriquecidos con politicaNombre, nodoActualNombre y progreso."
    )
    public ResponseEntity<List<Map<String, Object>>> misTramites(Authentication auth) {
        String clienteId = auth.getName();
        List<Map<String, Object>> tramites = workflowEngine.listarPorCliente(clienteId).stream()
                .map(this::resumenTramite)
                .toList();
        return ResponseEntity.ok(tramites);
    }

    @GetMapping("/mis-pendientes")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    @Operation(summary = "Bandeja de tramites pendientes del funcionario autenticado",
               description = "Parámetro ordenarPor=fecha|ia (CU-44 — IA delega al microservicio)")
    public ResponseEntity<List<Map<String, Object>>> misPendientes(
            @RequestParam(required = false, defaultValue = "fecha") String ordenarPor,
            Authentication auth) {
        String userId = auth.getName();
        boolean esAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));

        List<Tramite> activos = tramiteRepository.findTramitesActivos();
        List<Tramite> pendientes = activos.stream()
                .filter(t -> t.getNodoActualId() != null || t.estaEnParalelo())
                .filter(t -> esAdmin || userId.equals(t.getFuncionarioActualId()))
                // No mostrar en la bandeja los que están en COMPUERTA (esperando
                // documentos del cliente): aún no le toca al funcionario.
                .filter(t -> !esperandoDocumentosCliente(t))
                .toList();

        // CU-44 — reordenar con IA si se solicita y el proxy está disponible.
        if ("ia".equalsIgnoreCase(ordenarPor) && iaProxy != null) {
            try {
                pendientes = reordenarPorIa(pendientes, userId);
            } catch (Exception ex) {
                // Fallback: si el microservicio falla, devolver orden por fecha.
                org.slf4j.LoggerFactory.getLogger(WorkflowController.class)
                        .warn("[CU-44] reordenado IA falló, cayendo a orden por fecha: {}", ex.getMessage());
            }
        }

        List<Map<String, Object>> respuesta = pendientes.stream()
                .map(this::resumenTramite)
                .toList();
        return ResponseEntity.ok(respuesta);
    }

    /** ¿El paso ACTUAL del trámite está en COMPUERTA (esperando documentos del cliente)? */
    private boolean esperandoDocumentosCliente(Tramite t) {
        if (t.getExpedienteId() == null || t.getNodoActualId() == null) return false;
        return seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId()).stream()
                .anyMatch(s -> t.getNodoActualId().equals(s.getNodoId())
                        && EstadoSeccion.from(s.getEstado()) == EstadoSeccion.PENDIENTE_DOCUMENTOS);
    }

    private List<Tramite> reordenarPorIa(List<Tramite> tramites, String funcionarioId) {
        if (tramites.isEmpty()) return tramites;
        List<Map<String, Object>> payload = new java.util.ArrayList<>();
        for (Tramite t : tramites) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("tramite_id", t.getId());
            entry.put("politica_id", t.getPoliticaId());
            entry.put("fecha_inicio", t.getFechaInicio() != null ? t.getFechaInicio().toString() : null);
            entry.put("prioridad_manual", t.getPrioridad());
            entry.put("riesgo_demora", t.getRiesgoDemora());
            payload.add(entry);
        }

        List<Map<String, Object>> ordenIa = iaProxy.prioridades(funcionarioId, payload);

        // Construir mapa id → score para reordenar conservando los Tramite originales.
        Map<String, Double> scoreById = new HashMap<>();
        Map<String, String> motivoById = new HashMap<>();
        for (Map<String, Object> r : ordenIa) {
            String id = String.valueOf(r.get("tramite_id"));
            Object s = r.get("score");
            scoreById.put(id, s instanceof Number n ? n.doubleValue() : 0d);
            motivoById.put(id, r.get("motivo") != null ? r.get("motivo").toString() : null);
        }

        // Orden descendente por score; si IA no devolvió score para alguno, va al final.
        return tramites.stream()
                .sorted((a, b) -> Double.compare(
                        scoreById.getOrDefault(b.getId(), -1d),
                        scoreById.getOrDefault(a.getId(), -1d)))
                .toList();
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
        m.put("fechaCierreReal", t.getFechaCierreReal());
        m.put("nodoActualId", t.getNodoActualId());
        m.put("documentoResolucionId", t.getDocumentoResolucionId());
        m.put("tipoResolucion", t.getTipoResolucion());
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
                        .filter(s -> EstadoSeccion.esDerivada(s.getEstado()))
                        .count();
                progreso = (int) Math.round(100.0 * completadas / secciones.size());
            }
            m.put("progreso", progreso);
            // Estado de la sección del nodo actual: permite a la app distinguir en la
            // lista la COMPUERTA ('Pendiente de documentos', el trámite avanzó) del
            // estado normal, sin abrir cada trámite.
            if (t.getNodoActualId() != null) {
                secciones.stream()
                        .filter(s -> t.getNodoActualId().equals(s.getNodoId()))
                        .findFirst()
                        .ifPresent(s -> m.put("estadoSeccion", s.getEstado()));
            }
        } else {
            m.put("progreso", 0);
        }
        return m;
    }
}
