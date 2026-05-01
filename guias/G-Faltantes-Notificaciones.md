# G-Faltantes — Integración de Notificaciones en el Motor

**Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Conectar `NotificacionService` a los tres puntos del flujo donde los CUs exigen disparar notificaciones automáticas pero que actualmente no están cableados: avance/cierre del trámite (CU-28), derivación manual (CU-11) y cancelación por cliente (CU-19).

---

## Por qué falta esto

`NotificacionService.crearNotificacion()` existe y funciona, pero ninguno de los servicios de flujo lo llama todavía. El motor crea trazabilidad e historial pero no genera las alertas que el cliente o funcionario deben recibir según el enunciado.

---

## 1. CU-28 — Notificar al cliente cuando el trámite cambia de estado

**Archivo a modificar:** `src/main/java/com/example/demo/services/WorkflowEngineService.java`

### Paso 1 — Inyectar NotificacionService

Agregar el `@Autowired` junto al resto de dependencias al inicio de la clase (líneas 17-25 actuales):

```java
@Autowired private NotificacionService notificacionService;
```

### Paso 2 — Notificar cuando el trámite se cierra

El método `cerrarTramite` (línea ~275) cierra el trámite con Aprobado o Rechazado.
Agregar la llamada a notificación justo antes del `return tramite`:

```java
private Tramite cerrarTramite(Tramite tramite, String estadoFinal) {
    tramite.setEstadoActual(estadoFinal);
    tramite.setNodoActualId(null);
    tramite.setNodosParalellosActivos(new ArrayList<>());
    tramite.setFechaCierreReal(LocalDateTime.now());

    // CU-28: push al cliente al cerrar el trámite
    String titulo = "Aprobado".equals(estadoFinal)
            ? "Tu trámite fue aprobado"
            : "Tu trámite fue rechazado";
    String mensaje = "El trámite " + tramite.getCodigo() + " ha sido " + estadoFinal.toLowerCase() + ".";
    notificacionService.crearNotificacion(
            tramite.getClienteId(), tramite.getId(),
            "cambio_estado", titulo, mensaje, "push");

    return tramite;
}
```

### Paso 3 — Notificar cuando el trámite avanza a un nuevo nodo actividad

En el método `procesarNodo`, en el `case "actividad"` (línea ~243), agregar la notificación al cliente después de asignar el nuevo nodo:

```java
case "actividad" -> {
    desbloquearSeccion(tramite.getExpedienteId(), nodo.getId());
    tramite.setNodoActualId(nodo.getId());
    tramite.setNodosParalellosActivos(new ArrayList<>());
    tramite.setEstadoActual("En proceso");

    // CU-28: aviso web al cliente de que su trámite sigue avanzando
    notificacionService.crearNotificacion(
            tramite.getClienteId(), tramite.getId(),
            "cambio_estado",
            "Tu trámite avanzó de etapa",
            "Tu trámite " + tramite.getCodigo() + " está ahora en: " + nodo.getNombre(),
            "web");

    yield tramite;
}
```

---

## 2. CU-11 — Notificar al nuevo funcionario al derivar el trámite

**Archivo a modificar:** `src/main/java/com/example/demo/services/TramiteDecisionService.java`

### Paso 1 — Inyectar NotificacionService

Agregar junto a los `@Autowired` existentes (después de `EstadoHistoricoRepository`):

```java
@Autowired
private NotificacionService notificacionService;
```

### Paso 2 — Llamar crearNotificacion después de guardar la trazabilidad

En el método `derivarTramite`, después de `trazabilidadRepository.save(t)` y antes del `return tramite`:

```java
// CU-11 paso 7: notificación interna al funcionario receptor
notificacionService.crearNotificacion(
        request.getNuevoFuncionarioId(),
        tramite.getId(),
        "asignacion",
        "Trámite derivado a tu bandeja",
        "El trámite " + tramite.getCodigo() + " ha sido derivado a tu responsabilidad. Motivo: "
                + (request.getMotivo() != null ? request.getMotivo() : "no especificado"),
        "web");
```

**Contexto completo del método después del cambio:**

