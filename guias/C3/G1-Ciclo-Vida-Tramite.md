# Guía 1 — Ciclo 3: Ciclo de vida completo del trámite

**Ciclo 3 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Implementar la cancelación de trámites por parte de los clientes (CU-19), la consulta de la línea de tiempo de los estados (CU-21), la actualización automática del estado en segundo plano (CU-20) y fortalecer el control de ejecución del motor de workflow (CU-22).

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-19** | Cancelar trámite | Cliente | `POST /api/tramites/{id}/cancelar` — Detiene el flujo y cambia estado a "Cancelado por el usuario" mediante validaciones. |
| **CU-20** | Actualizar estado del trámite | Sistema | Actualiza automáticamente `estadoActual` al finalizar actividades en el `WorkflowEngineService`. |
| **CU-21** | Consultar estado del trámite | Cliente | `GET /api/tramites/{id}/linea-tiempo` — Devuelve una línea cronológica con hitos (`HitoDTO`). |
| **CU-22** | Controlar ejecución del workflow | Sistema | Maneja ambigüedades en el flujo con `IllegalStateException` y auditoría en `Trazabilidad`. |

---

## 2. DTOs (Data Transfer Objects)

Crear las siguientes clases en el paquete `com.example.demo.dto` para responder las peticiones de línea de tiempo:

### `HitoDTO.java`
```java
package com.example.demo.dto;

import java.time.LocalDateTime;

public class HitoDTO {
    private LocalDateTime fecha;
    private String estado;
    private String departamento;
    private String actor;
    private boolean esActual;

    // Getters y Setters
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public boolean isEsActual() { return esActual; }
    public void setEsActual(boolean esActual) { this.esActual = esActual; }
}
```

### `LineaTiempoResponse.java`
```java
package com.example.demo.dto;

import java.util.List;

public class LineaTiempoResponse {
    private String tramiteId;
    private String estadoActual;
    private List<HitoDTO> hitos;

    // Getters y Setters
    public String getTramiteId() { return tramiteId; }
    public void setTramiteId(String tramiteId) { this.tramiteId = tramiteId; }
    public String getEstadoActual() { return estadoActual; }
    public void setEstadoActual(String estadoActual) { this.estadoActual = estadoActual; }
    public List<HitoDTO> getHitos() { return hitos; }
    public void setHitos(List<HitoDTO> hitos) { this.hitos = hitos; }
}
```

---

## 3. Servicios

### `TramiteCicloVidaService.java`

Este servicio se encarga de las acciones exclusivas del ciclo de vida como la cancelación manual (CU-19) y la construcción cronológica de la historia (CU-21).

Asegurarse de tener importadas las entidades y repositorios pertinentes (los modelos y repositorios base ya se crearon en C1/C2).

