# Fase 1.6 · Componente expediente

> Encapsular la gestión del expediente digital (secciones, campos, adjuntos) tras `ExpedientePort`. Componente con lógica significativa pero relativamente aislado.

---

## 1. Objetivo

`workflow` actualmente accede directamente a `SeccionExpedienteRepository` y `ExpedienteDigitalRepository`. Tras esta extracción, lo hará vía `ExpedientePort.completarSeccion()`, `desbloquearSeccionDeNodo()`, etc.

---

## 2. Archivos involucrados

### Modelos y repositorios
| Origen | Destino |
|--------|---------|
| `models/ExpedienteDigital.java` | `modules/expediente/domain/ExpedienteDigital.java` |
| `models/SeccionExpediente.java` | `modules/expediente/domain/SeccionExpediente.java` |
| `models/CampoSeccion.java` | `modules/expediente/domain/CampoSeccion.java` |
| `models/Adjunto.java` | `modules/expediente/domain/Adjunto.java` |
| `repositories/ExpedienteDigitalRepository.java` | `modules/expediente/internal/ExpedienteDigitalRepository.java` |
| `repositories/SeccionExpedienteRepository.java` | `modules/expediente/internal/SeccionExpedienteRepository.java` |
| `repositories/CampoSeccionRepository.java` | `modules/expediente/internal/CampoSeccionRepository.java` |
| `repositories/AdjuntoRepository.java` | `modules/expediente/internal/AdjuntoRepository.java` |

### Service y controller
| Origen | Destino |
|--------|---------|
| `services/ExpedienteService.java` | `modules/expediente/internal/ExpedienteServiceImpl.java` |
| `controllers/ExpedienteController.java` | `modules/expediente/internal/ExpedienteController.java` |

### DTOs
| Origen | Destino |
|--------|---------|
| `dto/CompletarSeccionRequest.java` | `modules/expediente/api/dto/CompletarSeccionRequest.java` |
| `dto/GuardarSeccionRequest.java` | `modules/expediente/api/dto/GuardarSeccionRequest.java` |
| `dto/CampoValorDto.java` | `modules/expediente/api/dto/CampoValorDto.java` |

---

## 3. Estructura final

```
modules/expediente/
├── api/
│   ├── ExpedientePort.java
│   └── dto/
│       ├── CompletarSeccionRequest.java
│       ├── GuardarSeccionRequest.java
│       ├── CampoValorDto.java
│       ├── ExpedienteResponse.java          ← NUEVO
│       └── SeccionResponse.java             ← NUEVO
├── domain/
│   ├── ExpedienteDigital.java
│   ├── SeccionExpediente.java
│   ├── CampoSeccion.java
│   └── Adjunto.java
├── internal/
│   ├── ExpedienteServiceImpl.java
│   ├── ExpedienteDigitalRepository.java
│   ├── SeccionExpedienteRepository.java
│   ├── CampoSeccionRepository.java
│   ├── AdjuntoRepository.java
│   └── ExpedienteController.java
├── package-info.java
└── README.md
```

---

## 4. Definición del puerto

### `api/ExpedientePort.java`

```java
package com.example.demo.modules.expediente.api;

import com.example.demo.modules.expediente.api.dto.*;

import java.util.List;

public interface ExpedientePort {

    // ─── Crear y consultar ───────────────────────────────────────────────

    /** Crea el expediente vacío para un trámite. Devuelve el ID. */
    String crearParaTramite(String tramiteId);

    /** Devuelve el expediente completo (incluye secciones). */
    ExpedienteResponse buscarPorTramite(String tramiteId);

    // ─── Operaciones que invoca el motor de workflow ─────────────────────

    /**
     * Crea una sección bloqueada por cada nodo de tipo "actividad".
     * Devuelve la lista de IDs de secciones creadas.
     */
    List<String> crearSeccionesIniciales(String expedienteId,
                                          List<NodoSeccionInfo> nodosActividad);

    /** Desbloquea (estado=en_curso) la sección asociada a un nodo. */
    void desbloquearSeccionDeNodo(String expedienteId, String nodoId);

    /** Marca una sección como completada por su nodoId. Devuelve la sección. */
    SeccionResponse completarSeccionDeNodo(String expedienteId, String nodoId,
                                             String funcionarioId);

    /** Lectura: secciones de un expediente (ordenadas). */
    List<SeccionResponse> listarSecciones(String expedienteId);

    // ─── Operaciones que invoca el funcionario desde la UI ───────────────

    SeccionResponse guardarSeccion(String seccionId, GuardarSeccionRequest req,
                                    String funcionarioId);

    SeccionResponse completarSeccion(String seccionId, CompletarSeccionRequest req,
                                      String funcionarioId);
}
```

