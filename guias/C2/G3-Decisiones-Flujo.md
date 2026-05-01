# Guía 3 — Ciclo 2: Decisiones del Flujo del Trámite

**Ciclo 2 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> **Objetivo:** Implementar la lógica de toma de decisiones avanzadas por parte de los funcionarios durante el ciclo de vida de un trámite. Al finalizar esta guía, los usuarios podrán reasignar un trámite dentro de su mismo departamento (CU-11), retrocederlo por observaciones (CU-17) y emitir un veredicto definitivo de aprobación o rechazo final (CU-18).

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-11** | Derivar trámite | Funcionario | `POST /api/tramites/{id}/derivar` — Cambia el `funcionarioActualId` dentro del mismo departamento sin avanzar el nodo. |
| **CU-17** | Devolver trámite a corregir | Funcionario | `POST /api/tramites/{id}/devolver` — Retrocede el motor a un nodo anterior y pasa el estado a `"Observado"`. |
| **CU-18** | Aprobar o rechazar trámite | Funcionario | `POST /api/tramites/{id}/decision-final` — Veredicto formal que provoca el salto del flujo vía `WorkflowEngineService`. |

---

## 2. Definición de Repositorios

Para auditar correctamente estas transacciones se necesita el repositorio de trazabilidad. `TramiteRepository` y `EstadoHistoricoRepository` ya están definidos en `G1-C2`.

### `TrazabilidadRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.Trazabilidad;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrazabilidadRepository extends MongoRepository<Trazabilidad, String> {
    // campo en el modelo: "timestamp"  (no "fechaAccion")
    List<Trazabilidad> findByTramiteIdOrderByTimestampDesc(String tramiteId);
}
```

---

## 3. DTOs (Data Transfer Objects)

Crear las siguientes clases en el paquete `com.example.demo.dto`:

### 3.1. `DerivarTramiteRequest.java` (CU-11)
```java
package com.example.demo.dto;

public class DerivarTramiteRequest {
    private String nuevoFuncionarioId;
    private String motivo;

