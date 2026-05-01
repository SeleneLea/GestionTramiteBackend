# Guía 4 — Ciclo 2: Colaboración en Diagramas

**Ciclo 2 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> **Objetivo:** Implementar la funcionalidad de invitar a otros funcionarios o administradores a editar un diagrama de flujo. Al finalizar esta guía se cubrirá el **CU-15**, que restringe el número de colaboraciones simultáneas a 4 por usuario y registra un historial de cambios.

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-15** | Solicitar Colaboración en diagrama | Administrador | `POST /api/colaboracion/diagrama/{id}/invitar` — Envía invitación. Valida que el invitado no tenga más de 4 colaboraciones activas simultáneas y crea una notificación. |

---

## 2. Modificaciones a Repositorios

Para la colaboración se usan los modelos `ColaboracionDiagrama` y `Notificacion`. Asegúrate de que sus repositorios estén definidos.

### `ColaboracionDiagramaRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.ColaboracionDiagrama;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColaboracionDiagramaRepository extends MongoRepository<ColaboracionDiagrama, String> {
    List<ColaboracionDiagrama> findByDiagramaId(String diagramaId);
    // campo del modelo: invitadoId  (no "usuarioId")
    // estado values del schema: "pendiente" | "aceptada" | "rechazada"
    List<ColaboracionDiagrama> findByInvitadoIdAndEstado(String invitadoId, String estado);
    long countByInvitadoIdAndEstado(String invitadoId, String estado);
}
```

### `NotificacionRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    // campo del modelo: destinatarioId  (no "usuarioId")
    List<Notificacion> findByDestinatarioIdOrderByFechaCreacionDesc(String destinatarioId);
}
```

---

## 3. DTOs (Data Transfer Objects)

Crear las siguientes clases en `com.example.demo.dto`:

### `InvitarColaboradorRequest.java`
```java
package com.example.demo.dto;

public class InvitarColaboradorRequest {
    private String usuarioInvitadoId;
    // Valores: "editor" | "visualizador"  (mapea al campo rolColaboracion del schema)
    private String permisos;

    public String getUsuarioInvitadoId() { return usuarioInvitadoId; }
    public void setUsuarioInvitadoId(String usuarioInvitadoId) { this.usuarioInvitadoId = usuarioInvitadoId; }
    public String getPermisos() { return permisos; }
    public void setPermisos(String permisos) { this.permisos = permisos; }
}
```

### `ResponderInvitacionRequest.java`
```java
package com.example.demo.dto;

public class ResponderInvitacionRequest {
    // Valores: "ACEPTAR" | "RECHAZAR"
    private String decision;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
}
```

---

## 4. Servicio

### `ColaboracionService.java`

Regla de negocio fundamental: **no se puede invitar a un usuario si ya tiene 4 colaboraciones en estado `"aceptada"`.**

```java
package com.example.demo.services;

