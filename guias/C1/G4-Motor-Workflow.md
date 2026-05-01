# Guía 1 Ciclo 2 — Motor de Workflow

**Ciclo 2 · Sistema de Gestión de Trámites**

> 🎯 **Objetivo:** implementar el núcleo del sistema — el motor que lee el diagrama UML almacenado en MongoDB y mueve el trámite automáticamente de nodo en nodo. Al terminar esta guía, el trámite del ejemplo CRE recorre los 4 departamentos en vivo.

---

## 0. Prerequisitos (Ciclo 1 completo)

✅ G1: Auth JWT + Usuarios
✅ G2: Departamentos, Actividades, Políticas de negocio
✅ G3: Swagger + Health + Demo
✅ MongoDB con: 4 departamentos CRE, actividades con SLA, política "Nueva conexión residencial" activa
✅ Los 29 modelos existen (`NodoDiagrama`, `FlujoTransicion`, `ExpedienteDigital`, `SeccionExpediente`, etc.)

---

## 1. Cómo funciona el Motor — Concepto

El motor lee el grafo de nodos y transiciones del diagrama y decide qué hacer en cada paso:

```
INICIO → [ACTIVIDAD nodo-ATC] → [FORK] → [ACTIVIDAD nodo-TEC-inspeccion]
                                       → [ACTIVIDAD nodo-TEC-presupuesto]
                                 [JOIN] → [DECISION ¿legal aprueba?]
                                              Sí → [ACTIVIDAD nodo-OPE] → FIN
                                              No → [ACTIVIDAD nodo-TEC-inspeccion] (iterativo)
```

### Tipos de nodo que maneja el motor

| Tipo en BD | Comportamiento |
|-----------|----------------|
| `inicio` | Punto de entrada — salta automáticamente al primer nodo actividad |
| `actividad` | El funcionario completa su sección → motor avanza |
| `decision` | Motor evalúa la condición según la salida del funcionario (`si` / `no`) |
| `fork` | Motor activa múltiples ramas en paralelo simultáneamente |
| `join` | Motor espera a que **todas** las ramas paralelas terminen |
| `fin` | Motor cierra el trámite (`estado = Aprobado/Cerrado`) |

### Regla crítica

> El motor **nunca espera input humano** en nodos de control (`inicio`, `fork`, `join`, `fin`, `decision`). Solo espera en nodos `actividad`. Cuando el funcionario guarda su sección, el motor toma el control y avanza solo hasta el siguiente nodo `actividad` (o hasta cerrar si llega al `fin`).

---

## 2. Actualizar el modelo `Tramite`

El modelo actual solo tiene `nodoActualId` (un nodo). Para flujos paralelos necesitamos rastrear **múltiples nodos activos** simultáneamente.

Editar `src/main/java/com/example/demo/models/Tramite.java`:

```java
package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tramites")
public class Tramite {

    @Id
    private String id;

    @Indexed(unique = true)
    private String codigo;

    private String clienteId;
    private String politicaId;
    private String expedienteId;

    @Indexed
    private String estadoActual;

    // Nodo actual para flujos lineales/condicionales/iterativos
    private String nodoActualId;

    // Para flujos paralelos: lista de nodos activos simultáneamente
    // Vacía en flujos no paralelos. Cuando todos se completen el JOIN puede avanzar.
    private List<String> nodosParalellosActivos = new ArrayList<>();

    private String funcionarioActualId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private LocalDateTime fechaCierreReal;

    private int prioridad;

    // Helpers
    public boolean estaEnParalelo() {
        return nodosParalellosActivos != null && !nodosParalellosActivos.isEmpty();
    }
}
```

---

## 3. DTOs del Motor

Crear en `src/main/java/com/example/demo/dto/`:

