# Fase 1.4 · Componente trazabilidad

> Encapsular el registro de auditoría con cadena de hash (hash chain) tras `TrazabilidadPort`. Pequeño, autocontenido, ideal para reforzar el patrón.

---

## 1. Objetivo

Que cualquier acción auditable del sistema se registre llamando a `TrazabilidadPort.registrar(...)` sin acoplarse al algoritmo de hash ni al repositorio.

---

## 2. Archivos involucrados

| Origen | Destino |
|--------|---------|
| `models/Trazabilidad.java` | `modules/trazabilidad/domain/Trazabilidad.java` |
| `repositories/TrazabilidadRepository.java` | `modules/trazabilidad/internal/TrazabilidadRepository.java` |
| `services/TrazabilidadService.java` | `modules/trazabilidad/internal/TrazabilidadServiceImpl.java` |

> No hay controller — la trazabilidad se consulta indirectamente vía workflow/historial. Si más adelante se quiere exponer un endpoint de auditoría, se agregará en `internal/`.

---

## 3. Estructura final

```
modules/trazabilidad/
├── api/
│   ├── TrazabilidadPort.java
│   └── dto/
│       └── EventoTrazabilidad.java          ← NUEVO, DTO público
├── domain/
│   └── Trazabilidad.java
├── internal/
│   ├── TrazabilidadServiceImpl.java
│   └── TrazabilidadRepository.java
├── package-info.java
└── README.md
```

---

## 4. Definición del puerto

### `api/TrazabilidadPort.java`

```java
package com.example.demo.modules.trazabilidad.api;

import com.example.demo.modules.trazabilidad.api.dto.EventoTrazabilidad;

import java.util.List;
import java.util.Map;

public interface TrazabilidadPort {

    /**
     * Registra un evento auditable. Vincula el hash al evento previo del trámite
     * (cadena de integridad).
     */
    EventoTrazabilidad registrar(String tramiteId, String actorId, String accion,
                                  String nodoId, Map<String, Object> datosDespues);

    /** Lista los eventos de un trámite ordenados temporalmente. */
    List<EventoTrazabilidad> listarPorTramite(String tramiteId);
}
```

### `api/dto/EventoTrazabilidad.java`

```java
package com.example.demo.modules.trazabilidad.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoTrazabilidad {
    private String id;
    private String tramiteId;
    private String actorId;
    private String accion;
    private String nodoId;
    private Map<String, Object> datosDespues;
    private LocalDateTime timestamp;
    private String hashAnterior;
    private String hashActual;
}
```

---

## 5. Pasos de migración

### Paso A — Mover archivos
Refactor → Move sobre los 3 archivos.

### Paso B — Crear puerto y DTO
Crear `TrazabilidadPort.java` y `EventoTrazabilidad.java`.

### Paso C — Adaptar la implementación

Renombrar `TrazabilidadService.java` → `TrazabilidadServiceImpl.java`, y agregar `implements TrazabilidadPort`:

```java
@Service
class TrazabilidadServiceImpl implements TrazabilidadPort {

    @Autowired
    private TrazabilidadRepository trazabilidadRepository;

    @Override
    public EventoTrazabilidad registrar(String tramiteId, String actorId, String accion,
                                         String nodoId, Map<String, Object> datosDespues) {
        Trazabilidad previa = trazabilidadRepository.findTopByTramiteIdOrderByTimestampDesc(tramiteId);
        String hashAnterior = previa != null ? previa.getHashActual() : SEED_HASH;

        Trazabilidad nueva = new Trazabilidad();
        nueva.setTramiteId(tramiteId);
        nueva.setActorId(actorId);
        nueva.setAccion(accion);
        nueva.setNodoId(nodoId);
        nueva.setDatosDespues(datosDespues);
        nueva.setTimestamp(LocalDateTime.now());
        nueva.setHashAnterior(hashAnterior);
        nueva.setHashActual(generarHash(tramiteId + accion + nueva.getTimestamp() + hashAnterior));

        return toDto(trazabilidadRepository.save(nueva));
    }

    @Override
    public List<EventoTrazabilidad> listarPorTramite(String tramiteId) {
        return trazabilidadRepository.findByTramiteIdOrderByTimestampAsc(tramiteId)
                .stream().map(this::toDto).toList();
    }

    private static final String SEED_HASH =
        "0000000000000000000000000000000000000000000000000000000000000000";

    private String generarHash(String input) { /* SHA-256 igual que antes */ }

    private EventoTrazabilidad toDto(Trazabilidad t) { /* mapping directo */ }
}
```

> **Posible método nuevo en el repositorio:** `findByTramiteIdOrderByTimestampAsc`. Verificar que existe; si no, agregarlo.

### Paso D — Adaptar consumidores

`WorkflowEngineService` ya usa `trazabilidadService.registrar(...)` — solo hay que cambiar la inyección a `TrazabilidadPort`:

```java
@Autowired private TrazabilidadPort trazabilidad;

trazabilidad.registrar(tramite.getId(), userId, "iniciar",
                        tramite.getNodoActualId(), Map.of("politicaId", req.getPoliticaId()));
```

### Paso E — `package-info.java`

```java
/**
 * Componente: trazabilidad
 *
 * Propósito:
 *   Registrar eventos auditables con cadena de hash SHA-256 que garantiza
 *   la integridad e inmutabilidad de la auditoría (hash chain).
 *
 * Puerto público:
 *   - TrazabilidadPort
 *
 * Consume: ninguno
 *
 * Es consumido por:
 *   - workflow (al iniciar trámite, completar nodo, derivar, devolver, decidir)
 *
 * Colecciones MongoDB:
 *   - trazabilidad
 */
package com.example.demo.modules.trazabilidad;
```

---

## 6. Verificación

| Flujo | Esperado |
|-------|----------|
| Iniciar trámite | Aparece registro en colección `trazabilidad` con `accion=iniciar` |
| Completar nodo | Aparece registro con `accion=completar_nodo` |
| Verificar `hashAnterior` apunta al `hashActual` previo del mismo trámite | Cadena íntegra |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente trazabilidad con TrazabilidadPort y hash chain"
```

---

## Próximo paso

Continuar con **`05_componente_metricas.md`**.
