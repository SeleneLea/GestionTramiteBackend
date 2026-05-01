# Guía 1 — Ciclo 2: Registrar Trámite + Motor de Workflow

**Ciclo 2 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Implementar los tres primeros CUs del Ciclo 2. Al terminar esta guía, un cliente puede iniciar un trámite (CU-07), el motor lo enruta automáticamente por el diagrama (CU-08), y el funcionario lo recibe en su bandeja de entrada (CU-09).

---

## 0. Estado al entrar a G1-C2

✅ G1-C1: Auth JWT + Usuarios (CU-01, CU-02)
✅ G2-C1: Departamentos + Actividades + Políticas (CU-04, CU-05, CU-06)
✅ G3-C1: Swagger + Health + Roles completos (CU-03)
✅ G4-C1 / G5-C1: Diagramas + Nodos + Transiciones + IA mock (CU-12, CU-13, CU-14)
✅ MongoDB con: departamentos CRE, actividades, política "Nueva conexión residencial", diagrama publicado con nodos y transiciones

> 💡 **Importante:** el diagrama del flujo CRE ya puede crearse vía API (endpoints `/api/diagramas`, `/api/diagramas/{id}/nodos`, `/api/diagramas/{id}/transiciones` — implementados en G5-C1). Ya no es necesario insertarlo manualmente en Mongo Express.

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-07** | Registrar solicitud de trámite | Cliente | `POST /api/tramites/iniciar` — crea el trámite y lo encola al primer nodo |
| **CU-08** | Asignar siguiente actividad | Sistema (Motor) | `WorkflowEngineService` — evalúa el grafo y mueve el trámite nodo a nodo |
| **CU-09** | Recibir trámite asignado | Funcionario | `GET /api/tramites/mis-pendientes` — lista la bandeja del funcionario actual |

### Cómo se relacionan los tres CUs

```
Cliente: POST /api/tramites/iniciar (CU-07)
    ↓
Motor evalúa el diagrama → desbloquea sección del primer nodo → guarda nodoActualId (CU-08)
    ↓
Funcionario: GET /api/tramites/mis-pendientes → ve el trámite en su bandeja (CU-09)
    ↓
Funcionario: POST /api/tramites/{id}/completar-nodo → motor avanza al siguiente nodo (CU-08)
```

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
    private String codigo; // TR-2026-00001

    private String clienteId;
    private String politicaId;
    private String expedienteId;

    @Indexed
    private String estadoActual; // Nuevo | En proceso | Derivado | Observado | Rechazado | Aprobado

    // Nodo actual para flujos lineales/condicionales/iterativos
    private String nodoActualId;

    // Para flujos paralelos: nodos activos simultáneamente (fork/join)
    private List<String> nodosParalellosActivos = new ArrayList<>();

    private String funcionarioActualId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private LocalDateTime fechaCierreReal;

    private int prioridad; // 1–5

    public boolean estaEnParalelo() {
        return nodosParalellosActivos != null && !nodosParalellosActivos.isEmpty();
    }
}
```

---

## 3. DTOs

Crear en `src/main/java/com/example/demo/dto/`:

### 3.1. `IniciarTramiteRequest.java` (CU-07)

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IniciarTramiteRequest {

    // En producción el clienteId viene del token JWT.
    // Para la demo se envía explícitamente para simplificar las pruebas.
    @NotBlank(message = "clienteId es obligatorio")
    private String clienteId;

    @NotBlank(message = "politicaId es obligatorio")
    private String politicaId;

    private int prioridad; // 1–5, default 3 si no se envía
}
```

### 3.2. `CompletarNodoRequest.java` (CU-08)

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompletarNodoRequest {

    @NotBlank(message = "funcionarioId es obligatorio")
    private String funcionarioId;

    // Solo para nodos de decisión: "si" o "no" (coincide con la etiqueta del FlujoTransicion)
    // Para nodos normales: null o vacío
    private String decision;

    private String notas;
}
```

### 3.3. `EstadoTramiteResponse.java` (CU-09)

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

    // Info del nodo activo
    private String nodoActualId;
    private String nodoActualNombre;
    private String nodoActualTipo;
    private String departamentoActual;

    // Para flujos paralelos
    private boolean enParalelo;
    private List<String> nodosParalellosActivos;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private int prioridad;
}
```

---

## 4. Repositorio de EstadoHistorico