### 3.1. `IniciarTramiteRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IniciarTramiteRequest {

    @NotBlank(message = "clienteId es obligatorio")
    private String clienteId;

    @NotBlank(message = "politicaId es obligatorio")
    private String politicaId;

    private int prioridad; // 1–5, default 3
}
```

### 3.2. `CompletarNodoRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompletarNodoRequest {

    @NotBlank(message = "funcionarioId es obligatorio")
    private String funcionarioId;

    // Para nodos de decisión: "si" o "no" (coincide con la etiqueta del FlujoTransicion)
    // Para nodos normales: null o vacío
    private String decision;

    private String notas;
}
```

### 3.3. `EstadoTramiteResponse.java`

```java
package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoTramiteResponse {

    private String tramiteId;
    private String codigo;
    private String estadoActual;

    // Info del nodo actual
    private String nodoActualId;
    private String nodoActualNombre;
    private String nodoActualTipo;
    private String departamentoActual;

    // Para flujos paralelos
    private boolean enParalelo;
    private List<String> nodosParalellosActivos;

    // Línea de tiempo
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private int prioridad;
}
```

---

## 4. WorkflowEngineService — El núcleo

`src/main/java/com/example/demo/services/WorkflowEngineService.java`

Este es el servicio más importante del sistema. Lee el diagrama y ejecuta el flujo.

```java
package com.example.demo.services;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.IniciarTramiteRequest;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    @Autowired private TrazabilidadRepository trazabilidadRepository;

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
        tramite.setEstadoActual("Nuevo");
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
            seccion.setEstado("bloqueada");   // todas bloqueadas al inicio
            seccion = seccionRepository.save(seccion);
            seccionesIds.add(seccion.getId());
        }

        expediente.setSeccionesIds(seccionesIds);
        expedienteRepository.save(expediente);

        tramite.setExpedienteId(expediente.getId());
        tramite.setEstadoActual("En proceso");
        tramite = tramiteRepository.save(tramite);

        // 6. Avanzar desde INICIO al primer nodo (el motor toma control)
        tramite = avanzarDesde(tramite, nodoInicio, null, todosLosNodos);

        registrarHistorico(tramite.getId(), "Nuevo", "En proceso", null, tramite.getNodoActualId(), req.getClienteId(), "Trámite iniciado");
        return tramiteRepository.save(tramite);
    }

    // ─────────────────────────────────────────────────────────────────────
    // COMPLETAR NODO ACTUAL
    // ─────────────────────────────────────────────────────────────────────

    public Tramite completarNodo(String tramiteId, CompletarNodoRequest req) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        if ("Aprobado".equals(tramite.getEstadoActual()) || "Rechazado".equals(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El trámite ya está cerrado");
        }

        // Determinar qué nodo está completando el funcionario
        String nodoIdActivo = resolverNodoActivo(tramite, req.getFuncionarioId());

        // Marcar la sección del nodo como completada
        SeccionExpediente seccion = seccionRepository
                .findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId())
                .stream()
                .filter(s -> nodoIdActivo.equals(s.getNodoId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Sección del nodo no encontrada"));

        seccion.setEstado("completada");
        seccion.setFechaCompletado(LocalDateTime.now());
        seccionRepository.save(seccion);

        // Cargar todos los nodos del diagrama para el motor
        PoliticaNegocio politica = politicaRepository.findById(tramite.getPoliticaId()).orElseThrow();
        List<NodoDiagrama> todosLosNodos = nodoRepository.findByDiagramaId(politica.getDiagramaId());

        NodoDiagrama nodoActual = nodoRepository.findById(nodoIdActivo).orElseThrow();

        String estadoAnterior = tramite.getEstadoActual();

        // Si es nodo paralelo: marcar rama y verificar si se completó el join
        if (tramite.estaEnParalelo()) {
            tramite.getNodosParalellosActivos().remove(nodoIdActivo);

            if (!tramite.getNodosParalellosActivos().isEmpty()) {
                // Aún hay ramas paralelas activas — esperar
                tramiteRepository.save(tramite);
                return tramite;
            }

            // Todas las ramas completadas → buscar el JOIN y avanzar desde él
            NodoDiagrama nodoJoin = encontrarJoin(nodoActual, todosLosNodos);
            tramite = avanzarDesde(tramite, nodoJoin, req.getDecision(), todosLosNodos);
        } else {
            tramite = avanzarDesde(tramite, nodoActual, req.getDecision(), todosLosNodos);
        }

        registrarHistorico(tramiteId, estadoAnterior, tramite.getEstadoActual(),
                nodoIdActivo, tramite.getNodoActualId(), req.getFuncionarioId(), req.getNotas());

        return tramiteRepository.save(tramite);
    }

    // ─────────────────────────────────────────────────────────────────────
    // MOTOR: AVANZAR DESDE UN NODO
    // ─────────────────────────────────────────────────────────────────────

    private Tramite avanzarDesde(Tramite tramite, NodoDiagrama nodoOrigen,
                                  String decision, List<NodoDiagrama> todosLosNodos) {

        List<FlujoTransicion> transiciones = flujoRepository.findByNodoOrigenId(nodoOrigen.getId());

        if (transiciones.isEmpty()) {
            // Sin salida → cerrar trámite
            return cerrarTramite(tramite, "Aprobado");
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
                String decisionNormalizada = (decision != null ? decision : "si").toLowerCase();
                FlujoTransicion transicion = transiciones.stream()
                        .filter(t -> decisionNormalizada.equals(
                                t.getEtiqueta() != null ? t.getEtiqueta().toLowerCase() : ""))
                        .findFirst()
                        .orElse(transiciones.get(0));    // fallback a la primera si no coincide

                NodoDiagrama nodoSiguiente = encontrarNodoPorId(transicion.getNodoDestinoId(), todosLosNodos);
                tramite.setEstadoActual(
                        "si".equals(decisionNormalizada) ? "Derivado" : "Observado");
                yield procesarNodo(tramite, nodoSiguiente, decision, todosLosNodos);
            }

            case "fork" -> {
                // Activar TODAS las ramas en paralelo
                List<String> nodosParalelos = new ArrayList<>();
                for (FlujoTransicion t : transiciones) {
                    NodoDiagrama rama = encontrarNodoPorId(t.getNodoDestinoId(), todosLosNodos);
                    desbloquearSeccion(tramite.getExpedienteId(), rama.getId());
                    nodosParalelos.add(rama.getId());
                }
                tramite.setNodosParalellosActivos(nodosParalelos);
                tramite.setNodoActualId(null);   // sin nodo único — hay varios
                tramite.setEstadoActual("En proceso");
                yield tramite;
            }

            default -> tramite;
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // MOTOR: PROCESAR UN NODO DESTINO
    // ─────────────────────────────────────────────────────────────────────

    private Tramite procesarNodo(Tramite tramite, NodoDiagrama nodo,
                                  String decision, List<NodoDiagrama> todosLosNodos) {
        return switch (nodo.getTipo()) {
            case "actividad" -> {
                // Desbloquear la sección de este nodo y asignar
                desbloquearSeccion(tramite.getExpedienteId(), nodo.getId());
                tramite.setNodoActualId(nodo.getId());
                tramite.setNodosParalellosActivos(new ArrayList<>());
                tramite.setEstadoActual("En proceso");
                yield tramite;
            }
            // Nodos de control: el motor los atraviesa automáticamente sin parar
            case "decision", "fork", "join", "fin" ->
                    avanzarDesde(tramite, nodo, decision, todosLosNodos);

            default -> tramite;
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private void desbloquearSeccion(String expedienteId, String nodoId) {
        seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(expedienteId)
                .stream()
                .filter(s -> nodoId.equals(s.getNodoId()) && "bloqueada".equals(s.getEstado()))
                .findFirst()
                .ifPresent(s -> {
                    s.setEstado("en_curso");
                    s.setFechaAsignacion(LocalDateTime.now());
                    seccionRepository.save(s);
                });
    }

    private Tramite cerrarTramite(Tramite tramite, String estadoFinal) {
        tramite.setEstadoActual(estadoFinal);
        tramite.setNodoActualId(null);
        tramite.setNodosParalellosActivos(new ArrayList<>());
        tramite.setFechaCierreReal(LocalDateTime.now());
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

    private String resolverNodoActivo(Tramite tramite, String funcionarioId) {
        if (tramite.estaEnParalelo()) {
            // En paralelo: buscar qué nodo le corresponde al funcionario
            return tramite.getNodosParalellosActivos().stream()
                    .filter(nodoId -> {
                        NodoDiagrama n = nodoRepository.findById(nodoId).orElse(null);
                        if (n == null) return false;
                        // Simplificación para demo: el funcionario puede completar cualquier nodo activo
                        // En producción: comparar con el departamento del funcionario
                        return true;
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No hay nodo paralelo activo para el funcionario"));
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
        long count = tramiteRepository.count() + 1;
        return String.format("TR-%d-%05d", year, count);
    }
}
```

---

## 5. Repositorio de EstadoHistorico

El servicio lo necesita pero no existía. Crear:

`src/main/java/com/example/demo/repositories/EstadoHistoricoRepository.java`

```java
package com.example.demo.repositories;

import com.example.demo.models.EstadoHistorico;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstadoHistoricoRepository extends MongoRepository<EstadoHistorico, String> {
    List<EstadoHistorico> findByTramiteIdOrderByFechaCambioAsc(String tramiteId);
}
```

---

## 6. WorkflowController

`src/main/java/com/example/demo/controllers/WorkflowController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.EstadoTramiteResponse;
import com.example.demo.dto.IniciarTramiteRequest;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.services.WorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow")
@Tag(name = "Motor de Workflow", description = "Núcleo del sistema — inicia y avanza trámites por el diagrama UML")
public class WorkflowController {

    @Autowired private WorkflowEngineService workflowEngine;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;

    @PostMapping("/iniciar")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(
        summary = "Iniciar un trámite",
        description = "Crea el trámite, genera el expediente digital con todas las secciones, y avanza automáticamente al primer nodo actividad del diagrama."
    )
    public ResponseEntity<Tramite> iniciar(@Valid @RequestBody IniciarTramiteRequest req) {
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
    @Operation(summary = "Estado actual del trámite con info del nodo activo")
    public ResponseEntity<EstadoTramiteResponse> estado(@PathVariable String tramiteId) {
        Tramite t = workflowEngine.buscarTramite(tramiteId);

        EstadoTramiteResponse resp = new EstadoTramiteResponse();
        resp.setTramiteId(t.getId());
        resp.setCodigo(t.getCodigo());
        resp.setEstadoActual(t.getEstadoActual());
        resp.setNodosParalellosActivos(t.getNodosParalellosActivos());
        resp.setEnParalelo(t.estaEnParalelo());
        resp.setFechaInicio(t.getFechaInicio());
        resp.setFechaEstimadaCierre(t.getFechaEstimadaCierre());
        resp.setPrioridad(t.getPrioridad());

        if (t.getNodoActualId() != null) {
            nodoRepository.findById(t.getNodoActualId()).ifPresent(nodo -> {
                resp.setNodoActualId(nodo.getId());
                resp.setNodoActualNombre(nodo.getNombre());
                resp.setNodoActualTipo(nodo.getTipo());
                if (nodo.getDepartamentoId() != null) {
                    departamentoRepository.findById(nodo.getDepartamentoId())
                            .ifPresent(d -> resp.setDepartamentoActual(d.getNombre()));
                }
            });
        }

        return ResponseEntity.ok(resp);
    }
}
```

Agregar el método `buscarTramite` al `WorkflowEngineService`:

```java
public Tramite buscarTramite(String tramiteId) {
    return tramiteRepository.findById(tramiteId)
            .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
}
```

---

## 7. Actualizar SecurityConfig — rutas del motor

```java
.requestMatchers("/api/workflow/*/estado").authenticated()
.requestMatchers("/api/workflow/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
```

---

## 8. Cargar el diagrama CRE en MongoDB para el demo

Antes de probar el motor, el diagrama UML debe existir en la BD. Crear en Mongo Express (colección `diagramas_workflow`) el diagrama del flujo CRE, y luego sus nodos y transiciones.

### 8.1. Crear el diagrama

```json
// Colección: diagramas_workflow
{
  "nombre": "Flujo Nueva Conexión Residencial",
  "politicaId": "<ID_POLITICA_ACTIVA>",
  "creadorId": "<ID_ADMIN>",
  "swimlanes": ["Atención al Cliente", "Área Técnica", "Área Legal", "Operaciones"],
  "versionActual": 1,
  "estado": "publicado",
  "generadoPorIa": false,
  "fechaCreacion": { "$date": "2025-04-24T00:00:00Z" },
  "ultimaModificacion": { "$date": "2025-04-24T00:00:00Z" }
}
```

### 8.2. Crear los nodos (en `nodos_diagrama`)

```json
// Nodo INICIO
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "inicio", "nombre": "Inicio", "orden": 0 }

// Nodo ATC (actividad)
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "actividad", "nombre": "Verificar documentos",
  "departamentoId": "<ID_ATC>", "swimlane": "Atención al Cliente", "orden": 1 }

// Nodo FORK
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "fork", "nombre": "Fork paralelo TEC", "orden": 2 }

// Nodo TEC — Inspección
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "actividad", "nombre": "Inspección en campo",
  "departamentoId": "<ID_TEC>", "swimlane": "Área Técnica", "orden": 3 }

// Nodo TEC — Presupuesto
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "actividad", "nombre": "Elaborar presupuesto",
  "departamentoId": "<ID_TEC>", "swimlane": "Área Técnica", "orden": 3 }

// Nodo JOIN
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "join", "nombre": "Join TEC", "orden": 4 }

// Nodo DECISION — Legal
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "decision", "nombre": "¿Contrato aprobado?", "orden": 5 }

// Nodo LEG (actividad)
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "actividad", "nombre": "Revisar contrato",
  "departamentoId": "<ID_LEG>", "swimlane": "Área Legal", "orden": 6 }

// Nodo OPE (actividad)
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "actividad", "nombre": "Cierre y conexión",
  "departamentoId": "<ID_OPE>", "swimlane": "Operaciones", "orden": 7 }

