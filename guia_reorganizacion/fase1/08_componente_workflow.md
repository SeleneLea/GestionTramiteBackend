# Fase 1.8 · Componente workflow (NÚCLEO)

> El componente más importante y más acoplado: el motor de ejecución. Llega al final porque ya tenemos todos los Ports que va a consumir.

---

## 1. Objetivo

Encapsular el motor de workflow tras `WorkflowEnginePort`. Este Port es **el corazón funcional del sistema** — lo que un sistema externo (la app móvil, la web admin) consume para iniciar y avanzar trámites.

---

## 2. Archivos involucrados

### Modelos y repositorios
| Origen | Destino |
|--------|---------|
| `models/Tramite.java` | `modules/workflow/domain/Tramite.java` |
| `models/EstadoActual.java` | `modules/workflow/domain/EstadoActual.java` |
| `models/EstadoHistorico.java` | `modules/workflow/domain/EstadoHistorico.java` |
| `repositories/TramiteRepository.java` | `modules/workflow/internal/TramiteRepository.java` |
| `repositories/EstadoActualRepository.java` | `modules/workflow/internal/EstadoActualRepository.java` |
| `repositories/EstadoHistoricoRepository.java` | `modules/workflow/internal/EstadoHistoricoRepository.java` |

### Services
| Origen | Destino |
|--------|---------|
| `services/WorkflowEngineService.java` | `modules/workflow/internal/WorkflowEngineServiceImpl.java` |
| `services/TramiteService.java` | `modules/workflow/internal/TramiteServiceImpl.java` |
| `services/TramiteCicloVidaService.java` | `modules/workflow/internal/TramiteCicloVidaServiceImpl.java` |
| `services/TramiteDecisionService.java` | `modules/workflow/internal/TramiteDecisionServiceImpl.java` |

### Controllers
| Origen | Destino |
|--------|---------|
| `controllers/WorkflowController.java` | `modules/workflow/internal/WorkflowController.java` |
| `controllers/TramiteController.java` | `modules/workflow/internal/TramiteController.java` |
| `controllers/TramiteCicloVidaController.java` | `modules/workflow/internal/TramiteCicloVidaController.java` |
| `controllers/TramiteDecisionController.java` | `modules/workflow/internal/TramiteDecisionController.java` |
| `controllers/HistorialTramiteController.java` | `modules/workflow/internal/HistorialTramiteController.java` |

### DTOs
| Origen | Destino |
|--------|---------|
| `dto/IniciarTramiteRequest.java` | `modules/workflow/api/dto/IniciarTramiteRequest.java` |
| `dto/CompletarNodoRequest.java` | `modules/workflow/api/dto/CompletarNodoRequest.java` |
| `dto/EstadoTramiteResponse.java` | `modules/workflow/api/dto/EstadoTramiteResponse.java` |
| `dto/LineaTiempoResponse.java` | `modules/workflow/api/dto/LineaTiempoResponse.java` |
| `dto/HitoDTO.java` | `modules/workflow/api/dto/HitoDTO.java` |
| `dto/DerivarTramiteRequest.java` | `modules/workflow/api/dto/DerivarTramiteRequest.java` |
| `dto/DevolverTramiteRequest.java` | `modules/workflow/api/dto/DevolverTramiteRequest.java` |
| `dto/DecisionFinalRequest.java` | `modules/workflow/api/dto/DecisionFinalRequest.java` |

---

## 3. Estructura final

```
modules/workflow/
├── api/
│   ├── WorkflowEnginePort.java              ← interfaz principal
│   ├── TramiteQueryPort.java                ← consultas (mis-tramites, estado)
│   └── dto/
│       ├── IniciarTramiteRequest.java
│       ├── CompletarNodoRequest.java
│       ├── DerivarTramiteRequest.java
│       ├── DevolverTramiteRequest.java
│       ├── DecisionFinalRequest.java
│       ├── EstadoTramiteResponse.java
│       ├── LineaTiempoResponse.java
│       ├── HitoDTO.java
│       ├── TramiteResponse.java             ← NUEVO, vista pública del Tramite
│       └── TramiteResumen.java              ← NUEVO, vista pública para listas
├── domain/
│   ├── Tramite.java
│   ├── EstadoActual.java
│   └── EstadoHistorico.java
├── internal/
│   ├── WorkflowEngineServiceImpl.java
│   ├── TramiteServiceImpl.java
│   ├── TramiteCicloVidaServiceImpl.java
│   ├── TramiteDecisionServiceImpl.java
│   ├── TramiteRepository.java
│   ├── EstadoActualRepository.java
│   ├── EstadoHistoricoRepository.java
│   └── (todos los controllers)
├── package-info.java
└── README.md
```