Crear `src/main/java/com/example/demo/repositories/EstadoHistoricoRepository.java`:

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

## 5. WorkflowEngineService — El núcleo (CU-08)

Este es el servicio más importante del sistema. Lee el diagrama almacenado en MongoDB y ejecuta el flujo automáticamente.

### Cómo funciona el motor

```
INICIO → [ACTIVIDAD nodo-ATC] → [FORK] → [ACTIVIDAD nodo-TEC-inspeccion]
                                        → [ACTIVIDAD nodo-TEC-presupuesto]
                                  [JOIN] → [ACTIVIDAD nodo-LEG]
                                                  ↓
                                          [DECISION ¿aprueba?]
                                          Sí → [ACTIVIDAD nodo-OPE] → FIN
                                          No → [ACTIVIDAD nodo-TEC-inspeccion] (iterativo)
```

### Regla crítica del motor

> El motor **nunca espera input humano** en nodos de control (`inicio`, `fork`, `join`, `decision`, `fin`). Solo se detiene en nodos `actividad`. Cuando el funcionario guarda su sección, el motor toma el control y avanza solo hasta el siguiente nodo `actividad` (o cierra el trámite si llega a `fin`).

| Tipo de nodo | Comportamiento del motor |
|-------------|--------------------------|
| `inicio` | Salta automáticamente al primer nodo actividad |
| `actividad` | Se detiene — espera que el funcionario complete la sección |
| `decision` | Evalúa la decisión del funcionario (`si`/`no`) y elige la transición |
| `fork` | Activa múltiples ramas en paralelo simultáneamente |
| `join` | Espera a que **todas** las ramas paralelas terminen |
| `fin` | Cierra el trámite (`estado = Aprobado`) |