import com.example.demo.dto.InvitarColaboradorRequest;
import com.example.demo.models.ColaboracionDiagrama;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.Notificacion;
import com.example.demo.repositories.ColaboracionDiagramaRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ColaboracionService {

    @Autowired private ColaboracionDiagramaRepository colaboracionRepo;
    @Autowired private DiagramaWorkflowRepository diagramaRepo;
    @Autowired private NotificacionRepository notificacionRepo;

    public ColaboracionDiagrama invitar(String diagramaId, InvitarColaboradorRequest request, String adminId) {
        DiagramaWorkflow diagrama = diagramaRepo.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado: " + diagramaId));

        // Regla: máximo 4 colaboraciones activas (estado "aceptada")
        // countByInvitadoIdAndEstado es correcto — campo del modelo es invitadoId
        long activas = colaboracionRepo.countByInvitadoIdAndEstado(request.getUsuarioInvitadoId(), "aceptada");
        if (activas >= 4) {
            throw new IllegalArgumentException("El usuario ya tiene el máximo permitido de 4 diagramas en edición activa.");
        }

        // Campos del modelo ColaboracionDiagrama: diagramaId, adminInvitadorId, invitadoId,
        // rolColaboracion, estado ("pendiente"|"aceptada"|"rechazada"), fechaInvitacion, fechaRespuesta
        ColaboracionDiagrama colab = new ColaboracionDiagrama();
        colab.setDiagramaId(diagramaId);
        colab.setAdminInvitadorId(adminId);
        colab.setInvitadoId(request.getUsuarioInvitadoId());
        colab.setRolColaboracion(request.getPermisos() != null ? request.getPermisos() : "editor");
        colab.setEstado("pendiente");
        colab.setFechaInvitacion(LocalDateTime.now());
        colaboracionRepo.save(colab);

        // Campos del modelo Notificacion: destinatarioId, tramiteId, canal, tipo,
        // titulo, mensaje, leida, estadoEnvio, intentosEnvio, fechaCreacion, fechaLeida
        // tipo válidos: "cambio_estado | asignacion | sla_vencido | observacion"
        Notificacion notif = new Notificacion();
        notif.setDestinatarioId(request.getUsuarioInvitadoId());
        notif.setTitulo("Invitación a colaborar");
        notif.setMensaje("Has sido invitado a colaborar en el diagrama: " + diagrama.getNombre());
        notif.setTipo("asignacion");
        notif.setCanal("web");
        notif.setLeida(false);
        notif.setEstadoEnvio("pendiente");
        notif.setIntentosEnvio(0);
        notif.setFechaCreacion(LocalDateTime.now());
        notificacionRepo.save(notif);

        return colab;
    }

    public ColaboracionDiagrama responderInvitacion(String colaboracionId, String decision, String usuarioId) {
        ColaboracionDiagrama colab = colaboracionRepo.findById(colaboracionId)
                .orElseThrow(() -> new IllegalArgumentException("Colaboración no encontrada: " + colaboracionId));

        // campo invitadoId — no "usuarioId"
        if (!colab.getInvitadoId().equals(usuarioId)) {
            throw new IllegalArgumentException("No tienes permiso para responder a esta invitación.");
        }

        if ("ACEPTAR".equalsIgnoreCase(decision)) {
            // Doble-check de seguridad antes de aceptar
            long activas = colaboracionRepo.countByInvitadoIdAndEstado(usuarioId, "aceptada");
            if (activas >= 4) {
                throw new IllegalArgumentException("No puedes aceptar: ya tienes 4 diagramas en edición activa.");
            }
            colab.setEstado("aceptada");
        } else {
            colab.setEstado("rechazada");
        }
        colab.setFechaRespuesta(LocalDateTime.now());

        return colaboracionRepo.save(colab);
    }
}
```

---

## 5. Controlador

### `ColaboracionController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.InvitarColaboradorRequest;
import com.example.demo.dto.ResponderInvitacionRequest;
import com.example.demo.models.ColaboracionDiagrama;
import com.example.demo.services.ColaboracionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/colaboracion")
public class ColaboracionController {

    @Autowired
    private ColaboracionService colaboracionService;

    @PostMapping("/diagrama/{diagramaId}/invitar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ColaboracionDiagrama> invitarColaborador(
            @PathVariable String diagramaId,
            @RequestBody InvitarColaboradorRequest request,
            Authentication authentication) {
        ColaboracionDiagrama c = colaboracionService.invitar(diagramaId, request, authentication.getName());
        return ResponseEntity.ok(c);
    }

    @PostMapping("/{colaboracionId}/responder")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<ColaboracionDiagrama> responderInvitacion(
            @PathVariable String colaboracionId,
            @RequestBody ResponderInvitacionRequest request,
            Authentication authentication) {
        ColaboracionDiagrama c = colaboracionService.responderInvitacion(
                colaboracionId, request.getDecision(), authentication.getName());
        return ResponseEntity.ok(c);
    }
}
```

---

## 6. Pruebas rápidas con curl

```bash
TOKEN_ADMIN="Bearer <jwt_admin>"
TOKEN_FUNC="Bearer <jwt_funcionario>"
BASE="http://localhost:8080"
DIAGRAMA_ID="<id_diagrama>"