    public String getNuevoFuncionarioId() { return nuevoFuncionarioId; }
    public void setNuevoFuncionarioId(String nuevoFuncionarioId) { this.nuevoFuncionarioId = nuevoFuncionarioId; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
```

### 3.2. `DevolverTramiteRequest.java` (CU-17)
```java
package com.example.demo.dto;

public class DevolverTramiteRequest {
    private String nodoDestinoId;
    private String observaciones;

    public String getNodoDestinoId() { return nodoDestinoId; }
    public void setNodoDestinoId(String nodoDestinoId) { this.nodoDestinoId = nodoDestinoId; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
```

### 3.3. `DecisionFinalRequest.java` (CU-18)
```java
package com.example.demo.dto;

public class DecisionFinalRequest {
    // Valores esperados: "Aprobar", "Rechazar"
    private String decision;
    private String justificacion;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getJustificacion() { return justificacion; }
    public void setJustificacion(String justificacion) { this.justificacion = justificacion; }
}
```

---

## 4. Servicio

### `TramiteDecisionService.java`

Crear en `com.example.demo.services`. Delega el avance real del flujo en `WorkflowEngineService`.

```java
package com.example.demo.services;

import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.DecisionFinalRequest;
import com.example.demo.dto.DerivarTramiteRequest;
import com.example.demo.dto.DevolverTramiteRequest;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.Tramite;
import com.example.demo.models.Trazabilidad;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.TrazabilidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class TramiteDecisionService {

    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private WorkflowEngineService workflowEngineService;
    @Autowired private TrazabilidadRepository trazabilidadRepository;
    @Autowired private EstadoHistoricoRepository estadoHistoricoRepository;

    // CU-11: Derivar (reasignar) el trámite dentro del mismo departamento
    public Tramite derivarTramite(String tramiteId, DerivarTramiteRequest request, String usuarioQueDeriva) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado: " + tramiteId));

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

        return tramite;
    }

    // CU-17: Devolver trámite a corregir — retrocede a un nodo anterior
    public Tramite devolverTramite(String tramiteId, DevolverTramiteRequest request, String usuarioResponsable) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado: " + tramiteId));

        String estadoAnterior = tramite.getEstadoActual();
        String nodoAnteriorId  = tramite.getNodoActualId();

        tramite.setNodoActualId(request.getNodoDestinoId());
        tramite.setEstadoActual("Observado");
        tramite.setFuncionarioActualId(null); // libera la bandeja para reasignación
        tramite = tramiteRepository.save(tramite);

        // Campos del modelo EstadoHistorico: tramiteId, estadoAnterior, estadoNuevo,
        // nodoAnteriorId, nodoNuevoId, actorId, motivo, fechaCambio
        EstadoHistorico historico = new EstadoHistorico();
        historico.setTramiteId(tramite.getId());
        historico.setEstadoAnterior(estadoAnterior);
        historico.setEstadoNuevo("Observado");
        historico.setNodoAnteriorId(nodoAnteriorId);
        historico.setNodoNuevoId(request.getNodoDestinoId());
        historico.setActorId(usuarioResponsable);
        historico.setMotivo(request.getObservaciones());
        historico.setFechaCambio(LocalDateTime.now());
        estadoHistoricoRepository.save(historico);

        // Campos del modelo Trazabilidad: tramiteId, actorId, accion, nodoId,
        // datosDespues, timestamp  (accion es enum: crear|editar|derivar|aprobar|rechazar|observar)
        Trazabilidad t = new Trazabilidad();
        t.setTramiteId(tramite.getId());
        t.setActorId(usuarioResponsable);
        t.setAccion("observar");
        t.setNodoId(tramite.getNodoActualId());
        t.setDatosDespues(Map.of(
            "nodoDestino", request.getNodoDestinoId(),
            "motivo", request.getObservaciones() != null ? request.getObservaciones() : ""
        ));
        t.setTimestamp(LocalDateTime.now());
        trazabilidadRepository.save(t);

        return tramite;
    }

    // CU-18: Aprobar o Rechazar el trámite (Decisión Final)
    public Tramite decisionFinal(String tramiteId, DecisionFinalRequest request, String usuarioResponsable) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado: " + tramiteId));

        boolean rechazar = "Rechazar".equalsIgnoreCase(request.getDecision());

        Trazabilidad t = new Trazabilidad();
        t.setTramiteId(tramite.getId());
        t.setActorId(usuarioResponsable);
        t.setAccion(rechazar ? "rechazar" : "aprobar");
        t.setNodoId(tramite.getNodoActualId());
        t.setDatosDespues(Map.of(
            "decision", request.getDecision(),
            "justificacion", request.getJustificacion() != null ? request.getJustificacion() : ""
        ));
        t.setTimestamp(LocalDateTime.now());
        trazabilidadRepository.save(t);

        if (rechazar) {
            tramite.setEstadoActual("Rechazado");
            return tramiteRepository.save(tramite);
        }

        // Delega al motor; "si" activa la rama positiva del nodo decisión
        // completarNodo firma: (String tramiteId, CompletarNodoRequest req)
        CompletarNodoRequest engineReq = new CompletarNodoRequest();
        engineReq.setDecision("si");
        engineReq.setFuncionarioId(usuarioResponsable);
        return workflowEngineService.completarNodo(tramite.getId(), engineReq);
    }
}
```

---

## 5. Controlador

### `TramiteDecisionController.java`

Ubicado en `com.example.demo.controllers`.

```java
package com.example.demo.controllers;

import com.example.demo.dto.DecisionFinalRequest;
import com.example.demo.dto.DerivarTramiteRequest;
import com.example.demo.dto.DevolverTramiteRequest;
import com.example.demo.models.Tramite;
import com.example.demo.services.TramiteDecisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tramites/{id}")
public class TramiteDecisionController {

    @Autowired
    private TramiteDecisionService decisionService;

    @PostMapping("/derivar")
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> derivarTramite(
            @PathVariable String id,
            @RequestBody DerivarTramiteRequest request,
            Authentication authentication) {
        Tramite t = decisionService.derivarTramite(id, request, authentication.getName());
        return ResponseEntity.ok(t);
    }