`src/main/java/com/example/demo/services/WorkflowEngineService.java`

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

    // ─────────────────────────────────────────────────────────────────────────
    // CU-07: REGISTRAR SOLICITUD DE TRÁMITE
    // ─────────────────────────────────────────────────────────────────────────

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

        if (!"publicado".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException("El diagrama debe estar publicado");
        }

        List<NodoDiagrama> todosLosNodos = nodoRepository.findByDiagramaId(diagrama.getId());

        // 3. Encontrar el nodo INICIO
        NodoDiagrama nodoInicio = todosLosNodos.stream()
                .filter(n -> "inicio".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El diagrama no tiene nodo de inicio"));

        // 4. Crear el trámite con estado inicial
        Tramite tramite = new Tramite();
        tramite.setCodigo(generarCodigo());
        tramite.setClienteId(req.getClienteId());
        tramite.setPoliticaId(req.getPoliticaId());
        tramite.setEstadoActual("Nuevo");
        tramite.setPrioridad(req.getPrioridad() > 0 ? req.getPrioridad() : 3);
        tramite.setFechaInicio(LocalDateTime.now());
        tramite = tramiteRepository.save(tramite);

        // 5. Crear expediente digital con una sección por cada nodo actividad
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
            seccion.setEstado("bloqueada"); // todas bloqueadas al inicio
            seccion = seccionRepository.save(seccion);
            seccionesIds.add(seccion.getId());
        }

        expediente.setSeccionesIds(seccionesIds);
        expedienteRepository.save(expediente);

        tramite.setExpedienteId(expediente.getId());
        tramite.setEstadoActual("En proceso");
        tramite = tramiteRepository.save(tramite);

        // 6. Motor (CU-08): avanzar desde INICIO al primer nodo actividad
        tramite = avanzarDesde(tramite, nodoInicio, null, todosLosNodos);

        registrarHistorico(tramite.getId(), "Nuevo", "En proceso",
                null, tramite.getNodoActualId(), req.getClienteId(), "Trámite iniciado");

        return tramiteRepository.save(tramite);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CU-08: ASIGNAR SIGUIENTE ACTIVIDAD (COMPLETAR NODO)
    // ─────────────────────────────────────────────────────────────────────────

    public Tramite completarNodo(String tramiteId, CompletarNodoRequest req) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        if ("Aprobado".equals(tramite.getEstadoActual())
                || "Rechazado".equals(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El trámite ya está cerrado");
        }

        String nodoIdActivo = resolverNodoActivo(tramite, req.getFuncionarioId());

        // Marcar la sección del nodo como completada
        seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(tramite.getExpedienteId())
                .stream()
                .filter(s -> nodoIdActivo.equals(s.getNodoId()))
                .findFirst()
                .ifPresent(s -> {
                    s.setEstado("completada");
                    s.setFechaCompletado(LocalDateTime.now());
                    seccionRepository.save(s);
                });

        PoliticaNegocio politica = politicaRepository.findById(tramite.getPoliticaId()).orElseThrow();
        List<NodoDiagrama> todosLosNodos = nodoRepository.findByDiagramaId(politica.getDiagramaId());
        NodoDiagrama nodoActual = nodoRepository.findById(nodoIdActivo).orElseThrow();
        String estadoAnterior = tramite.getEstadoActual();

        // Flujo paralelo: marcar rama y verificar si se completó el join
        if (tramite.estaEnParalelo()) {
            tramite.getNodosParalellosActivos().remove(nodoIdActivo);

            if (!tramite.getNodosParalellosActivos().isEmpty()) {
                // Aún quedan ramas activas — no avanzar todavía
                tramiteRepository.save(tramite);
                return tramite;
            }

            // Todas las ramas completadas → buscar el JOIN y continuar
            NodoDiagrama nodoJoin = encontrarJoin(nodoActual, todosLosNodos);
            tramite = avanzarDesde(tramite, nodoJoin, req.getDecision(), todosLosNodos);
        } else {
            tramite = avanzarDesde(tramite, nodoActual, req.getDecision(), todosLosNodos);
        }

        registrarHistorico(tramiteId, estadoAnterior, tramite.getEstadoActual(),
                nodoIdActivo, tramite.getNodoActualId(), req.getFuncionarioId(), req.getNotas());

        return tramiteRepository.save(tramite);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MOTOR: AVANZAR DESDE UN NODO
    // ─────────────────────────────────────────────────────────────────────────

    private Tramite avanzarDesde(Tramite tramite, NodoDiagrama nodoOrigen,
                                  String decision, List<NodoDiagrama> todosLosNodos) {

        List<FlujoTransicion> transiciones = flujoRepository.findByNodoOrigenId(nodoOrigen.getId());

        if (transiciones.isEmpty()) {
            return cerrarTramite(tramite, "Aprobado");
        }

        return switch (nodoOrigen.getTipo()) {

            case "inicio", "join", "actividad" -> {
                FlujoTransicion transicion = transiciones.get(0);
                NodoDiagrama siguiente = encontrarNodoPorId(transicion.getNodoDestinoId(), todosLosNodos);
                yield procesarNodo(tramite, siguiente, decision, todosLosNodos);
            }

            case "decision" -> {
                String dec = (decision != null ? decision : "si").toLowerCase();
                FlujoTransicion transicion = transiciones.stream()
                        .filter(t -> dec.equals(t.getEtiqueta() != null ? t.getEtiqueta().toLowerCase() : ""))
                        .findFirst()
                        .orElse(transiciones.get(0));

                NodoDiagrama siguiente = encontrarNodoPorId(transicion.getNodoDestinoId(), todosLosNodos);
                tramite.setEstadoActual("si".equals(dec) ? "Derivado" : "Observado");
                yield procesarNodo(tramite, siguiente, decision, todosLosNodos);
            }

            case "fork" -> {
                List<String> ramas = new ArrayList<>();
                for (FlujoTransicion t : transiciones) {
                    NodoDiagrama rama = encontrarNodoPorId(t.getNodoDestinoId(), todosLosNodos);
                    desbloquearSeccion(tramite.getExpedienteId(), rama.getId());
                    ramas.add(rama.getId());
                }
                tramite.setNodosParalellosActivos(ramas);
                tramite.setNodoActualId(null);
                tramite.setEstadoActual("En proceso");
                yield tramite;
            }

            default -> tramite;
        };
    }

    private Tramite procesarNodo(Tramite tramite, NodoDiagrama nodo,
                                  String decision, List<NodoDiagrama> todosLosNodos) {
        return switch (nodo.getTipo()) {
            case "actividad" -> {
                desbloquearSeccion(tramite.getExpedienteId(), nodo.getId());
                tramite.setNodoActualId(nodo.getId());
                tramite.setNodosParalellosActivos(new ArrayList<>());
                tramite.setEstadoActual("En proceso");
                yield tramite;
            }
            case "decision", "fork", "join", "fin" ->
                    avanzarDesde(tramite, nodo, decision, todosLosNodos);
            default -> tramite;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

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
                .orElseThrow(() -> new IllegalStateException("Nodo no encontrado en el diagrama: " + id));
    }

    private NodoDiagrama encontrarJoin(NodoDiagrama nodoRama, List<NodoDiagrama> todosLosNodos) {
        return flujoRepository.findByNodoOrigenId(nodoRama.getId()).stream()
                .map(t -> encontrarNodoPorId(t.getNodoDestinoId(), todosLosNodos))
                .filter(n -> "join".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró nodo JOIN tras la rama paralela del nodo: " + nodoRama.getNombre()));
    }

    private String resolverNodoActivo(Tramite tramite, String funcionarioId) {
        if (tramite.estaEnParalelo()) {
            // Simplificación demo: el primer nodo activo disponible
            // En producción: comparar con el departamento del funcionario
            return tramite.getNodosParalellosActivos().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No hay nodo paralelo activo para el funcionario"));
        }
        if (tramite.getNodoActualId() == null) {
            throw new IllegalArgumentException("El trámite no tiene nodo activo asignado");
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

    public Tramite buscarTramite(String tramiteId) {
        return tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
    }
}
```

---

## 6. Actualizar `TramiteRepository`

El repo de trámites necesita métodos para la bandeja del funcionario (CU-09).

Editar `src/main/java/com/example/demo/repositories/TramiteRepository.java`:

```java
package com.example.demo.repositories;

import com.example.demo.models.Tramite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TramiteRepository extends MongoRepository<Tramite, String> {

    Optional<Tramite> findByCodigo(String codigo);

    List<Tramite> findByClienteId(String clienteId);

    // CU-09: bandeja del funcionario — trámites donde su nodo está activo
    List<Tramite> findByNodoActualIdIn(List<String> nodoIds);

    // Trámites activos (no cerrados)
    @Query("{ 'estadoActual': { $in: ['En proceso', 'Derivado', 'Observado'] } }")
    List<Tramite> findTramitesActivos();

    long countByEstadoActual(String estado);
}
```

---

## 7. WorkflowController

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
import com.example.demo.repositories.TramiteRepository;
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

import java.util.List;

@RestController
@RequestMapping("/api/tramites")
@Tag(name = "Trámites — Motor de Workflow",
     description = "CU-07 Registrar solicitud · CU-08 Motor asigna siguiente actividad · CU-09 Bandeja del funcionario")
public class WorkflowController {

    @Autowired private WorkflowEngineService workflowEngine;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private TramiteRepository tramiteRepository;

    // ─── CU-07: el cliente registra una solicitud de trámite ─────────────────
    @PostMapping("/iniciar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'FUNCIONARIO', 'ADMINISTRADOR')")
    @Operation(
        summary = "Registrar solicitud de trámite (CU-07)",
        description = """
                El cliente selecciona una política de negocio activa y el sistema:
                1. Crea el trámite con código único.
                2. Genera el expediente digital con todas las secciones (una por cada nodo actividad).
                3. El motor (CU-08) avanza automáticamente hasta el primer nodo actividad.
                El trámite queda listo en la bandeja del primer funcionario.
                """)
    public ResponseEntity<Tramite> iniciar(@Valid @RequestBody IniciarTramiteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowEngine.iniciarTramite(req));
    }

    // ─── CU-08: motor asigna la siguiente actividad al completar el nodo ─────
    @PostMapping("/{tramiteId}/completar-nodo")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    @Operation(
        summary = "Completar nodo y avanzar (CU-08)",
        description = """
                El funcionario indica que terminó su sección. El motor evalúa el siguiente paso:
                - Flujo lineal: avanza al siguiente nodo.
                - Decision node: evalúa "si"/"no" y toma la ruta correspondiente.
                - Fork: activa todas las ramas paralelas simultáneamente.
                - Join: espera a que todas las ramas del fork terminen.
                - Fin: cierra el trámite con estado "Aprobado".
                """)
    public ResponseEntity<Tramite> completarNodo(@PathVariable String tramiteId,
                                                  @Valid @RequestBody CompletarNodoRequest req) {
        return ResponseEntity.ok(workflowEngine.completarNodo(tramiteId, req));
    }

    // ─── CU-09: el funcionario consulta su bandeja de pendientes ─────────────
    @GetMapping("/mis-pendientes")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    @Operation(
        summary = "Bandeja de trámites pendientes del funcionario (CU-09)",
        description = """
                Devuelve los trámites que tienen como nodo activo alguno que pertenece al
                departamento del funcionario autenticado. Esto es la "bandeja de entrada" del CU-09.
                """)
    public ResponseEntity<List<Tramite>> misPendientes(Authentication auth) {
        String funcionarioId = auth.getName(); // userId del token JWT

        // Buscar todos los nodos que pertenecen al departamento del funcionario
        // Simplificación para demo: busca todos los trámites activos donde nodoActualId != null
        // En producción: filtrar por departamentoId del funcionario
        List<Tramite> activos = tramiteRepository.findTramitesActivos();
        List<Tramite> pendientes = activos.stream()
                .filter(t -> t.getNodoActualId() != null)
                .filter(t -> !t.estaEnParalelo())
                .toList();

        return ResponseEntity.ok(pendientes);
    }

    // ─── Estado del trámite (usado por CU-09 y CU-10 en G2-C2) ──────────────
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

    // ─── Listar todos los trámites (admin) ────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar todos los trámites activos (admin)")
    public ResponseEntity<List<Tramite>> listarActivos() {
        return ResponseEntity.ok(tramiteRepository.findTramitesActivos());
    }

    // ─── Historial de estados ─────────────────────────────────────────────────
    @GetMapping("/{tramiteId}/historial")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Historial de estados del trámite (trazabilidad básica)")
    public ResponseEntity<?> historial(@PathVariable String tramiteId) {
        // El historial completo se implementa en G3-C2 con el servicio de trazabilidad
        // Aquí devolvemos el trámite con sus datos como referencia
        return ResponseEntity.ok(workflowEngine.buscarTramite(tramiteId));
    }
}
```

---

## 8. Actualizar `SecurityConfig`

Agregar las rutas del motor en la configuración de seguridad:

```java
// === RUTAS G1-C2: Trámites y Motor de Workflow ===
.requestMatchers(HttpMethod.GET, "/api/tramites/**").authenticated()
.requestMatchers(HttpMethod.POST, "/api/tramites/iniciar")
    .hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")
.requestMatchers("/api/tramites/**")
    .hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
```

---

## 9. Preparar el diagrama CRE para el demo

> Antes de probar el motor, el diagrama del flujo CRE debe estar en MongoDB con estado `publicado`. Desde C1-G5 ya existen los endpoints para esto.

### 9.1. Crear el diagrama CRE por API

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cre.bo","password":"admin12345"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Crear diagrama
DIAG=$(curl -s -X POST http://localhost:8080/api/diagramas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Flujo Nueva Conexión Residencial",
    "swimlanes": ["Atención al Cliente","Área Técnica","Área Legal","Operaciones"]
  }')
DIAG_ID=$(echo $DIAG | python -c "import sys,json; print(json.load(sys.stdin)['id'])")
```

### 9.2. Agregar los nodos del flujo CRE

```bash
# Nodo INICIO
N_INICIO=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Inicio","tipo":"inicio","orden":0}')

# Nodo ATC (Atención al Cliente)
N_ATC=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Verificar documentos","tipo":"actividad",
       "departamentoId":"<ID_ATC>","swimlane":"Atención al Cliente","orden":1}')

# Nodo FORK
N_FORK=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Fork paralelo TEC","tipo":"fork","orden":2}')

# Nodo TEC — Inspección en campo
N_TEC_INSP=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Inspección en campo","tipo":"actividad",
       "departamentoId":"<ID_TEC>","swimlane":"Área Técnica","orden":3}')

# Nodo TEC — Elaborar presupuesto
N_TEC_PRES=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Elaborar presupuesto","tipo":"actividad",
       "departamentoId":"<ID_TEC>","swimlane":"Área Técnica","orden":3}')

# Nodo JOIN
N_JOIN=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Join TEC","tipo":"join","orden":4}')

# Nodo LEG (Área Legal)
N_LEG=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Revisar contrato","tipo":"actividad",
       "departamentoId":"<ID_LEG>","swimlane":"Área Legal","orden":5}')

