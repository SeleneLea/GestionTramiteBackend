# Fase 1.7 · Componente workflow-design (workflowdesign)

> Encapsular el **diseño** de diagramas (creación, edición, generación por IA, colaboración). Distinto de `workflow` que es la **ejecución**.

---

## 1. Objetivo

Que `workflow` consulte los nodos y transiciones de un diagrama vía `WorkflowDesignPort.cargarDiagrama(diagramaId)` en lugar de inyectar `NodoDiagramaRepository` y `FlujoTransicionRepository` directamente.

---

## 2. Archivos involucrados

### Modelos y repositorios
| Origen | Destino |
|--------|---------|
| `models/DiagramaWorkflow.java` | `modules/workflowdesign/domain/DiagramaWorkflow.java` |
| `models/NodoDiagrama.java` | `modules/workflowdesign/domain/NodoDiagrama.java` |
| `models/FlujoTransicion.java` | `modules/workflowdesign/domain/FlujoTransicion.java` |
| `models/ColaboracionDiagrama.java` | `modules/workflowdesign/domain/ColaboracionDiagrama.java` |
| `models/VersionDiagrama.java` | `modules/workflowdesign/domain/VersionDiagrama.java` |
| `models/FormularioPlantilla.java` | `modules/workflowdesign/domain/FormularioPlantilla.java` |
| `models/CampoPlantilla.java` | `modules/workflowdesign/domain/CampoPlantilla.java` |
| `repositories/DiagramaWorkflowRepository.java` | `modules/workflowdesign/internal/...` |
| `repositories/NodoDiagramaRepository.java` | `modules/workflowdesign/internal/...` |
| `repositories/FlujoTransicionRepository.java` | `modules/workflowdesign/internal/...` |
| `repositories/ColaboracionDiagramaRepository.java` | `modules/workflowdesign/internal/...` |
| `repositories/VersionDiagramaRepository.java` | `modules/workflowdesign/internal/...` |
| `repositories/FormularioPlantillaRepository.java` | `modules/workflowdesign/internal/...` |
| `repositories/CampoPlantillaRepository.java` | `modules/workflowdesign/internal/...` |

### Services
| Origen | Destino |
|--------|---------|
| `services/DiagramaWorkflowService.java` | `modules/workflowdesign/internal/DiagramaServiceImpl.java` |
| `services/NodoDiagramaService.java` | `modules/workflowdesign/internal/NodoServiceImpl.java` |
| `services/FlujoTransicionService.java` | `modules/workflowdesign/internal/FlujoServiceImpl.java` |
| `services/PromptFlowService.java` | `modules/workflowdesign/internal/PromptFlowServiceImpl.java` |
| `services/ColaboracionService.java` | `modules/workflowdesign/internal/ColaboracionServiceImpl.java` |

### Controllers
| Origen | Destino |
|--------|---------|
| `controllers/DiagramaWorkflowController.java` | `modules/workflowdesign/internal/...` |
| `controllers/NodoDiagramaController.java` | `modules/workflowdesign/internal/...` |
| `controllers/FlujoTransicionController.java` | `modules/workflowdesign/internal/...` |
| `controllers/PromptFlowController.java` | `modules/workflowdesign/internal/...` |
| `controllers/ColaboracionController.java` | `modules/workflowdesign/internal/...` |

### DTOs
Todos los `Diagrama*Request`, `Nodo*Request`, `Flujo*Request`, `Prompt*`, `InvitarColaboradorRequest`, `ResponderInvitacionRequest`, `PromptFlujoRequest`, `PromptFlujoResponse` → `modules/workflowdesign/api/dto/`

> ⚠️ `PromptFlowService` accede a `DepartamentoRepository`. **Deuda documentada**: en una iteración futura, esa lectura debería ir vía `CatalogoPort.listarDepartamentosActivos()`.

---

## 3. Estructura final

