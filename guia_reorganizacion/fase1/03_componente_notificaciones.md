# Fase 1.3 · Componente notificaciones

> Encapsular el envío y consulta de notificaciones tras un único `NotificacionPort`. Es el primer componente "consumido" por workflow, así que aquí estrenamos el patrón de inversión de dependencias.

---

## 1. Objetivo

Que cualquier componente que necesite notificar (ej: workflow al cambiar de etapa, expediente al recibir un adjunto) lo haga llamando a `NotificacionPort.enviar(...)` sin saber si la notificación va por web, push o email.

---

## 2. Archivos involucrados

| Origen | Destino |
|--------|---------|
| `models/Notificacion.java` | `modules/notificaciones/domain/Notificacion.java` |
| `models/CanalEnvio.java` | `modules/notificaciones/domain/CanalEnvio.java` |
| `repositories/NotificacionRepository.java` | `modules/notificaciones/internal/NotificacionRepository.java` |
| `repositories/CanalEnvioRepository.java` | `modules/notificaciones/internal/CanalEnvioRepository.java` |
| `services/NotificacionService.java` | `modules/notificaciones/internal/NotificacionServiceImpl.java` |
| `controllers/NotificacionController.java` | `modules/notificaciones/internal/NotificacionController.java` |

---

## 3. Estructura final

```
modules/notificaciones/
├── api/
│   ├── NotificacionPort.java
│   └── dto/
│       ├── NotificacionRequest.java         ← NUEVO
│       └── NotificacionResponse.java        ← NUEVO
├── domain/
│   ├── Notificacion.java
│   └── CanalEnvio.java
├── internal/
│   ├── NotificacionServiceImpl.java
│   ├── NotificacionRepository.java
│   ├── CanalEnvioRepository.java
│   └── NotificacionController.java
├── package-info.java
└── README.md
```

---

## 4. Definición del puerto

### `api/NotificacionPort.java`

```java
package com.example.demo.modules.notificaciones.api;

import com.example.demo.modules.notificaciones.api.dto.*;

import java.util.List;

public interface NotificacionPort {

    /** Encola/envía una notificación al destinatario. */
    NotificacionResponse enviar(NotificacionRequest req);

    /** Lista todas las notificaciones de un usuario, más recientes primero. */
    List<NotificacionResponse> listarPorDestinatario(String destinatarioId);

    /** Marca una notificación como leída. */
    NotificacionResponse marcarLeida(String notificacionId, String usuarioId);

    /** Cantidad de notificaciones no leídas de un usuario. */
    long contarNoLeidas(String destinatarioId);
}
```

### `api/dto/NotificacionRequest.java`

```java
package com.example.demo.modules.notificaciones.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionRequest {
    private String destinatarioId;
    private String tramiteId;            // opcional
    private String tipo;                 // asignacion, cambio_estado, etc.
    private String titulo;
    private String mensaje;
    private String canal;                // web, push, email
}
```

### `api/dto/NotificacionResponse.java`

```java
package com.example.demo.modules.notificaciones.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionResponse {
    private String id;
    private String destinatarioId;
    private String tramiteId;
    private String tipo;
    private String titulo;
    private String mensaje;
    private String canal;
    private boolean leida;
    private String estadoEnvio;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLeida;
}
```

---

## 5. Pasos de migración

### Paso A — Mover archivos
IntelliJ Refactor → Move sobre los archivos de la tabla del punto 2.

### Paso B — Crear el puerto y los DTOs
Crear los 3 archivos de la sección 4.

### Paso C — Adaptar `NotificacionServiceImpl`

Renombrar el archivo `NotificacionService.java` a `NotificacionServiceImpl.java` (Refactor → Rename).

Hacer que implemente `NotificacionPort`:

```java
@Service
class NotificacionServiceImpl implements NotificacionPort {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Override
    public NotificacionResponse enviar(NotificacionRequest req) {
        Notificacion n = new Notificacion();
        n.setDestinatarioId(req.getDestinatarioId());
        n.setTramiteId(req.getTramiteId());
        n.setTipo(req.getTipo());
        n.setTitulo(req.getTitulo());
        n.setMensaje(req.getMensaje());
        n.setCanal(req.getCanal());
        n.setLeida(false);
        n.setEstadoEnvio("web".equals(req.getCanal()) ? "enviada" : "pendiente");
        n.setIntentosEnvio(0);
        n.setFechaCreacion(LocalDateTime.now());
        return toResponse(notificacionRepository.save(n));
    }

    @Override
    public List<NotificacionResponse> listarPorDestinatario(String destinatarioId) {
        return notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(destinatarioId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public NotificacionResponse marcarLeida(String id, String usuarioId) {
        Notificacion n = notificacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notificación no encontrada"));
        if (!usuarioId.equals(n.getDestinatarioId())) {
            throw new IllegalArgumentException("No autorizado");
        }
        n.setLeida(true);
        n.setFechaLeida(LocalDateTime.now());
        return toResponse(notificacionRepository.save(n));
    }

    @Override
    public long contarNoLeidas(String destinatarioId) {
        return notificacionRepository.countByDestinatarioIdAndLeidaFalse(destinatarioId);
    }

    // Mantener el método @Scheduled procesarNotificacionesPendientes() interno

    private NotificacionResponse toResponse(Notificacion n) {
        return new NotificacionResponse(
                n.getId(), n.getDestinatarioId(), n.getTramiteId(),
                n.getTipo(), n.getTitulo(), n.getMensaje(), n.getCanal(),
                n.isLeida(), n.getEstadoEnvio(),
                n.getFechaCreacion(), n.getFechaLeida()
        );
    }
}
```

> **Importante:** quitar `public` de la clase para que sea package-private.

### Paso D — Adaptar `NotificacionController`

Cambiar las dependencias para usar `NotificacionPort`:

```java
@RestController
@RequestMapping("/api/notificaciones")
class NotificacionController {

    @Autowired
    private NotificacionPort notificaciones;

    @GetMapping("/mis-notificaciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificacionResponse>> mias(Authentication auth) {
        return ResponseEntity.ok(notificaciones.listarPorDestinatario(auth.getName()));
    }

    @PutMapping("/{id}/marcar-leida")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificacionResponse> marcarLeida(
            @PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(notificaciones.marcarLeida(id, auth.getName()));
    }
}
```

> Notar que el controller ya **no** inyecta `NotificacionRepository` directamente; ahora va por el puerto.

### Paso E — Adaptar consumidores externos

`WorkflowEngineService` (que se moverá en 1.8) usa `notificacionService.crearNotificacion(...)`. **Por ahora**, en este commit, cambiar la firma del consumo en WorkflowEngineService a usar `NotificacionPort.enviar(NotificacionRequest)`:

```java
// Antes:
@Autowired private NotificacionService notificacionService;

notificacionService.crearNotificacion(
    nuevoFuncionarioId, tramite.getId(), "asignacion",
    "Tramite asignado a tu bandeja",
    "El tramite " + tramite.getCodigo() + " avanzo a la etapa: " + nodo.getNombre(),
    "web"
);

// Después:
@Autowired private NotificacionPort notificaciones;

notificaciones.enviar(new NotificacionRequest(
    nuevoFuncionarioId, tramite.getId(), "asignacion",
    "Tramite asignado a tu bandeja",
    "El tramite " + tramite.getCodigo() + " avanzo a la etapa: " + nodo.getNombre(),
    "web"
));
```

Hacer el mismo cambio para todas las llamadas a `notificacionService.crearNotificacion(...)` en `WorkflowEngineService`. (Hay ~3 llamadas según el código actual.)

### Paso F — `package-info.java` y README

```java
/**
 * Componente: notificaciones
 *
 * Propósito:
 *   Enviar notificaciones (web, push, email) a usuarios cuando ocurren
 *   eventos de negocio. Procesa pendientes en background (scheduler).
 *
 * Puerto público:
 *   - NotificacionPort
 *
 * Consume: ninguno
 *
 * Es consumido por:
 *   - workflow (al cambiar de etapa, cerrar trámite, asignar funcionario)
 *   - expediente (al subir adjuntos importantes)
 *
 * Colecciones MongoDB:
 *   - notificaciones
 *   - canales_envio
 */
package com.example.demo.modules.notificaciones;
```

---

## 6. Verificación

### 6.1 Compilar
`./mvnw clean compile`

### 6.2 Tests funcionales
| Flujo | Esperado |
|-------|----------|
| Iniciar trámite | Cliente recibe notificación de "trámite avanzo de etapa" |
| Avanzar nodo | Funcionario receptor recibe "tramite asignado a tu bandeja" |
| GET `/api/notificaciones/mis-notificaciones` | 200 + lista |
| PUT `/api/notificaciones/{id}/marcar-leida` | 200 + leida=true |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente notificaciones con NotificacionPort"
```

---

## 8. Riesgos y notas

- **Scheduler `@Scheduled`**: el método `procesarNotificacionesPendientes()` debe seguir activándose. Verificar en logs que se ejecuta cada 60 segundos.
- **No exponer la entidad `Notificacion`**: el API público solo expone `NotificacionResponse`. La entidad se mantiene interna.

---

## Próximo paso

Continuar con **`04_componente_trazabilidad.md`**.