# Nodo DECISION
N_DEC=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"¿Contrato aprobado?","tipo":"decision","orden":6}')

# Nodo OPE (Operaciones)
N_OPE=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Cierre y conexión","tipo":"actividad",
       "departamentoId":"<ID_OPE>","swimlane":"Operaciones","orden":7}')

# Nodo FIN
N_FIN=$(curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Fin","tipo":"fin","orden":8}')
```

### 9.3. Conectar los nodos con transiciones

```bash
# INICIO → ATC
curl -s -X POST http://localhost:8080/api/diagramas/$DIAG_ID/transiciones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"nodoOrigenId\":\"<ID_INICIO>\",\"nodoDestinoId\":\"<ID_ATC>\",\"tipo\":\"secuencial\"}"

# ATC → FORK
# FORK → TEC-Inspección   (tipo: paralelo)
# FORK → TEC-Presupuesto  (tipo: paralelo)
# TEC-Inspección → JOIN   (tipo: paralelo)
# TEC-Presupuesto → JOIN  (tipo: paralelo)
# JOIN → LEG              (tipo: secuencial)
# LEG → DECISION          (tipo: secuencial)
# DECISION → OPE          (tipo: condicional, etiqueta: "si")
# DECISION → TEC-Inspección (tipo: iterativo, etiqueta: "no")
# OPE → FIN               (tipo: secuencial)
```

### 9.4. Publicar el diagrama y asignarlo a la política

```bash
# Publicar
curl -X PATCH http://localhost:8080/api/diagramas/$DIAG_ID/estado \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"estado":"publicado"}'

# Asignar a la política: actualizar politica con el diagramaId
curl -X PUT http://localhost:8080/api/politicas/<POLITICA_ID> \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"diagramaId\":\"$DIAG_ID\", \"estado\":\"activa\"}"
```

---

## 10. Probar el flujo CRE completo

### 10.1. Registrar la solicitud de trámite (CU-07)

```bash
curl -X POST http://localhost:8080/api/tramites/iniciar \
  -H "Authorization: Bearer $TOKEN_CLIENTE" \
  -H "Content-Type: application/json" \
  -d '{
    "clienteId": "<ID_CLIENTE>",
    "politicaId": "<ID_POLITICA>",
    "prioridad": 3
  }'
