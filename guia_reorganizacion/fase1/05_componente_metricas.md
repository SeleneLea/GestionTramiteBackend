# Fase 1.5 · Componente metricas

> Componente que mide tiempos de actividades vs SLA y detecta cuellos de botella. Componente "hoja" sin dependencias hacia otros.

---

## 1. Objetivo

Que `workflow` registre métricas de tiempo cuando un funcionario completa una actividad llamando a `MetricasPort.registrarMetricaActividad(...)`, y que el análisis nocturno de cuellos de botella sea independiente.

---

## 2. Archivos involucrados

| Origen | Destino |
|--------|---------|
| `models/MetricaTiempo.java` | `modules/metricas/domain/MetricaTiempo.java` |
| `models/CuelloBotella.java` | `modules/metricas/domain/CuelloBotella.java` |
| `repositories/MetricaTiempoRepository.java` | `modules/metricas/internal/MetricaTiempoRepository.java` |
| `repositories/CuelloBotellaRepository.java` | `modules/metricas/internal/CuelloBotellaRepository.java` |
| `services/MetricaYCuelloService.java` | `modules/metricas/internal/MetricasServiceImpl.java` |
| `controllers/MetricaController.java` | `modules/metricas/internal/MetricaController.java` |

> ⚠️ `MetricaYCuelloService` accede a `ActividadRepository` directamente (de catalogo). Para el corto plazo lo dejamos así (lo documentamos como deuda); en una iteración futura iría vía `CatalogoPort.buscarActividad(id)`.

---

## 3. Estructura final

```
modules/metricas/
├── api/
│   ├── MetricasPort.java
│   └── dto/
│       ├── MetricaTiempoResponse.java
│       └── CuelloBotellaResponse.java
├── domain/
│   ├── MetricaTiempo.java
│   └── CuelloBotella.java
├── internal/
│   ├── MetricasServiceImpl.java
│   ├── MetricaTiempoRepository.java
│   ├── CuelloBotellaRepository.java
│   └── MetricaController.java
├── package-info.java
└── README.md
```

---

## 4. Definición del puerto

### `api/MetricasPort.java`

```java
package com.example.demo.modules.metricas.api;

import com.example.demo.modules.metricas.api.dto.*;

import java.time.LocalDateTime;
import java.util.List;

public interface MetricasPort {

    /** Registra el tiempo que tomó completar una actividad y si superó el SLA. */
    void registrarMetricaActividad(String tramiteId, String actividadId,
                                    String departamentoId,
                                    LocalDateTime inicio, LocalDateTime fin);

    /** Métricas por trámite (lectura). */
    List<MetricaTiempoResponse> listarPorTramite(String tramiteId);

    /** Cuellos de botella detectados, más recientes primero. */
    List<CuelloBotellaResponse> listarCuellosBotella();
}
```

---

## 5. Pasos de migración

### Paso A — Mover archivos
Refactor → Move sobre los 6 archivos.

### Paso B — Crear puerto y DTOs

### Paso C — Adaptar `MetricasServiceImpl`

Renombrar `MetricaYCuelloService.java` → `MetricasServiceImpl.java`, hacer que implemente `MetricasPort`:

```java
@Service
class MetricasServiceImpl implements MetricasPort {

    @Autowired private MetricaTiempoRepository metricaRepo;
    @Autowired private CuelloBotellaRepository cuelloRepo;
    @Autowired private ActividadRepository actividadRepository;  // ⚠️ deuda: debería ir por CatalogoPort

    @Override
    public void registrarMetricaActividad(String tramiteId, String actividadId,
                                           String departamentoId,
                                           LocalDateTime inicio, LocalDateTime fin) {
        // misma lógica de hoy
    }

    @Override
    public List<MetricaTiempoResponse> listarPorTramite(String tramiteId) {
        return metricaRepo.findByTramiteId(tramiteId)
                .stream().map(this::toMetricaResponse).toList();
    }

    @Override
    public List<CuelloBotellaResponse> listarCuellosBotella() {
        return cuelloRepo.findAllByOrderByFechaDeteccionDesc()
                .stream().map(this::toCuelloResponse).toList();
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void analizarCuellosDeBotella() {
        // misma lógica de hoy
    }
}
```

### Paso D — Adaptar `MetricaController`

Cambiar la inyección a `MetricasPort` y devolver los DTOs:

```java
@RestController
@RequestMapping("/api/metricas")
class MetricaController {

    @Autowired private MetricasPort metricas;

    @GetMapping("/tramite/{tramiteId}")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<List<MetricaTiempoResponse>> porTramite(@PathVariable String tramiteId) {
        return ResponseEntity.ok(metricas.listarPorTramite(tramiteId));
    }

    @GetMapping("/cuellos-botella")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<CuelloBotellaResponse>> cuellos() {
        return ResponseEntity.ok(metricas.listarCuellosBotella());
    }
}
```

### Paso E — Adaptar `WorkflowEngineService`
Cambiar `MetricaYCuelloService` por `MetricasPort` en la inyección:

```java
@Autowired private MetricasPort metricas;

// en completarNodo:
if ("actividad".equals(nodoActual.getTipo())) {
    metricas.registrarMetricaActividad(
        tramite.getId(), nodoActual.getActividadId(),
        nodoActual.getDepartamentoId(),
        seccion.getFechaAsignacion(), seccion.getFechaCompletado()
    );
}
```

### Paso F — `package-info.java` y README

---

## 6. Verificación

| Flujo | Esperado |
|-------|----------|
| Completar nodo de actividad | Aparece documento en `metricas_tiempo` con `superoSla` correcto |
| GET `/api/metricas/tramite/{id}` | 200 + lista |
| GET `/api/metricas/cuellos-botella` (admin) | 200 |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente metricas con MetricasPort"
```

---

## 8. Deuda técnica documentada

Anotar en el README del componente:

> **Deuda:** `MetricasServiceImpl` accede a `ActividadRepository` directamente para leer SLA de actividad. En una iteración futura, esa lectura debería pasar por `CatalogoPort.buscarActividad(id)`.

---

## Próximo paso

Continuar con **`06_componente_expediente.md`**.
