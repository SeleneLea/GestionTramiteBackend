package com.example.demo.services;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.IniciarTramiteRequest;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkflowEngineService {

    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private ExpedienteDigitalRepository expedienteRepository;
    @Autowired private SeccionExpedienteRepository seccionRepository;
    @Autowired private EstadoHistoricoRepository estadoHistoricoRepository;
    @Autowired private MetricaYCuelloService metricaYCuelloService;
    @Autowired private NotificacionService notificacionService;
    @Autowired private TrazabilidadService trazabilidadService;
    @Autowired private com.example.demo.repositories.UsuarioRepository usuarioRepository;
    @Autowired private MongoTemplate mongoTemplate;

    // ─────────────────────────────────────────────────────────────────────
    // INICIAR TRÁMITE
    // ─────────────────────────────────────────────────────────────────────

    public Tramite iniciarTramite(IniciarTramiteRequest req) {
        // 1. Validar política activa
        PoliticaNegocio politica = politicaRepository.findById(req.getPoliticaId())
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        if (!"activa".equals(politica.getEstado())) {
            throw new IllegalArgumentException("La política debe estar activa para iniciar trámites");
        }
        if (politica.getDiagramaId() == null) {
            throw new IllegalArgumentException("La política no tiene un diagrama de flujo asignado");
        }

        // 2. Cargar el diagrama y sus nodos
        DiagramaWorkflow diagrama = diagramaRepository.findById(politica.getDiagramaId())
                .orElseThrow(() -> new IllegalArgumentException("Diagrama del flujo no encontrado"));

        List<NodoDiagrama> todosLosNodos = nodoRepository.findByDiagramaId(diagrama.getId());

        // 3. Encontrar el nodo INICIO
        NodoDiagrama nodoInicio = todosLosNodos.stream()
                .filter(n -> "inicio".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El diagrama no tiene nodo de inicio"));

        // 4. Crear el trámite
        Tramite tramite = new Tramite();
        tramite.setCodigo(generarCodigo());
        tramite.setClienteId(req.getClienteId());
        tramite.setPoliticaId(req.getPoliticaId());
        tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
        tramite.setPrioridad(req.getPrioridad() > 0 ? req.getPrioridad() : 3);
        tramite.setFechaInicio(LocalDateTime.now());
        tramite = tramiteRepository.save(tramite);

        // 5. Crear el expediente digital con una sección por cada nodo actividad
        ExpedienteDigital expediente = new ExpedienteDigital();
        expediente.setTramiteId(tramite.getId());
        expediente.setFechaCreacion(LocalDateTime.now());
        expediente.setUltimaActualizacion(LocalDateTime.now());
        expediente = expedienteRepository.save(expediente);

        List<String> seccionesIds = new ArrayList<>();
        List<NodoDiagrama> nodosActividad = todosLosNodos.stream()
                .filter(n -> "actividad".equals(n.getTipo()))
                .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
                .toList();

        for (NodoDiagrama nodo : nodosActividad) {
            SeccionExpediente seccion = new SeccionExpediente();
            seccion.setExpedienteId(expediente.getId());
            seccion.setNodoId(nodo.getId());
            seccion.setDepartamentoId(nodo.getDepartamentoId());
            seccion.setOrdenSeccion(nodo.getOrden());
            seccion.setEstado(EstadoSeccion.BLOQUEADA.getValor());   // todas bloqueadas al inicio
            seccion = seccionRepository.save(seccion);
            seccionesIds.add(seccion.getId());
        }

        expediente.setSeccionesIds(seccionesIds);
        expedienteRepository.save(expediente);

        tramite.setExpedienteId(expediente.getId());
        tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
        tramite = tramiteRepository.save(tramite);

        // 6. Avanzar desde INICIO al primer nodo (el motor toma control)
        tramite = avanzarDesde(tramite, nodoInicio, null, todosLosNodos);

        registrarHistorico(tramite.getId(), null, EstadoTramite.EN_CURSO.getValor(), null, tramite.getNodoActualId(), req.getClienteId(), "Trámite iniciado");
        trazabilidadService.registrar(tramite.getId(), req.getClienteId(), "iniciar",
                tramite.getNodoActualId(), Map.of("politicaId", req.getPoliticaId()));
        return tramiteRepository.save(tramite);
    }

    // ─────────────────────────────────────────────────────────────────────
    // COMPLETAR NODO ACTUAL
    // ─────────────────────────────────────────────────────────────────────

    public Tramite completarNodo(String tramiteId, CompletarNodoRequest req) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El trámite ya está cerrado");
        }

        // Determinar qué nodo está completando el funcionario.
        // NOTA: el control fino de ownership/departamento está deliberadamente
        // deshabilitado (cualquier funcionario puede completar el nodo activo),
        // coherente con el alcance no comercial del proyecto (RBAC fino diferido).
        String nodoIdActivo = resolverNodoActivo(tramite, req.getFuncionarioId());

        // Marcar la sección del nodo como completada
        // Primero buscar por nodoId. Si no hay match (datos de seed con nodoIds
        // desactualizados), caer a buscar por departamentoId del nodo activo.
        List<SeccionExpediente> seccionesExp = seccionRepository
                .findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId());

        final Tramite tramiteRef = tramite;
        final String funcionarioId = req.getFuncionarioId();
        SeccionExpediente seccion = seccionesExp.stream()
                .filter(s -> nodoIdActivo.equals(s.getNodoId()))
                .findFirst()
                .orElseGet(() -> {
                    NodoDiagrama nodoAct = nodoRepository.findById(nodoIdActivo).orElse(null);
                    String deptoActivo = nodoAct != null ? nodoAct.getDepartamentoId() : null;

                    // Fallback 1: matchear por departamento del nodo activo
                    if (deptoActivo != null) {
                        Optional<SeccionExpediente> match = seccionesExp.stream()
                                .filter(s -> deptoActivo.equals(s.getDepartamentoId())
                                        && !EstadoSeccion.esDerivada(s.getEstado()))
                                .findFirst();
                        if (match.isPresent()) return match.get();
                    }

                    // Fallback 2: crear la sección al vuelo si nunca existió
                    // (data semilla incompleta o tramite avanzo a un nodo nuevo).
                    SeccionExpediente nueva = new SeccionExpediente();
                    nueva.setExpedienteId(tramiteRef.getExpedienteId());
                    nueva.setNodoId(nodoIdActivo);
                    nueva.setDepartamentoId(deptoActivo);
                    nueva.setOrdenSeccion(seccionesExp.size() + 1);
                    nueva.setEstado(EstadoSeccion.EN_EJECUCION.getValor());
                    nueva.setFechaAsignacion(LocalDateTime.now());
                    nueva.setFuncionarioId(funcionarioId);
                    return seccionRepository.save(nueva);
                });

        // Self-heal: alinear nodoId de la sección con el nodo realmente activo
        if (!nodoIdActivo.equals(seccion.getNodoId())) {
            seccion.setNodoId(nodoIdActivo);
        }
        seccion.setEstado(EstadoSeccion.DERIVADA.getValor());
        seccion.setFechaCompletado(LocalDateTime.now());
        seccionRepository.save(seccion);

        // Cargar todos los nodos del diagrama para el motor
        PoliticaNegocio politica = politicaRepository.findById(tramite.getPoliticaId()).orElseThrow();
        List<NodoDiagrama> todosLosNodos = nodoRepository.findByDiagramaId(politica.getDiagramaId());

        NodoDiagrama nodoActual = nodoRepository.findById(nodoIdActivo).orElseThrow();

        // CU-24: al completar una actividad, registrar metrica de tiempo vs SLA.
        if ("actividad".equals(nodoActual.getTipo())) {
            metricaYCuelloService.registrarMetricaActividad(
                tramite.getId(),
                nodoActual.getActividadId(),
                nodoActual.getDepartamentoId(),
                seccion.getFechaAsignacion(),
                seccion.getFechaCompletado()
            );
        }

        String estadoAnterior = tramite.getEstadoActual();

        // Si es nodo paralelo: marcar rama y verificar si se completó el join
        if (tramite.estaEnParalelo()) {
            tramite.getNodosParalellosActivos().remove(nodoIdActivo);

            if (!tramite.getNodosParalellosActivos().isEmpty()) {
                // Aún hay ramas paralelas activas — esperar
                Tramite guardado = tramiteRepository.save(tramite);
                // Cada rama paralela completada queda en histórico y trazabilidad,
                // aunque el trámite global todavía no cambie de estado.
                registrarHistorico(tramiteId, estadoAnterior, guardado.getEstadoActual(),
                        nodoIdActivo, null, req.getFuncionarioId(), req.getNotas());
                trazabilidadService.registrar(tramiteId, req.getFuncionarioId(), "completar_rama_paralela",
                        nodoIdActivo, Map.of("estadoActual", tramite.getEstadoActual()));
                return guardado;
            }

            // Todas las ramas completadas → buscar el JOIN y avanzar desde él
            NodoDiagrama nodoJoin = encontrarJoin(nodoActual, todosLosNodos);
            tramite = avanzarDesde(tramite, nodoJoin, req.getDecision(), todosLosNodos);
        } else {
            tramite = avanzarDesde(tramite, nodoActual, req.getDecision(), todosLosNodos);
        }

        registrarHistorico(tramiteId, estadoAnterior, tramite.getEstadoActual(),
                nodoIdActivo, tramite.getNodoActualId(), req.getFuncionarioId(), req.getNotas());
        trazabilidadService.registrar(tramiteId, req.getFuncionarioId(), "completar_nodo",
                nodoIdActivo, Map.of(
                        "estadoAnterior", estadoAnterior,
                        "estadoNuevo", tramite.getEstadoActual()
                ));

        return tramiteRepository.save(tramite);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ACEPTAR / RECEPCIONAR EL NODO ACTUAL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * El responsable acepta el trámite que llegó a su bandeja: la sección activa
     * pasa de {@code Pendiente de recepción} a {@code En ejecución} y queda
     * formalmente a su cargo. Idempotente si ya estaba En ejecución.
     */
    public Tramite aceptarTramite(String tramiteId, String funcionarioId) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El trámite ya está cerrado");
        }

        String nodoIdActivo = resolverNodoActivo(tramite, funcionarioId);

        seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId())
                .stream()
                .filter(s -> nodoIdActivo.equals(s.getNodoId()))
                .findFirst()
                .ifPresent(s -> {
                    s.setEstado(EstadoSeccion.EN_EJECUCION.getValor());
                    s.setFuncionarioId(funcionarioId);
                    if (s.getFechaAsignacion() == null) {
                        s.setFechaAsignacion(LocalDateTime.now());
                    }
                    seccionRepository.save(s);
                });

        tramite.setFuncionarioActualId(funcionarioId);
        tramite = tramiteRepository.save(tramite);

        trazabilidadService.registrar(tramiteId, funcionarioId, "aceptar", nodoIdActivo, Map.of());
        return tramite;
    }

    // ─────────────────────────────────────────────────────────────────────
    // MOTOR: AVANZAR DESDE UN NODO
    // ─────────────────────────────────────────────────────────────────────

    private Tramite avanzarDesde(Tramite tramite, NodoDiagrama nodoOrigen,
                                  String decision, List<NodoDiagrama> todosLosNodos) {

        List<FlujoTransicion> transiciones = flujoRepository.findByNodoOrigenId(nodoOrigen.getId());

        if (transiciones.isEmpty()) {
            // Sin salida → cerrar trámite
            return cerrarTramite(tramite, EstadoTramite.APROBADO.getValor());
        }

        // Seleccionar la transición según el tipo de nodo origen
        return switch (nodoOrigen.getTipo()) {

            case "inicio", "join", "actividad" -> {
                // Un solo camino — tomar la primera transición
                FlujoTransicion transicion = transiciones.get(0);
                NodoDiagrama nodoSiguiente = encontrarNodoPorId(transicion.getNodoDestinoId(), todosLosNodos);
                yield procesarNodo(tramite, nodoSiguiente, decision, todosLosNodos);
            }

            case "decision" -> {
                // Elegir transición según la decisión del funcionario ("si" o "no")
                String decisionNormalizada = (decision != null && !decision.isBlank() ? decision.trim() : "si").toLowerCase();
                FlujoTransicion transicion = transiciones.stream()
                        .filter(t -> decisionNormalizada.equals(
                                t.getEtiqueta() != null ? t.getEtiqueta().toLowerCase() : ""))
                    .findFirst()
                    .orElse(null);

                if (transicion == null) {
                    throw new IllegalArgumentException("La respuesta '" + decisionNormalizada
                        + "' no corresponde a ninguna rama del nodo de decision (se espera 'si' o 'no').");
                }

                NodoDiagrama nodoSiguiente = encontrarNodoPorId(transicion.getNodoDestinoId(), todosLosNodos);
                // El nodo decisión solo RUTEA; no cambia el estado global del trámite.
                // El estado lo fija el nodo destino (procesarNodo): actividad → En curso,
                // fin → Aprobado. "Observado" global es exclusivo de la acción Observar.
                yield procesarNodo(tramite, nodoSiguiente, decision, todosLosNodos);
            }

            case "fork" -> {
                // Activar TODAS las ramas en paralelo
                List<String> nodosParalelos = new ArrayList<>();
                for (FlujoTransicion t : transiciones) {
                    NodoDiagrama rama = encontrarNodoPorId(t.getNodoDestinoId(), todosLosNodos);
                    desbloquearSeccion(tramite.getExpedienteId(), rama.getId(),
                            elegirFuncionarioDelDepto(rama.getDepartamentoId()));
                    nodosParalelos.add(rama.getId());
                }
                tramite.setNodosParalellosActivos(nodosParalelos);
                tramite.setNodoActualId(null);   // sin nodo único — hay varios
                tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
                yield tramite;
            }

                default -> throw new IllegalStateException(
                    "Excepcion de Regla de Negocio: tipo de nodo no soportado '" + nodoOrigen.getTipo() + "'");
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // MOTOR: PROCESAR UN NODO DESTINO
    // ─────────────────────────────────────────────────────────────────────

    private Tramite procesarNodo(Tramite tramite, NodoDiagrama nodo,
                                  String decision, List<NodoDiagrama> todosLosNodos) {
        return switch (nodo.getTipo()) {
            case "actividad" -> {
                // Resolver el funcionario del departamento destino (auto-derivación)
                String nuevoFuncionarioId = elegirFuncionarioDelDepto(nodo.getDepartamentoId());

                // Desbloquear la sección de este nodo y asignarle el funcionario
                desbloquearSeccion(tramite.getExpedienteId(), nodo.getId(), nuevoFuncionarioId);
                tramite.setNodoActualId(nodo.getId());
                tramite.setNodosParalellosActivos(new ArrayList<>());
                tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());

                tramite.setFuncionarioActualId(nuevoFuncionarioId);

                if (nuevoFuncionarioId != null) {
                    // CU-11: aviso al funcionario receptor de la nueva etapa.
                    notificacionService.crearNotificacion(
                            nuevoFuncionarioId,
                            tramite.getId(),
                            "asignacion",
                            "Tramite asignado a tu bandeja",
                            "El tramite " + tramite.getCodigo() + " avanzo a la etapa: " + nodo.getNombre(),
                            "web"
                    );
                }

                // CU-28: aviso al cliente de avance de etapa.
                notificacionService.crearNotificacion(
                        tramite.getClienteId(),
                        tramite.getId(),
                        "cambio_estado",
                        "Tu tramite avanzo de etapa",
                        "Tu tramite " + tramite.getCodigo() + " esta ahora en: " + nodo.getNombre(),
                        "web"
                );
                yield tramite;
            }
            // Nodos de control: el motor los atraviesa automáticamente sin parar
            case "decision", "fork", "join", "fin" ->
                    avanzarDesde(tramite, nodo, decision, todosLosNodos);

                default -> throw new IllegalStateException(
                    "Excepcion de Regla de Negocio: tipo de nodo destino no soportado '" + nodo.getTipo() + "'");
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private void desbloquearSeccion(String expedienteId, String nodoId, String funcionarioId) {
        seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(expedienteId)
                .stream()
                .filter(s -> {
                    if (!nodoId.equals(s.getNodoId())) return false;
                    EstadoSeccion e = EstadoSeccion.from(s.getEstado());
                    // Reactivar tanto nodos futuros (BLOQUEADA) como re-trabajo por
                    // decisión 'no'→fork: secciones ya DERIVADA u OBSERVADO.
                    // NO se pisan secciones EN_EJECUCION/PENDIENTE_RECEPCION (trabajo en curso).
                    return e == EstadoSeccion.BLOQUEADA
                            || e == EstadoSeccion.DERIVADA
                            || e == EstadoSeccion.OBSERVADO;
                })
                .findFirst()
                .ifPresent(s -> {
                    // Llega a la bandeja del responsable: queda Pendiente de recepción
                    // hasta que lo acepte (aceptarTramite -> En ejecución).
                    s.setEstado(EstadoSeccion.PENDIENTE_RECEPCION.getValor());
                    s.setFechaAsignacion(LocalDateTime.now());
                    // Re-trabajo: limpiar la fecha de cierre previa para no arrastrar
                    // la finalización anterior de la sección reactivada.
                    s.setFechaCompletado(null);
                    // Poblar el funcionario asignado ya en el desbloqueo, para que el
                    // guard de ownership del front no bloquee antes de "aceptar".
                    if (funcionarioId != null) {
                        s.setFuncionarioId(funcionarioId);
                    }
                    seccionRepository.save(s);
                });
    }

    private Tramite cerrarTramite(Tramite tramite, String estadoFinal) {
        tramite.setEstadoActual(estadoFinal);
        tramite.setNodoActualId(null);
        tramite.setNodosParalellosActivos(new ArrayList<>());
        tramite.setFuncionarioActualId(null);
        tramite.setFechaCierreReal(LocalDateTime.now());

        // CU-28: push al cliente al cerrar el tramite.
        String titulo = EstadoTramite.APROBADO.getValor().equals(estadoFinal)
            ? "Tu tramite fue aprobado"
            : "Tu tramite fue rechazado";
        String mensaje = "El tramite " + tramite.getCodigo() + " ha sido " + estadoFinal.toLowerCase() + ".";
        notificacionService.crearNotificacion(
            tramite.getClienteId(),
            tramite.getId(),
            "cambio_estado",
            titulo,
            mensaje,
            "push"
        );
        return tramite;
    }

    private NodoDiagrama encontrarNodoPorId(String id, List<NodoDiagrama> nodos) {
        return nodos.stream()
                .filter(n -> id.equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nodo no encontrado: " + id));
    }

    private NodoDiagrama encontrarJoin(NodoDiagrama nodoRama, List<NodoDiagrama> todosLosNodos) {
        // Busca el primer nodo JOIN en las transiciones salientes del nodo rama
        return flujoRepository.findByNodoOrigenId(nodoRama.getId()).stream()
                .map(t -> encontrarNodoPorId(t.getNodoDestinoId(), todosLosNodos))
                .filter(n -> "join".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontró nodo JOIN tras la rama paralela"));
    }

    private String elegirFuncionarioDelDepto(String departamentoId) {
        if (departamentoId == null) return null;
        return usuarioRepository.findByTipo("funcionario").stream()
                .filter(u -> u.getDepartamentosIds() != null
                        && u.getDepartamentosIds().contains(departamentoId)
                        && u.isActivo())
                .findFirst()
                .map(u -> u.getId())
                .orElse(null);
    }

    private String resolverNodoActivo(Tramite tramite, String funcionarioId) {
        if (tramite.estaEnParalelo()) {
            // En paralelo: priorizar el nodo cuyo departamento esté entre los del
            // funcionario; si no hay match (o el funcionario no tiene departamentos),
            // caer al primer nodo activo (mantiene compatibilidad previa).
            List<String> deptosFuncionario = usuarioRepository.findById(funcionarioId)
                    .map(Usuario::getDepartamentosIds)
                    .orElse(null);

            if (deptosFuncionario != null && !deptosFuncionario.isEmpty()) {
                Optional<String> propio = tramite.getNodosParalellosActivos().stream()
                        .filter(nodoId -> {
                            NodoDiagrama n = nodoRepository.findById(nodoId).orElse(null);
                            return n != null && n.getDepartamentoId() != null
                                    && deptosFuncionario.contains(n.getDepartamentoId());
                        })
                        .findFirst();
                if (propio.isPresent()) return propio.get();
            }

            return tramite.getNodosParalellosActivos().stream()
                    .filter(nodoId -> nodoRepository.findById(nodoId).isPresent())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No hay nodo paralelo activo para el funcionario"));
        }
        if (tramite.getNodoActualId() == null) {
            throw new IllegalArgumentException("El tramite no tiene nodo activo asignado");
        }
        return tramite.getNodoActualId();
    }

    private void registrarHistorico(String tramiteId, String estadoAnterior, String estadoNuevo,
                                     String nodoAnteriorId, String nodoNuevoId,
                                     String actorId, String motivo) {
        EstadoHistorico h = new EstadoHistorico();
        h.setTramiteId(tramiteId);
        h.setEstadoAnterior(estadoAnterior);
        h.setEstadoNuevo(estadoNuevo);
        h.setNodoAnteriorId(nodoAnteriorId);
        h.setNodoNuevoId(nodoNuevoId);
        h.setActorId(actorId);
        h.setMotivo(motivo);
        h.setFechaCambio(LocalDateTime.now());
        estadoHistoricoRepository.save(h);
    }

    private String generarCodigo() {
        int year = LocalDateTime.now().getYear();
        Secuencia sec = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is("tramite-" + year)),
                new Update().inc("seq", 1),
                new FindAndModifyOptions().returnNew(true).upsert(true),
                Secuencia.class,
                "secuencias");
        return String.format("TR-%d-%05d", year, sec.getSeq());
    }

    public Tramite buscarTramite(String tramiteId) {
        return tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
    }

    /**
     * Listar todos los trámites del cliente (usuario autenticado)
     * ordenados por fecha de inicio en forma descendente (más recientes primero)
     */
    public List<Tramite> listarPorCliente(String clienteId) {
        return tramiteRepository.findByClienteIdOrderByFechaInicioDesc(clienteId);
    }
}
