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
    @Autowired private RepositorioDocumentalService repositorioDocumentalService;
    @Autowired private RequisitoDocumentoService requisitoDocumentoService;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(WorkflowEngineService.class);

    public Tramite iniciarTramite(IniciarTramiteRequest req) {
        PoliticaNegocio politica = politicaRepository.findById(req.getPoliticaId())
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        if (!"activa".equals(politica.getEstado())) {
            throw new IllegalArgumentException("La política debe estar activa para iniciar trámites");
        }
        if (politica.getDiagramaId() == null) {
            throw new IllegalArgumentException("La política no tiene un diagrama de flujo asignado");
        }

        DiagramaWorkflow diagrama = diagramaRepository.findById(politica.getDiagramaId())
                .orElseThrow(() -> new IllegalArgumentException("Diagrama del flujo no encontrado"));

        List<NodoDiagrama> todosLosNodos = nodoRepository.findByDiagramaId(diagrama.getId());

        NodoDiagrama nodoInicio = todosLosNodos.stream()
                .filter(n -> "inicio".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El diagrama no tiene nodo de inicio"));

        Tramite tramite = new Tramite();
        tramite.setCodigo(generarCodigo());
        tramite.setClienteId(req.getClienteId());
        tramite.setPoliticaId(req.getPoliticaId());
        tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
        tramite.setPrioridad(req.getPrioridad() > 0 ? req.getPrioridad() : 3);
        tramite.setFechaInicio(LocalDateTime.now());
        tramite = tramiteRepository.save(tramite);

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
            seccion.setEstado(EstadoSeccion.BLOQUEADA.getValor());
            seccion = seccionRepository.save(seccion);
            seccionesIds.add(seccion.getId());
        }

        expediente.setSeccionesIds(seccionesIds);
        expedienteRepository.save(expediente);

        tramite.setExpedienteId(expediente.getId());
        tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
        tramite = tramiteRepository.save(tramite);

        try {
            RepositorioDocumental repo =
                    repositorioDocumentalService.crearAlIniciarTramite(tramite.getId(), tramite.getPoliticaId());
            tramite.setRepositorioId(repo.getId());
        } catch (Exception ex) {
            log.warn("[CU-32] No se pudo crear el repositorio para el trámite {}: {}",
                    tramite.getId(), ex.getMessage());
        }

        tramite = avanzarDesde(tramite, nodoInicio, null, todosLosNodos);

        registrarHistorico(tramite.getId(), null, EstadoTramite.EN_CURSO.getValor(), null, tramite.getNodoActualId(), req.getClienteId(), "Trámite iniciado");
        trazabilidadService.registrar(tramite.getId(), req.getClienteId(), "iniciar",
                tramite.getNodoActualId(), Map.of("politicaId", req.getPoliticaId()));
        return tramiteRepository.save(tramite);
    }

    public Tramite completarNodo(String tramiteId, CompletarNodoRequest req) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El trámite ya está cerrado");
        }

        String solicitado = req.getNodoId();
        boolean solicitadoValido = solicitado != null && !solicitado.isBlank()
                && (solicitado.equals(tramite.getNodoActualId())
                    || (tramite.estaEnParalelo()
                        && tramite.getNodosParalellosActivos().contains(solicitado)));
        String nodoIdActivo = solicitadoValido
                ? solicitado
                : resolverNodoActivo(tramite, req.getFuncionarioId());

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

                    if (deptoActivo != null) {
                        Optional<SeccionExpediente> match = seccionesExp.stream()
                                .filter(s -> deptoActivo.equals(s.getDepartamentoId())
                                        && !EstadoSeccion.esDerivada(s.getEstado()))
                                .findFirst();
                        if (match.isPresent()) return match.get();
                    }

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

        if (!nodoIdActivo.equals(seccion.getNodoId())) {
            seccion.setNodoId(nodoIdActivo);
        }
        seccion.setEstado(EstadoSeccion.DERIVADA.getValor());
        seccion.setFechaCompletado(LocalDateTime.now());
        seccionRepository.save(seccion);

        PoliticaNegocio politica = politicaRepository.findById(tramite.getPoliticaId()).orElseThrow();
        List<NodoDiagrama> todosLosNodos = nodoRepository.findByDiagramaId(politica.getDiagramaId());

        NodoDiagrama nodoActual = nodoRepository.findById(nodoIdActivo).orElseThrow();

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

        if (tramite.estaEnParalelo()) {
            tramite.getNodosParalellosActivos().remove(nodoIdActivo);

            if (!tramite.getNodosParalellosActivos().isEmpty()) {
                Tramite guardado = tramiteRepository.save(tramite);
                registrarHistorico(tramiteId, estadoAnterior, guardado.getEstadoActual(),
                        nodoIdActivo, null, req.getFuncionarioId(), req.getNotas());
                trazabilidadService.registrar(tramiteId, req.getFuncionarioId(), "completar_rama_paralela",
                        nodoIdActivo, Map.of("estadoActual", tramite.getEstadoActual()));
                return guardado;
            }

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

    private Tramite avanzarDesde(Tramite tramite, NodoDiagrama nodoOrigen,
                                  String decision, List<NodoDiagrama> todosLosNodos) {

        List<FlujoTransicion> transiciones = flujoRepository.findByNodoOrigenId(nodoOrigen.getId());

        if (transiciones.isEmpty()) {
            return cerrarTramite(tramite, EstadoTramite.APROBADO.getValor());
        }

        return switch (nodoOrigen.getTipo()) {

            case "inicio", "join", "actividad" -> {
                FlujoTransicion transicion = transiciones.get(0);
                NodoDiagrama nodoSiguiente = encontrarNodoPorId(transicion.getNodoDestinoId(), todosLosNodos);
                yield procesarNodo(tramite, nodoSiguiente, decision, todosLosNodos);
            }

            case "decision" -> {
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
                yield procesarNodo(tramite, nodoSiguiente, decision, todosLosNodos);
            }

            case "fork" -> {
                List<String> nodosParalelos = new ArrayList<>();
                boolean algunaRamaEsperaDocs = false;
                for (FlujoTransicion t : transiciones) {
                    NodoDiagrama rama = encontrarNodoPorId(t.getNodoDestinoId(), todosLosNodos);
                    boolean faltanDocsRama = requisitoDocumentoService
                            .faltanObligatoriosCliente(tramite.getId(), rama.getActividadId());
                    String estadoRama = faltanDocsRama
                            ? EstadoSeccion.PENDIENTE_DOCUMENTOS.getValor()
                            : EstadoSeccion.PENDIENTE_RECEPCION.getValor();
                    desbloquearSeccion(tramite.getExpedienteId(), rama.getId(),
                            elegirFuncionarioDelDepto(rama.getDepartamentoId()), estadoRama);
                    if (faltanDocsRama) algunaRamaEsperaDocs = true;
                    nodosParalelos.add(rama.getId());
                }
                if (algunaRamaEsperaDocs) {
                    notificacionService.crearNotificacion(
                            tramite.getClienteId(), tramite.getId(), "documentos_pendientes",
                            "Faltan documentos para continuar",
                            "Tu tramite " + tramite.getCodigo() + " necesita que subas documentos para continuar.",
                            "web");
                }
                tramite.setNodosParalellosActivos(nodosParalelos);
                tramite.setNodoActualId(null);
                tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
                yield tramite;
            }

                default -> throw new IllegalStateException(
                    "Excepcion de Regla de Negocio: tipo de nodo no soportado '" + nodoOrigen.getTipo() + "'");
        };
    }

    private Tramite procesarNodo(Tramite tramite, NodoDiagrama nodo,
                                  String decision, List<NodoDiagrama> todosLosNodos) {
        return switch (nodo.getTipo()) {
            case "actividad" -> {
                String nuevoFuncionarioId = elegirFuncionarioDelDepto(nodo.getDepartamentoId());

                boolean faltanDocs = requisitoDocumentoService
                        .faltanObligatoriosCliente(tramite.getId(), nodo.getActividadId());
                String estadoDestino = faltanDocs
                        ? EstadoSeccion.PENDIENTE_DOCUMENTOS.getValor()
                        : EstadoSeccion.PENDIENTE_RECEPCION.getValor();

                desbloquearSeccion(tramite.getExpedienteId(), nodo.getId(), nuevoFuncionarioId, estadoDestino);
                tramite.setNodoActualId(nodo.getId());
                tramite.setNodosParalellosActivos(new ArrayList<>());
                tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
                tramite.setFuncionarioActualId(nuevoFuncionarioId);

                if (faltanDocs) {
                    notificacionService.crearNotificacion(
                            tramite.getClienteId(),
                            tramite.getId(),
                            "documentos_pendientes",
                            "Faltan documentos para continuar",
                            "Tu tramite " + tramite.getCodigo() + " necesita que subas documentos en: " + nodo.getNombre(),
                            "web"
                    );
                } else {
                    if (nuevoFuncionarioId != null) {
                        notificacionService.crearNotificacion(
                                nuevoFuncionarioId,
                                tramite.getId(),
                                "asignacion",
                                "Tramite asignado a tu bandeja",
                                "El tramite " + tramite.getCodigo() + " avanzo a la etapa: " + nodo.getNombre(),
                                "web"
                        );
                    }
                    notificacionService.crearNotificacion(
                            tramite.getClienteId(),
                            tramite.getId(),
                            "cambio_estado",
                            "Tu tramite avanzo de etapa",
                            "Tu tramite " + tramite.getCodigo() + " esta ahora en: " + nodo.getNombre(),
                            "web"
                    );
                }
                yield tramite;
            }
            case "decision", "fork", "join", "fin" ->
                    avanzarDesde(tramite, nodo, decision, todosLosNodos);

                default -> throw new IllegalStateException(
                    "Excepcion de Regla de Negocio: tipo de nodo destino no soportado '" + nodo.getTipo() + "'");
        };
    }

    private void desbloquearSeccion(String expedienteId, String nodoId, String funcionarioId,
                                    String estadoDestino) {
        seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(expedienteId)
                .stream()
                .filter(s -> {
                    if (!nodoId.equals(s.getNodoId())) return false;
                    EstadoSeccion e = EstadoSeccion.from(s.getEstado());
                    return e == EstadoSeccion.BLOQUEADA
                            || e == EstadoSeccion.DERIVADA
                            || e == EstadoSeccion.OBSERVADO;
                })
                .findFirst()
                .ifPresent(s -> {
                    s.setEstado(estadoDestino);
                    s.setFechaAsignacion(LocalDateTime.now());
                    s.setFechaCompletado(null);
                    if (funcionarioId != null) {
                        s.setFuncionarioId(funcionarioId);
                    }
                    seccionRepository.save(s);
                });
    }

    public void reanudarPorDocumentos(String tramiteId, String actividadId) {
        if (tramiteId == null || actividadId == null) return;
        if (requisitoDocumentoService.faltanObligatoriosCliente(tramiteId, actividadId)) return;

        Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
        if (tramite == null || tramite.getExpedienteId() == null) return;

        seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId()).stream()
                .filter(s -> EstadoSeccion.from(s.getEstado()) == EstadoSeccion.PENDIENTE_DOCUMENTOS)
                .filter(s -> {
                    NodoDiagrama nodo = nodoRepository.findById(s.getNodoId()).orElse(null);
                    return (nodo != null && actividadId.equals(nodo.getActividadId()))
                            || s.getNodoId().equals(tramite.getNodoActualId());
                })
                .findFirst()
                .ifPresent(s -> {
                    s.setEstado(EstadoSeccion.PENDIENTE_RECEPCION.getValor());
                    s.setFechaAsignacion(LocalDateTime.now());
                    seccionRepository.save(s);
                    log.info("[compuerta] documentos completos: tramite {} reanudado a PENDIENTE_RECEPCION",
                            tramite.getCodigo());
                    String funcId = s.getFuncionarioId() != null
                            ? s.getFuncionarioId() : tramite.getFuncionarioActualId();
                    if (funcId != null) {
                        notificacionService.crearNotificacion(
                                funcId, tramiteId, "asignacion",
                                "Tramite listo para recibir",
                                "El cliente completo los documentos del tramite " + tramite.getCodigo() + ".",
                                "web");
                    }
                });
    }

    public void limpiarDocumentoObservado(String tramiteId, String corrigeDocumentoId) {
        if (tramiteId == null || corrigeDocumentoId == null || corrigeDocumentoId.isBlank()) return;
        Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
        if (tramite == null || tramite.getExpedienteId() == null) return;
        String id = corrigeDocumentoId.trim();
        seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId()).stream()
                .filter(s -> s.getDocumentosObservados() != null && s.getDocumentosObservados().contains(id))
                .findFirst()
                .ifPresent(s -> {
                    s.getDocumentosObservados().remove(id);
                    seccionRepository.save(s);
                    log.info("[observado] documento {} corregido en tramite {}", id, tramite.getCodigo());
                });
    }

    private Tramite cerrarTramite(Tramite tramite, String estadoFinal) {
        tramite.setEstadoActual(estadoFinal);
        tramite.setNodoActualId(null);
        tramite.setNodosParalellosActivos(new ArrayList<>());
        tramite.setFuncionarioActualId(null);
        tramite.setFechaCierreReal(LocalDateTime.now());

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

    public List<Tramite> listarPorCliente(String clienteId) {
        return tramiteRepository.findByClienteIdOrderByFechaInicioDesc(clienteId);
    }
}