```
modules/workflowdesign/
├── api/
│   ├── WorkflowDesignPort.java
│   └── dto/
│       ├── DiagramaResumen.java             ← NUEVO, info pública del diagrama
│       ├── NodoInfo.java                    ← NUEVO, info pública del nodo
│       ├── TransicionInfo.java              ← NUEVO
│       ├── PromptFlujoRequest.java
│       ├── PromptFlujoResponse.java
│       └── (resto de DTOs Request/Response)
├── domain/
│   ├── DiagramaWorkflow.java
│   ├── NodoDiagrama.java
│   ├── FlujoTransicion.java
│   ├── ColaboracionDiagrama.java
│   ├── VersionDiagrama.java
│   ├── FormularioPlantilla.java
│   └── CampoPlantilla.java
├── internal/
│   ├── DiagramaServiceImpl.java
│   ├── NodoServiceImpl.java
│   ├── FlujoServiceImpl.java
│   ├── PromptFlowServiceImpl.java
│   ├── ColaboracionServiceImpl.java
│   ├── (todos los repositorios)
│   └── (todos los controllers)
├── package-info.java
└── README.md
```

---

## 4. Definición del puerto

### `api/WorkflowDesignPort.java`

```java
package com.example.demo.modules.workflowdesign.api;

import com.example.demo.modules.workflowdesign.api.dto.*;

import java.util.List;
import java.util.Optional;

public interface WorkflowDesignPort {

    /** Información pública del diagrama (sin nodos ni transiciones). */
    Optional<DiagramaResumen> buscarDiagrama(String diagramaId);

    /** Todos los nodos del diagrama, ordenados. Solo lectura para el motor. */
    List<NodoInfo> listarNodosDeDiagrama(String diagramaId);

    /** Detalle de un nodo. */
    Optional<NodoInfo> buscarNodo(String nodoId);

    /** Todas las transiciones cuyo origen es el nodo dado. */
    List<TransicionInfo> listarTransicionesDesde(String nodoOrigenId);

    /** Generación de un diagrama desde prompt en lenguaje natural. */
    PromptFlujoResponse generarDesdePrompt(PromptFlujoRequest req, String creadorId);

    // (CRUD de diagramas, nodos y transiciones se mantiene en los controllers internos
    //  porque son endpoints REST públicos para el admin, no API entre componentes)
}
```

> **Decisión clave:** los CRUDs (crear, editar, borrar) los expone el controller REST, no el Port. El Port solo expone lo que **otros componentes** necesitan: leer el diagrama y generar por prompt.

### `api/dto/NodoInfo.java`

```java
package com.example.demo.modules.workflowdesign.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodoInfo {
    private String id;
    private String diagramaId;
    private String tipo;              // inicio, actividad, decision, fork, join, fin
    private String nombre;
    private String actividadId;
    private String departamentoId;
    private String swimlane;
    private int orden;
}
```

### `api/dto/TransicionInfo.java`

```java
package com.example.demo.modules.workflowdesign.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransicionInfo {
    private String id;
    private String diagramaId;
    private String nodoOrigenId;
    private String nodoDestinoId;
    private String tipo;              // secuencial, condicional, paralelo, iterativo
    private String etiqueta;          // si, no, etc.
    private String condicion;
}
```

---

## 5. Pasos de migración

### Paso A — Mover archivos (mucho)
Hacer Move por grupos: primero modelos, luego repositorios, luego services, luego controllers, luego DTOs.

### Paso B — Crear el Port y DTOs públicos

### Paso C — `DiagramaServiceImpl implements WorkflowDesignPort`

Implementación de los métodos del Port delegando a los repositorios internos:

```java
@Service
class DiagramaServiceImpl implements WorkflowDesignPort {

    @Autowired private DiagramaWorkflowRepository diagramaRepo;
    @Autowired private NodoDiagramaRepository nodoRepo;
    @Autowired private FlujoTransicionRepository flujoRepo;
    @Autowired private PromptFlowServiceImpl promptFlowService;

    @Override
    public Optional<DiagramaResumen> buscarDiagrama(String id) {
        return diagramaRepo.findById(id).map(this::toResumen);
    }

    @Override
    public List<NodoInfo> listarNodosDeDiagrama(String diagramaId) {
        return nodoRepo.findByDiagramaId(diagramaId)
                .stream().map(this::toNodoInfo).toList();
    }

    @Override
    public Optional<NodoInfo> buscarNodo(String nodoId) {
        return nodoRepo.findById(nodoId).map(this::toNodoInfo);
    }

    @Override
    public List<TransicionInfo> listarTransicionesDesde(String nodoOrigenId) {
        return flujoRepo.findByNodoOrigenId(nodoOrigenId)
                .stream().map(this::toTransicionInfo).toList();
    }

    @Override
    public PromptFlujoResponse generarDesdePrompt(PromptFlujoRequest req, String creadorId) {
        return promptFlowService.generarDesdePrompt(req, creadorId);
    }
    // mappers privados
}
```

### Paso D — Adaptar consumidores externos (sobre todo `WorkflowEngineService`)

Cambios en `WorkflowEngineService`:

```java
// Antes:
@Autowired private DiagramaWorkflowRepository diagramaRepository;
@Autowired private NodoDiagramaRepository nodoRepository;
@Autowired private FlujoTransicionRepository flujoRepository;

// Después:
@Autowired private WorkflowDesignPort design;
```

Sustituir las llamadas:

| Antes | Después |
|-------|---------|
| `nodoRepository.findByDiagramaId(id)` | `design.listarNodosDeDiagrama(id)` |
| `nodoRepository.findById(id)` | `design.buscarNodo(id)` |
| `flujoRepository.findByNodoOrigenId(id)` | `design.listarTransicionesDesde(id)` |

> **Importante:** los métodos internos del motor que usan `NodoDiagrama` (entidad) ahora deben usar `NodoInfo` (DTO). Eso obliga a cambiar firmas como `private NodoDiagrama avanzarDesde(...)` → `private NodoInfo avanzarDesde(...)`. Es un cambio amplio pero mecánico.

### Paso E — `package-info.java` y README

```java
/**
 * Componente: workflowdesign
 *
 * Propósito:
 *   Diseño de diagramas de flujo (UML 2.5 con swimlanes): nodos, transiciones,
 *   versionado, colaboración y generación a partir de prompts en lenguaje natural.
 *
 * Puerto público:
 *   - WorkflowDesignPort
 *
 * Consume: ninguno (deuda: lectura de departamentos debería ir vía CatalogoPort)
 *
 * Es consumido por:
 *   - workflow (lee nodos y transiciones del diagrama para ejecutar el flujo)
 *
 * Colecciones MongoDB:
 *   - diagramas_workflow, nodos_diagrama, flujos_transicion
 *   - colaboracion_diagrama, versiones_diagrama
 *   - formularios_plantilla, campos_plantilla
 */
package com.example.demo.modules.workflowdesign;
```

---

## 6. Verificación

| Flujo | Esperado |
|-------|----------|
| GET `/api/diagramas` | 200 |
| POST `/api/diagramas` (admin) | 201 |
| POST `/api/workflow-design/from-prompt` | 201 + diagrama con nodos y transiciones |
| Iniciar trámite (consume nodos del diagrama vía Port) | 201 + Tramite con nodoActualId |
| Completar nodo (consume transiciones vía Port) | 200 + avanza al siguiente |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente workflowdesign con WorkflowDesignPort"
```

---

## 8. Riesgos

- Es la subfase con más archivos a mover. Hacer en bloques pequeños y compilar entre cada bloque.
- El cambio de `NodoDiagrama` → `NodoInfo` en el motor toca muchas líneas. Considerar mantener temporalmente el modelo `NodoDiagrama` como `public` y migrar a `NodoInfo` en una iteración separada **dentro de la misma subfase** si el riesgo se vuelve alto.

---

## Próximo paso

Continuar con **`08_componente_workflow.md`** — el núcleo, el más grande.