---

## 4. Definición de los puertos

### `api/WorkflowEnginePort.java` — operaciones de mando

```java
package com.example.demo.modules.workflow.api;

import com.example.demo.modules.workflow.api.dto.*;

public interface WorkflowEnginePort {

    /** Inicia un nuevo trámite a partir de una política. */
    TramiteResponse iniciar(IniciarTramiteRequest req);

    /** El funcionario completa la actividad actual; el motor avanza. */
    TramiteResponse completarNodo(String tramiteId, CompletarNodoRequest req);

    /** El funcionario deriva el trámite a otro departamento. */
    TramiteResponse derivar(String tramiteId, DerivarTramiteRequest req, String funcionarioId);

    /** El funcionario devuelve el trámite con observaciones. */
    TramiteResponse devolver(String tramiteId, DevolverTramiteRequest req, String funcionarioId);

    /** Decisión final: aprobar / rechazar. */
    TramiteResponse decidir(String tramiteId, DecisionFinalRequest req, String funcionarioId);

    /** Cancelación voluntaria por el cliente (CU-19). */
    TramiteResponse cancelar(String tramiteId, String motivo, String clienteId);
}
```

### `api/TramiteQueryPort.java` — operaciones de consulta

```java
package com.example.demo.modules.workflow.api;

import com.example.demo.modules.workflow.api.dto.*;

import java.util.List;

public interface TramiteQueryPort {

    /** Estado completo del trámite (con progreso, nodo actual, historial). */
    EstadoTramiteResponse estado(String tramiteId);

    /** Línea de tiempo / hitos del trámite. */
    LineaTiempoResponse lineaTiempo(String tramiteId);

    /** Trámites del cliente, más recientes primero. */
    List<TramiteResumen> misTramites(String clienteId);

    /** Bandeja del funcionario: trámites donde es el actor activo. */
    List<TramiteResumen> pendientesDeFuncionario(String funcionarioId, boolean esAdmin);

    /** Detalle plano del trámite. */
    TramiteResponse buscar(String tramiteId);
}
```

> **Por qué dos puertos:** separación CQRS-light. Los controllers de cliente (Flutter) consumen sobre todo `TramiteQueryPort`; los de funcionario (web) consumen `WorkflowEnginePort`. Si un test quiere mockear solo lecturas, mockea `TramiteQueryPort`.

---

## 5. Pasos de migración

### Paso A — Mover modelos, repositorios y DTOs

### Paso B — Crear los dos Ports y los DTOs `TramiteResponse`/`TramiteResumen`

### Paso C — Adaptar `WorkflowEngineServiceImpl`

Renombrar el archivo. El **service principal** ahora implementa **ambos** Ports (puede dividirse en dos clases si se prefiere):

```java
@Service
class WorkflowEngineServiceImpl implements WorkflowEnginePort, TramiteQueryPort {

    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private EstadoHistoricoRepository historicoRepository;

    // Ports consumidos (todos por interfaz, no por impl)
    @Autowired private NotificacionPort notificaciones;
    @Autowired private TrazabilidadPort trazabilidad;
    @Autowired private MetricasPort metricas;
    @Autowired private ExpedientePort expedientes;
    @Autowired private WorkflowDesignPort design;
    @Autowired private CatalogoPort catalogo;       // se creará en 1.9

    @Override
    public TramiteResponse iniciar(IniciarTramiteRequest req) {
        // misma lógica de hoy, pero usando los Ports en vez de repositorios
        ...
    }

    // ... resto de métodos ...
}
```

### Paso D — Adaptar todos los controllers para usar los Ports