// Nodo FIN
{ "diagramaId": "<ID_DIAGRAMA>", "tipo": "fin", "nombre": "Fin", "orden": 8 }
```

### 8.3. Crear las transiciones (en `flujos_transicion`)

```json
// INICIO → ATC
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_INICIO>", "nodoDestinoId": "<ID_ATC>", "tipo": "secuencial" }

// ATC → FORK
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_ATC>", "nodoDestinoId": "<ID_FORK>", "tipo": "secuencial" }

// FORK → TEC-Inspeccion
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_FORK>", "nodoDestinoId": "<ID_TEC_INSP>", "tipo": "paralelo" }

// FORK → TEC-Presupuesto
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_FORK>", "nodoDestinoId": "<ID_TEC_PRES>", "tipo": "paralelo" }

// TEC-Inspeccion → JOIN
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_TEC_INSP>", "nodoDestinoId": "<ID_JOIN>", "tipo": "paralelo" }

// TEC-Presupuesto → JOIN
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_TEC_PRES>", "nodoDestinoId": "<ID_JOIN>", "tipo": "paralelo" }

// JOIN → LEG
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_JOIN>", "nodoDestinoId": "<ID_LEG>", "tipo": "secuencial" }

// LEG → DECISION
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_LEG>", "nodoDestinoId": "<ID_DECISION>", "tipo": "secuencial" }