```java
package com.example.demo.services;

import com.example.demo.dto.HitoDTO;
import com.example.demo.dto.LineaTiempoResponse;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.Tramite;
import com.example.demo.models.Trazabilidad;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.TrazabilidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TramiteCicloVidaService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private EstadoHistoricoRepository estadoHistoricoRepository;

    @Autowired
    private TrazabilidadRepository trazabilidadRepository;

    /**
     * CU-19: Cancelar trámite por parte del cliente
     */
    public Tramite cancelarTramite(String tramiteId, String clienteId) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
            .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        if (!tramite.getClienteId().equals(clienteId)) {
            throw new IllegalArgumentException("No tiene permisos para cancelar este trámite");
        }

        // Restricción: No se puede cancelar si ya está en una etapa final o si la política lo prohíbe
        if ("Aprobado".equals(tramite.getEstadoActual()) || "Rechazado".equals(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El trámite ya se encuentra en un estado final y no puede ser cancelado");
        }

        tramite.setEstadoActual("Cancelado por el usuario");
        tramite.setFuncionarioActualId(null);
        tramite = tramiteRepository.save(tramite);

        // Registro en Trazabilidad (CU-23 preparativo)
        // Campos del modelo: tramiteId, actorId, accion, nodoId, datosDespues, timestamp
        Trazabilidad t = new Trazabilidad();
        t.setTramiteId(tramiteId);
        t.setActorId(clienteId);
        t.setAccion("cancelar");
        t.setNodoId(tramite.getNodoActualId());
        t.setDatosDespues(Map.of("motivo", "El cliente canceló el trámite voluntariamente"));
        t.setTimestamp(LocalDateTime.now());
        trazabilidadRepository.save(t);

        return tramite;
    }

    /**
     * CU-21: Consultar línea de tiempo (historial cronológico)
     */
    public LineaTiempoResponse getLineaTiempo(String tramiteId) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
            .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));

        // Método correcto: campo Java es fechaCambio (no fechaInicio)
        List<EstadoHistorico> historial = estadoHistoricoRepository.findByTramiteIdOrderByFechaCambioAsc(tramiteId);

        LineaTiempoResponse response = new LineaTiempoResponse();
        response.setTramiteId(tramiteId);
        response.setEstadoActual(tramite.getEstadoActual());

        List<HitoDTO> hitos = new ArrayList<>();
        for (EstadoHistorico hist : historial) {
            HitoDTO hito = new HitoDTO();
            // fechaCambio ya es LocalDateTime — no requiere conversión
            hito.setFecha(hist.getFechaCambio());
            // EstadoHistorico.estadoNuevo (no .estado, que no existe)
            hito.setEstado(hist.getEstadoNuevo());
            hito.setDepartamento("Departamento Asociado"); // TODO: resolver vía nodoId → actividad → departamento
            // EstadoHistorico.actorId (no .funcionarioId)
            hito.setActor(hist.getActorId() != null ? hist.getActorId() : "Sistema");
            // EstadoHistorico.nodoNuevoId (no .nodoDiagramaId); null-safe
            String nodoHito = hist.getNodoNuevoId();
            hito.setEsActual(nodoHito != null && nodoHito.equals(tramite.getNodoActualId()));

            hitos.add(hito);
        }
        response.setHitos(hitos);

        return response;
    }
}
```

---

## 4. Adaptaciones en `WorkflowEngineService` (CU-20 y CU-22)

Para **CU-20**, cada vez que el motor avance a un nuevo nodo en `WorkflowEngineService.java`, se debe asegurar de actualizar `tramite.setEstadoActual()` (ej. cambiando a *"En proceso"*, *"Finalizado"*).

Para **CU-22**, en el método donde evaluamos reglas de transición (como `Decision` nodes o `Fork` iterativos), si falta información (ej. la sección activa no se guardó completa o la regla ambigua falla), debes lanzar `throw new IllegalStateException("Excepción de Regla de Negocio: Transición inalcanzable");`. El `GlobalExceptionHandler` ya mapea esto a un Error HTTP 500 para el cliente y permitirá que se dispare una alerta de backend al admin.

---

## 5. Controlador REST

### `TramiteCicloVidaController.java`

Ubicado en `com.example.demo.controllers`.

```java
package com.example.demo.controllers;

import com.example.demo.dto.LineaTiempoResponse;
import com.example.demo.models.Tramite;
import com.example.demo.services.TramiteCicloVidaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tramites/{id}")
public class TramiteCicloVidaController {

    @Autowired
    private TramiteCicloVidaService cicloVidaService;

    @PostMapping("/cancelar")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<Tramite> cancelarTramite(@PathVariable String id, Authentication authentication) {
        Tramite cancelado = cicloVidaService.cancelarTramite(id, authentication.getName());
        return ResponseEntity.ok(cancelado);
    }

    @GetMapping("/linea-tiempo")
    @PreAuthorize("hasAnyRole('CLIENTE','FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<LineaTiempoResponse> getLineaTiempo(@PathVariable String id) {
        LineaTiempoResponse linea = cicloVidaService.getLineaTiempo(id);
        return ResponseEntity.ok(linea);
    }
}
```

---

## Siguientes Pasos
Una vez documentado e implementado el ciclo de vida base y la vista cronológica para el cliente, el próximo paso esencial del Ciclo 3 es fortalecer la trazabilidad con firmas hash (CU-23) y empezar a reportar las métricas de atención (CU-24). Esto continuará en la Guía **G2-C3**.