    @PostMapping("/devolver")
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> devolverTramite(
            @PathVariable String id,
            @RequestBody DevolverTramiteRequest request,
            Authentication authentication) {
        Tramite t = decisionService.devolverTramite(id, request, authentication.getName());
        return ResponseEntity.ok(t);
    }

    @PostMapping("/decision-final")
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<Tramite> decisionFinal(
            @PathVariable String id,
            @RequestBody DecisionFinalRequest request,
            Authentication authentication) {
        Tramite t = decisionService.decisionFinal(id, request, authentication.getName());
        return ResponseEntity.ok(t);
    }
}
```

---

## 6. Pruebas rápidas con curl

```bash
TOKEN="Bearer <tu_jwt>"
BASE="http://localhost:8080"
TRAMITE_ID="<id_del_tramite>"

# CU-11 — Derivar trámite a otro funcionario
curl -s -X POST "$BASE/api/tramites/$TRAMITE_ID/derivar" \
  -H "Authorization: $TOKEN" -H "Content-Type: application/json" \
  -d '{"nuevoFuncionarioId":"func456","motivo":"Vacaciones del titular"}'

# CU-17 — Devolver a corregir
curl -s -X POST "$BASE/api/tramites/$TRAMITE_ID/devolver" \
  -H "Authorization: $TOKEN" -H "Content-Type: application/json" \
  -d '{"nodoDestinoId":"nodo_revision_documentos","observaciones":"Falta cédula legible"}'

# CU-18 — Aprobar
curl -s -X POST "$BASE/api/tramites/$TRAMITE_ID/decision-final" \
  -H "Authorization: $TOKEN" -H "Content-Type: application/json" \
  -d '{"decision":"Aprobar","justificacion":"Documentación completa y válida"}'

# CU-18 — Rechazar
curl -s -X POST "$BASE/api/tramites/$TRAMITE_ID/decision-final" \
  -H "Authorization: $TOKEN" -H "Content-Type: application/json" \
  -d '{"decision":"Rechazar","justificacion":"Requisitos no cumplidos tras dos observaciones"}'
```

---

## 7. Bugs corregidos respecto a la versión anterior

| # | Archivo | Bug original | Corrección |
|---|---------|-------------|------------|
| 1 | `TrazabilidadRepository` | `findByTramiteIdOrderByFechaAccionDesc` — campo `fechaAccion` no existe | `findByTramiteIdOrderByTimestampDesc` (campo real: `timestamp`) |
| 2 | `TramiteDecisionService` | `t.setUsuarioId()`, `t.setDetalle()`, `t.setFechaAccion(new Date())` — no existen en el modelo | `setActorId()`, `setDatosDespues(Map.of(...))`, `setTimestamp(LocalDateTime.now())` |
| 3 | `TramiteDecisionService` | `historico.setEstado()`, `setNodoDiagramaId()`, `setFuncionarioId()`, `setFechaInicio()`, `setObservaciones()` | `setEstadoNuevo()`, `setNodoNuevoId()`, `setActorId()`, `setFechaCambio(LocalDateTime.now())`, `setMotivo()` |
| 4 | `TramiteDecisionService` | `workflowEngineService.completarNodo(id, "si", null)` — firma incorrecta (3 params) | Construir `CompletarNodoRequest` y llamar `completarNodo(id, engineReq)` (2 params) |
| 5 | `TramiteDecisionService` | `throw new RuntimeException(...)` | `throw new IllegalArgumentException(...)` → HTTP 400 via `GlobalExceptionHandler` |
| 6 | `TramiteDecisionController` | Sin `@PreAuthorize`; usaba `Principal` que puede ser null con JWT | Añadido `@PreAuthorize("hasRole('FUNCIONARIO')")` por método; `Authentication` inyectado por Spring |

---

## 8. Qué sigue

Con esta guía el funcionario tiene control total de su nodo activo. La guía G4 implementa **CU-15** (colaboración simultánea en diagramas de workflow).