```

**Respuesta esperada:**
```json
{
  "id": "...",
  "codigo": "TR-2026-00001",
  "estadoActual": "En proceso",
  "nodoActualId": "<ID_NODO_ATC>",
  "clienteId": "...",
  ...
}
```

El motor ejecutó CU-08: avanzó desde INICIO → ATC automáticamente.

### 10.2. Verificar la bandeja del funcionario (CU-09)

```bash
curl http://localhost:8080/api/tramites/mis-pendientes \
  -H "Authorization: Bearer $TOKEN_FUNC_ATC"
# Respuesta: lista con el trámite recién creado en nodoActualId = nodo ATC
```

### 10.3. ATC completa su nodo → motor activa el FORK

```bash
curl -X POST http://localhost:8080/api/tramites/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_ATC>", "notas": "Documentos verificados correctamente"}'
```

**Respuesta:** `nodosParalellosActivos: [<ID_TEC_INSP>, <ID_TEC_PRES>]`, `enParalelo: true`

### 10.4. TEC completa Inspección (queda 1 rama pendiente)

```bash
curl -X POST http://localhost:8080/api/tramites/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_TEC>", "notas": "Inspección completada"}'
# Motor: queda 1 rama (TEC-Presupuesto) → no avanza aún
```

### 10.5. TEC completa Presupuesto → JOIN → avanza a Legal

```bash
curl -X POST http://localhost:8080/api/tramites/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_TEC>", "notas": "Presupuesto listo"}'
# Motor: ambas ramas completas → JOIN → LEG se desbloquea automáticamente
```

### 10.6. Legal aprueba → va a Operaciones

```bash
curl -X POST http://localhost:8080/api/tramites/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_LEG>", "decision": "si", "notas": "Contrato aprobado"}'
# Motor: decision=si → estado=Derivado → OPE se desbloquea
```

### 10.7. Legal rechaza → vuelve a Técnica (flujo iterativo)

```bash
# Alternativa al paso anterior
curl -X POST http://localhost:8080/api/tramites/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_LEG>", "decision": "no", "notas": "Falta documentación"}'
# Motor: decision=no → estado=Observado → vuelve a TEC-Inspección (iterativo)
```

### 10.8. Operaciones cierra el trámite → FIN

```bash
curl -X POST http://localhost:8080/api/tramites/<TRAMITE_ID>/completar-nodo \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"funcionarioId": "<ID_FUNC_OPE>", "notas": "Conexión ejecutada. Trámite completado."}'
# Motor: OPE → FIN → estadoActual = "Aprobado", fechaCierreReal = now()
```

---

## 11. Verificar en Mongo Express tras el demo

Mostrar en tiempo real estas colecciones:

| Colección | Qué mostrar |
|-----------|-------------|
| `tramites` | `estadoActual` cambia: `En proceso → Derivado → Aprobado` |
| `secciones_expediente` | `estado` cambia: `bloqueada → en_curso → completada` por cada nodo |
| `estados_historicos` | Log completo del recorrido del trámite por cada departamento |
| `expedientes` | Único expediente con todas las secciones del trámite |

---

## 12. Checklist de entregables G1-C2

- [ ] Modelo `Tramite` actualizado con `nodosParalellosActivos` + `estaEnParalelo()`
- [ ] 3 DTOs: `IniciarTramiteRequest`, `CompletarNodoRequest`, `EstadoTramiteResponse`
- [ ] `EstadoHistoricoRepository` creado
- [ ] `TramiteRepository` actualizado con `findTramitesActivos()` y `findByNodoActualIdIn()`
- [ ] `WorkflowEngineService` con los 4 tipos de flujo:
  - [ ] Lineal/secuencial (INICIO → ACTIVIDAD → ACTIVIDAD → FIN)
  - [ ] Condicional (DECISION con etiqueta "si"/"no")
  - [ ] Paralelo (FORK activa múltiples ramas → JOIN espera a todas)
  - [ ] Iterativo (DECISION "no" apunta a nodo anterior)
- [ ] `WorkflowController` con endpoints: `/iniciar`, `/completar-nodo`, `/mis-pendientes`, `/estado`
- [ ] `SecurityConfig` actualizado para rutas `/api/tramites/**`
- [ ] **CU-07:** cliente inicia trámite → recibe código único (`TR-2026-XXXXX`)
- [ ] **CU-08:** motor avanza automáticamente nodo a nodo sin intervención manual
- [ ] **CU-09:** funcionario ve el trámite en `GET /api/tramites/mis-pendientes`
- [ ] Flujo CRE completo demostrado de principio a fin en Mongo Express (secciones desbloqueándose)

---

## 13. Qué sigue (G2-C2 y G3-C2)

| Guía | CUs | Contenido |
|------|-----|-----------|
| **G2-C2** | CU-10, CU-16 | Expediente Digital: el funcionario **revisa** el expediente completo (CU-10) y **registra su informe/sección** (CU-16) con campos dinámicos, adjuntos y validaciones |
| **G3-C2** | CU-11, CU-17, CU-18 | Decisiones del flujo: **derivar** trámite (CU-11), **devolver a corregir** con observaciones (CU-17), **aprobar o rechazar** formalmente (CU-18) |
| **G4-C2** | CU-15 | Colaboración online en diagramas en tiempo real (tipo Figma) |

---

## 🛠️ Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| `Política no tiene diagrama asignado` | `diagramaId` es null en la política | Asignar vía `PUT /api/politicas/{id}` con el `diagramaId` |
| `El diagrama debe estar publicado` | El diagrama está en `borrador` | Llamar `PATCH /api/diagramas/{id}/estado` con `{"estado":"publicado"}` |
| `El diagrama no tiene nodo de inicio` | Falta insertar el nodo `tipo: "inicio"` | Agregar el nodo antes de publicar |
| `No se encontró nodo JOIN` | Las transiciones del nodo paralelo no apuntan a un JOIN | Verificar `flujos_transicion` de los nodos TEC |
| Motor queda en rama paralela | `nodosParalellosActivos` no se vacía | Llamar `completarNodo` para **cada** rama paralela |
| `Transición inválida en decision` | `etiqueta` del flujo no coincide con `decision` del request | Usar exactamente `"si"` o `"no"` (minúsculas) |
| Sección no se desbloquea | `nodoId` del nodo no coincide con el de la sección | Verificar que `SeccionExpediente.nodoId` se seteó al iniciar el trámite |

---

*Guía 1 Ciclo 2 — Motor de Workflow · CU-07 · CU-08 · CU-09 · Sistema de Gestión de Trámites*