`WorkflowController`:
```java
@RestController
@RequestMapping("/api/tramites")
class WorkflowController {

    @Autowired private WorkflowEnginePort engine;
    @Autowired private TramiteQueryPort queries;

    @PostMapping("/iniciar")
    public ResponseEntity<TramiteResponse> iniciar(@RequestBody IniciarTramiteRequest req,
                                                    Authentication auth) {
        if (esCliente(auth)) req.setClienteId(auth.getName());
        return ResponseEntity.status(CREATED).body(engine.iniciar(req));
    }

    @GetMapping("/{id}/estado")
    public ResponseEntity<EstadoTramiteResponse> estado(@PathVariable String id) {
        return ResponseEntity.ok(queries.estado(id));
    }
    // ...
}
```

> **Importante:** los controllers ya **no** inyectan repositorios directamente. Toda la lógica de armar la respuesta de estado (que hoy está en `WorkflowController.estado()` con 80+ líneas) se mueve al Port `TramiteQueryPort.estado(...)`.

### Paso E — Liberar a `WorkflowController` de la lógica de armado

Hoy `WorkflowController.estado()` mezcla:
- Buscar tramite
- Buscar política, cliente, nodo (datos de catalogo / workflowdesign)
- Calcular progreso
- Listar histórico
- Mapear todo al response

**Toda esta lógica se mueve a `TramiteQueryPort.estado()`** dentro del componente. El controller queda en 5 líneas.

### Paso F — `package-info.java` y README

```java
/**
 * Componente: workflow (NÚCLEO)
 *
 * Propósito:
 *   Motor de ejecución de trámites. Interpreta diagramas UML 2.5 con
 *   swimlanes y orquesta el avance del trámite por sus nodos.
 *
 * Puertos públicos:
 *   - WorkflowEnginePort — operaciones de mando (iniciar, avanzar, derivar, etc.)
 *   - TramiteQueryPort — operaciones de consulta (estado, listas, línea de tiempo)
 *
 * Consume:
 *   - WorkflowDesignPort — para leer nodos y transiciones del diagrama
 *   - ExpedientePort — para crear/avanzar el expediente del trámite
 *   - NotificacionPort — para avisar a clientes y funcionarios
 *   - TrazabilidadPort — para auditar cada cambio de estado
 *   - MetricasPort — para registrar tiempos por actividad
 *   - CatalogoPort — para leer políticas, departamentos, usuarios funcionarios
 *
 * Es consumido por:
 *   - app móvil Flutter (cliente final)
 *   - web Angular (funcionario y administrador)
 *
 * Colecciones MongoDB:
 *   - tramites
 *   - estados_actuales
 *   - estados_historicos
 */
package com.example.demo.modules.workflow;
```

---

## 6. Verificación (la más exhaustiva de todas las subfases)

Ejecutar la **collection completa** del punto 3 de `00_preparacion.md`. Los 10 flujos deben pasar.

Adicionalmente:
| Caso especial | Esperado |
|---|---|
| Trámite con paralelo (fork → 2 actividades → join) | Avanza correctamente al join cuando ambas ramas terminan |
| Trámite con decisión "no" | Vuelve al nodo iterativo |
| Cancelar trámite | Estado pasa a "Cancelado" |
| Notificación al funcionario al cambiar etapa | Aparece en su bandeja |
| Trazabilidad de cada paso | Hash chain íntegro |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente workflow (núcleo) con WorkflowEnginePort y TramiteQueryPort"
```

Si el commit es muy grande, dividir en:
1. `refactor: mover modelos y repositorios de workflow`
2. `refactor: crear WorkflowEnginePort y TramiteQueryPort`
3. `refactor: adaptar WorkflowEngineServiceImpl para usar Ports de otros componentes`
4. `refactor: aliviar WorkflowController moviendo lógica de armado a TramiteQueryPort`

---

## 8. Riesgos (la subfase de mayor riesgo)

- **Romper el motor = romper toda la app**. Hacer cada cambio pequeño y verificar.
- **Dependencias hacia componentes que aún no existen**: si llega aquí antes de tener `CatalogoPort`, dejar el acceso directo a repositorios de catalogo y anotar `// TODO: 1.9 mover a CatalogoPort`.
- **Tests manuales obligatorios** después de esta subfase: ejecutar el flujo completo "iniciar trámite → completar 3 nodos → aprobar" y verificar que cliente y funcionarios reciben notificaciones, métricas se registran y trazabilidad queda íntegra.

---

## Próximo paso

Continuar con **`09_componente_catalogo.md`**.