> **Nota:** `NodoSeccionInfo` es un DTO público en `api/dto/NodoSeccionInfo.java` con `{ nodoId, departamentoId, ordenSeccion }`. Lo usa `workflow` para decirle a `expediente` qué secciones crear, sin que `expediente` tenga que conocer el modelo `NodoDiagrama` de `workflow-design`.

---

## 5. Pasos de migración

### Paso A — Mover archivos
Refactor → Move sobre la lista del punto 2.

### Paso B — Crear puerto, DTOs públicos y `NodoSeccionInfo`

### Paso C — Adaptar `ExpedienteServiceImpl`

- Renombrar `ExpedienteService.java` → `ExpedienteServiceImpl.java`
- Implementar `ExpedientePort`
- **Mover desde `WorkflowEngineService`** la lógica de:
  - Creación inicial de secciones (hoy está en el bucle de `iniciarTramite`)
  - `desbloquearSeccion` (hoy es helper privado de WorkflowEngineService)
  - `completarSeccionDeNodo` (hoy también está dentro de `completarNodo` de workflow)

Esto aliviana el motor y deja la responsabilidad correcta en `expediente`.

### Paso D — Adaptar `ExpedienteController`
Inyectar `ExpedientePort`, no el repositorio.

### Paso E — Adaptar `WorkflowEngineService`

Cambios concretos en el motor:

```java
// Antes:
@Autowired private ExpedienteDigitalRepository expedienteRepository;
@Autowired private SeccionExpedienteRepository seccionRepository;

// Después:
@Autowired private ExpedientePort expedientes;
```

En `iniciarTramite`:
```java
// Antes: 30 líneas creando expediente + secciones manualmente
// Después:
String expedienteId = expedientes.crearParaTramite(tramite.getId());
List<NodoSeccionInfo> nodosActividad = todosLosNodos.stream()
    .filter(n -> "actividad".equals(n.getTipo()))
    .map(n -> new NodoSeccionInfo(n.getId(), n.getDepartamentoId(), n.getOrden()))
    .toList();
expedientes.crearSeccionesIniciales(expedienteId, nodosActividad);
tramite.setExpedienteId(expedienteId);
```

En `completarNodo`:
```java
SeccionResponse seccion = expedientes.completarSeccionDeNodo(
    tramite.getExpedienteId(), nodoIdActivo, req.getFuncionarioId());
```

En `procesarNodo` (cuando llega a una actividad):
```java
expedientes.desbloquearSeccionDeNodo(tramite.getExpedienteId(), nodo.getId());
```

### Paso F — `package-info.java` y README

```java
/**
 * Componente: expediente
 *
 * Propósito:
 *   Gestión del expediente digital de cada trámite: secciones por nodo,
 *   campos, adjuntos, transición de estados (bloqueada → en_curso → completada).
 *
 * Puerto público:
 *   - ExpedientePort
 *
 * Consume: ninguno
 *
 * Es consumido por:
 *   - workflow (crea expediente, desbloquea secciones, marca completadas)
 *   - ai-integration (transcripcion de voz rellena la sección activa)
 *
 * Colecciones MongoDB:
 *   - expedientes_digitales
 *   - secciones_expediente
 *   - campos_seccion
 *   - adjuntos
 */
package com.example.demo.modules.expediente;
```

---

## 6. Verificación

| Flujo | Esperado |
|-------|----------|
| Iniciar trámite | Se crea expediente + secciones (todas `bloqueada` excepto la del primer nodo activo) |
| Avanzar al siguiente nodo | La sección del nodo destino pasa a `en_curso` |
| Completar nodo | La sección actual queda `completada` con `fechaCompletado` |
| GET `/api/expedientes/tramite/{id}` | 200 + expediente completo con secciones |
| POST `/api/expedientes/seccion/{id}/completar` | 200 |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente expediente con ExpedientePort y mover lógica de secciones desde workflow"
```

---

## 8. Riesgos

- Mover lógica desde `WorkflowEngineService` a `ExpedienteServiceImpl` es la parte más delicada. Hacer paso a paso, verificando que el flujo de iniciar/completar trámite siga funcionando entre cada cambio.
- El método `completarSeccionDeNodo` debe replicar el comportamiento del fallback actual de `WorkflowEngineService` (búsqueda por nodoId con caída a búsqueda por departamentoId).

---

## Próximo paso

Continuar con **`07_componente_workflow_design.md`**.