```java
public Tramite derivarTramite(String tramiteId, DerivarTramiteRequest request, String usuarioQueDeriva) {
    Tramite tramite = tramiteRepository.findById(tramiteId)
            .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado: " + tramiteId));

    String funcionarioOriginal = tramite.getFuncionarioActualId();
    tramite.setFuncionarioActualId(request.getNuevoFuncionarioId());
    tramite = tramiteRepository.save(tramite);

    Trazabilidad t = new Trazabilidad();
    t.setTramiteId(tramite.getId());
    t.setActorId(usuarioQueDeriva);
    t.setAccion("derivar");
    t.setNodoId(tramite.getNodoActualId());
    t.setDatosDespues(Map.of(
            "funcionarioAnterior", funcionarioOriginal != null ? funcionarioOriginal : "",
            "funcionarioNuevo", request.getNuevoFuncionarioId(),
            "motivo", request.getMotivo() != null ? request.getMotivo() : ""
    ));
    t.setTimestamp(LocalDateTime.now());
    trazabilidadRepository.save(t);

    // CU-11: notificación al nuevo funcionario receptor
    notificacionService.crearNotificacion(
            request.getNuevoFuncionarioId(),
            tramite.getId(),
            "asignacion",
            "Trámite derivado a tu bandeja",
            "El trámite " + tramite.getCodigo() + " ha sido derivado a tu responsabilidad. Motivo: "
                    + (request.getMotivo() != null ? request.getMotivo() : "no especificado"),
            "web");

    return tramite;
}
```

---

## 3. CU-19 — Notificar al funcionario asignado cuando el cliente cancela

**Archivo a modificar:** `src/main/java/com/example/demo/services/TramiteCicloVidaService.java`

### Paso 1 — Inyectar NotificacionService

Agregar junto a los `@Autowired` existentes:

```java
@Autowired
private NotificacionService notificacionService;
```

### Paso 2 — Guardar el funcionarioActualId ANTES de limpiarlo

En `cancelarTramite`, capturar el funcionario asignado antes de setearlo `null`:

```java
public Tramite cancelarTramite(String tramiteId, String clienteId) {
    Tramite tramite = tramiteRepository.findById(tramiteId)
            .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado"));

    if (!clienteId.equals(tramite.getClienteId())) {
        throw new IllegalArgumentException("No tiene permisos para cancelar este tramite");
    }

    if ("Aprobado".equals(tramite.getEstadoActual()) || "Rechazado".equals(tramite.getEstadoActual())) {
        throw new IllegalArgumentException("El tramite ya se encuentra en un estado final y no puede ser cancelado");
    }

    // Guardar el funcionario ANTES de limpiarlo para poder notificarlo
    String funcionarioAsignadoId = tramite.getFuncionarioActualId();

    tramite.setEstadoActual("Cancelado por el usuario");
    tramite.setFuncionarioActualId(null);
    tramite.setNodoActualId(null);
    tramite.setNodosParalellosActivos(List.of());
    tramite = tramiteRepository.save(tramite);

    Trazabilidad t = new Trazabilidad();
    t.setTramiteId(tramiteId);
    t.setActorId(clienteId);
    t.setAccion("cancelar");
    t.setNodoId(null);
    t.setDatosDespues(Map.of("motivo", "Cancelado por el cliente"));
    t.setTimestamp(LocalDateTime.now());
    trazabilidadRepository.save(t);

    // CU-19 post-condición: notificar al funcionario que tenía el trámite asignado
    if (funcionarioAsignadoId != null) {
        notificacionService.crearNotificacion(
                funcionarioAsignadoId,
                tramite.getId(),
                "cambio_estado",
                "Trámite cancelado por el cliente",
                "El trámite " + tramite.getCodigo() + " fue cancelado por su solicitante y ha sido retirado de tu bandeja.",
                "web");
    }

    return tramite;
}
```

> **Nota:** El `if (funcionarioAsignadoId != null)` es necesario porque si el trámite está en un nodo paralelo (fork activo), `getFuncionarioActualId()` puede ser `null` — en ese caso no hay un único responsable.

---

## 4. Import necesario

En los dos servicios que aún no lo tienen (`TramiteDecisionService` y `TramiteCicloVidaService`), si el compilador lo pide, agregar:

```java
import com.example.demo.services.NotificacionService;
```

Spring Boot resuelve los beans por tipo automáticamente con `@Autowired`, pero el IDE puede marcar error si no encuentra el import.

---

## Resumen de cambios

| Archivo | Cambio | CU cubierto |
|---------|--------|-------------|
| `WorkflowEngineService.java` | `@Autowired NotificacionService` + push en `cerrarTramite()` + web en `procesarNodo()` case actividad | CU-28 |
| `TramiteDecisionService.java` | `@Autowired NotificacionService` + web en `derivarTramite()` | CU-11 |
| `TramiteCicloVidaService.java` | `@Autowired NotificacionService` + capturar `funcionarioAsignadoId` antes de `setNull` + web en `cancelarTramite()` | CU-19 |

Una vez aplicados estos 3 cambios, el sistema de notificaciones quedará completamente integrado con el flujo de trabajo. El `@Scheduled` que ya existe en `NotificacionService` se encargará de despachar las notificaciones `push` y `email` pendientes de forma asíncrona.