// DECISION → OPE (si aprueba)
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_DECISION>", "nodoDestinoId": "<ID_OPE>",
  "tipo": "condicional", "etiqueta": "si", "condicion": "aprobado" }

// DECISION → TEC-Inspeccion (no aprueba — iterativo)
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_DECISION>", "nodoDestinoId": "<ID_TEC_INSP>",
  "tipo": "iterativo", "etiqueta": "no", "condicion": "rechazado" }

// OPE → FIN
{ "diagramaId": "<ID>", "nodoOrigenId": "<ID_OPE>", "nodoDestinoId": "<ID_FIN>", "tipo": "secuencial" }
```

> 💡 Para el demo usa el endpoint `POST /api/politicas/{id}` con `PATCH /estado` para asignar el `diagramaId` a la política activa (actualizar el campo `diagramaId` directamente en Mongo Express es más rápido para la demo).

---

## 9. Probar el flujo completo CRE

### 9.1. Iniciar el trámite

```bash
curl -X POST http://localhost:8080/api/workflow/iniciar \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{
    "clienteId": "<ID_CLIENTE>",
    "politicaId": "<ID_POLITICA>",
    "prioridad": 3
  }'
```

**Respuesta esperada:** trámite con `estadoActual: "En proceso"` y `nodoActualId` apuntando al nodo ATC.

### 9.2. Verificar estado

```bash
curl http://localhost:8080/api/workflow/<TRAMITE_ID>/estado \
  -H "Authorization: Bearer $TOKEN"