# Invitar colaborador (Admin)
curl -s -X POST "$BASE/api/colaboracion/diagrama/$DIAGRAMA_ID/invitar" \
  -H "Authorization: $TOKEN_ADMIN" -H "Content-Type: application/json" \
  -d '{"usuarioInvitadoId":"func123","permisos":"editor"}'

# Responder invitación (Funcionario)
COLAB_ID="<id_colaboracion>"
curl -s -X POST "$BASE/api/colaboracion/$COLAB_ID/responder" \
  -H "Authorization: $TOKEN_FUNC" -H "Content-Type: application/json" \
  -d '{"decision":"ACEPTAR"}'

# Rechazar invitación
curl -s -X POST "$BASE/api/colaboracion/$COLAB_ID/responder" \
  -H "Authorization: $TOKEN_FUNC" -H "Content-Type: application/json" \
  -d '{"decision":"RECHAZAR"}'
```

---

## 7. Bugs corregidos respecto a la versión anterior

| # | Archivo | Bug original | Corrección |
|---|---------|-------------|------------|
| 1 | `ColaboracionDiagramaRepository` | `findByUsuarioIdAndEstado`, `countByUsuarioIdAndEstado` — campo `usuarioId` no existe en el modelo | Cambiado a `findByInvitadoIdAndEstado`, `countByInvitadoIdAndEstado` (campo real: `invitadoId`) |
| 2 | `NotificacionRepository` | `findByUsuarioIdOrderByFechaCreacionDesc` — campo incorrecto | `findByDestinatarioIdOrderByFechaCreacionDesc` (campo real: `destinatarioId`) |
| 3 | `ColaboracionService.invitar` | `colab.setUsuarioId()`, `colab.setPermisos()`, `setEstado("PENDIENTE")`, `setFechaInicio(new Date())` | `setInvitadoId()`, `setRolColaboracion()`, `setEstado("pendiente")`, `setFechaInvitacion(LocalDateTime.now())` |
| 4 | `ColaboracionService.invitar` | `notif.setUsuarioId()`, `setTipo("SISTEMA")`, `setFechaCreacion(new Date())` | `setDestinatarioId()`, `setTipo("asignacion")`, `setFechaCreacion(LocalDateTime.now())`; añadidos `setEstadoEnvio`, `setIntentosEnvio`, `setCanal` que son `@NotNull` en el schema |
| 5 | `ColaboracionService.responderInvitacion` | `colab.getUsuarioId()`, `"ACTIVO"`, `"RECHAZADO"` | `colab.getInvitadoId()`, estado `"aceptada"`, `"rechazada"` (lowercase, valores del schema Mermaid) |
| 6 | `ColaboracionService` | `throw new RuntimeException(...)` | `throw new IllegalArgumentException(...)` → HTTP 400 via `GlobalExceptionHandler` |
| 7 | `ColaboracionController` | Sin `@PreAuthorize`; `Principal` puede ser null con JWT | `@PreAuthorize` por método; `Authentication` inyectado por Spring |

---

## 8. Observación sobre WebSockets (para el front-end)

La colaboración en tiempo real (CU-15 visual) requiere **WebSockets** (STOMP via Spring). Esta guía construye toda la lógica REST: validación de 4 ediciones simultáneas, registro en `colaboracion_diagrama`, notificaciones en `notificacion`. El acoplamiento con el visualizador web queda para el Ciclo 3 o el sprint de UI.

---

## 9. Qué sigue

Con G4 completada, el Ciclo 2 está finalizado. El siguiente paso es comenzar el **Ciclo 3** con G0-C3 como documento de contexto.