# Respuesta: nodo actual = "Verificar documentos", departamento = "Atención al Cliente"
```

### 9.3. Completar ATC → motor activa FORK

```bash
curl -X POST http://localhost:8080/api/workflow/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_ATC>", "notas": "Documentos verificados"}'
```

**Respuesta:** `nodosParalellosActivos: [<ID_TEC_INSP>, <ID_TEC_PRES>]`, `enParalelo: true`

### 9.4. Completar rama Inspección

```bash
curl -X POST http://localhost:8080/api/workflow/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_TEC>", "notas": "Inspección OK"}'
# Motor: queda 1 rama pendiente → no avanza aún
```

### 9.5. Completar rama Presupuesto → JOIN → avanza a Legal

```bash
curl -X POST http://localhost:8080/api/workflow/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_TEC>", "notas": "Presupuesto listo"}'
# Motor: ambas ramas completas → JOIN → nodo LEG desbloqueado automáticamente
```

### 9.6. Legal aprueba → va a Operaciones

```bash
curl -X POST http://localhost:8080/api/workflow/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_LEG>", "decision": "si", "notas": "Contrato aprobado"}'
# Motor: decision=si → avanza a OPE
```

### 9.7. Legal rechaza → vuelve a Técnica (iterativo)

```bash
# Alternativa al paso anterior
-d '{"funcionarioId": "<ID_FUNC_LEG>", "decision": "no", "notas": "Falta documentación"}'
# Motor: decision=no → estado=Observado → vuelve a TEC-Inspección
```

### 9.8. Operaciones cierra el trámite

```bash
curl -X POST http://localhost:8080/api/workflow/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_OPE>", "notas": "Conexión ejecutada"}'
# Motor: OPE → FIN → estadoActual = "Aprobado", fechaCierreReal = now()
```

---

## 10. Verificar en Mongo Express tras el demo

Mostrar en tiempo real estas colecciones:

| Colección | Qué mostrar |
|-----------|-------------|
| `tramites` | `estadoActual` cambia: `En proceso → Aprobado` |
| `secciones_expediente` | `estado` cambia: `bloqueada → en_curso → completada` por nodo |
| `estados_historicos` | Log completo del recorrido del trámite por cada nodo |
| `expedientes` | Único expediente con todas las secciones del trámite |

---

## 11. Checklist de entregables G1-C2

- [ ] Modelo `Tramite` actualizado con `nodosParalellosActivos` + `estaEnParalelo()`
- [ ] 3 DTOs: `IniciarTramiteRequest`, `CompletarNodoRequest`, `EstadoTramiteResponse`
- [ ] `EstadoHistoricoRepository` creado
- [ ] `WorkflowEngineService` implementado con los 4 tipos de flujo:
  - [ ] Lineal/secuencial (INICIO → ACTIVIDAD → ACTIVIDAD → FIN)
  - [ ] Condicional (DECISION con etiqueta "si"/"no")
  - [ ] Paralelo (FORK activa múltiples ramas → JOIN espera a todas)
  - [ ] Iterativo (DECISION "no" apunta a nodo anterior)
- [ ] `WorkflowController` con 3 endpoints: `/iniciar`, `/completar-nodo`, `/estado`
- [ ] `SecurityConfig` actualizado para rutas `/api/workflow/**`
- [ ] Diagrama CRE cargado en MongoDB (nodos + transiciones)
- [ ] Flujo CRE completo demostrado de principio a fin:
  - [ ] ATC → FORK → TEC (2 ramas paralelas) → JOIN → LEG → DECISION → OPE → FIN
  - [ ] Ruta alternativa: DECISION "no" → vuelve a TEC (iterativo)
- [ ] En Mongo Express: `secciones_expediente` se desbloquean y completan en orden
- [ ] En Mongo Express: `estados_historicos` registra cada transición

---

## 12. Qué sigue (G2 — Expediente Digital)

Con el motor funcionando, **G2 del Ciclo 2** agrega:

- Leer y editar los **campos de cada sección** del expediente
- **Bloqueos**: un funcionario solo puede editar su sección activa
- **Campos dinámicos** basados en la plantilla del nodo (`FormularioPlantilla` + `CampoPlantilla`)
- **Adjuntos** (documentos e imágenes por sección)
- **Voz a sección** (integración con el módulo IA — transcripción de audio a campos)

---

## 🛠️ Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| `Política no tiene diagrama asignado` | `diagramaId` es null en la política | Asignar en Mongo Express directamente |
| `El diagrama no tiene nodo de inicio` | Falta insertar el nodo `tipo: "inicio"` | Insertar en `nodos_diagrama` |
| `No se encontró nodo JOIN` | Las transiciones del nodo paralelo no apuntan a un JOIN | Revisar `flujos_transicion` de los nodos TEC |
| Motor se queda en rama paralela | `nodosParalellosActivos` no se vacía | Verificar que se llame `completarNodo` para CADA rama |
| `Transición inválida en decision` | `etiqueta` del flujo no coincide con `decision` del request | Usar exactamente `"si"` o `"no"` (minúsculas) |
| Sección no se desbloquea | `nodoId` del nodo no coincide con el de la sección | Verificar que `SeccionExpediente.nodoId` se setea al iniciar |

---

*Guía 1 Ciclo 2 — Motor de Workflow · Sistema de Gestión de Trámites · Primer Examen Parcial*
