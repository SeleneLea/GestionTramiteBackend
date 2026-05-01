# Guía 2 — Ciclo 2: Expediente Digital

**Ciclo 2 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Implementar la visualización y gestión del Expediente Digital. Al terminar esta guía, el funcionario podrá revisar el contexto completo del trámite (CU-10) y llenar/completar formalmente los campos de su sección habilitando el progreso automático del flujo (CU-16).

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-10** | Revisar información del trámite | Funcionario | `GET /api/expedientes/tramite/{tramiteId}` — Retorna el expediente único con todas sus secciones y campos, indicando cuáles son de solo lectura y cuál es editable. |
| **CU-16** | Registrar informe de actividad | Funcionario | `PUT /api/expedientes/seccion/{seccionId}` — Guarda los datos en la sección (borrador).<br>`POST /api/expedientes/seccion/{seccionId}/completar` — Cierra la sección y dispara al Motor de Workflow para avanzar al siguiente nodo. |

---

## 2. Modificaciones al Repositorio

Asegurarse de que los repositorios existan en `src/main/java/com/example/demo/repositories/`:

### `ExpedienteDigitalRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.ExpedienteDigital;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpedienteDigitalRepository extends MongoRepository<ExpedienteDigital, String> {
    Optional<ExpedienteDigital> findByTramiteId(String tramiteId);
}
```

### `SeccionExpedienteRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.SeccionExpediente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeccionExpedienteRepository extends MongoRepository<SeccionExpediente, String> {
    // El campo en el modelo es "ordenSeccion" — Spring Data deriva el nombre del método de él
    List<SeccionExpediente> findByExpedienteIdOrderByOrdenSeccionAsc(String expedienteId);
    List<SeccionExpediente> findByExpedienteId(String expedienteId);
}
```

### `CampoSeccionRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.CampoSeccion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampoSeccionRepository extends MongoRepository<CampoSeccion, String> {
    List<CampoSeccion> findBySeccionId(String seccionId);
}
```

---

## 3. DTOs para el Expediente

Crear los DTOs en `src/main/java/com/example/demo/dto/`:

### `CampoValorDto.java`
```java
package com.example.demo.dto;

import lombok.Data;

@Data
public class CampoValorDto {
    private String campoId;
    private String valor; // El valor ingresado por el usuario o extraído de la voz
}
```

### `GuardarSeccionRequest.java` (Para borrador CU-16)
```java
package com.example.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class GuardarSeccionRequest {
    private List<CampoValorDto> campos;
    private String notasOperativas;
}
```

### `CompletarSeccionRequest.java` (Para cierre CU-16)
```java
package com.example.demo.dto;

import lombok.Data;

@Data
public class CompletarSeccionRequest {
    private String notasOperativas;
    // Obligatorio si el nodo actual en el flujo es de tipo 'decision'
    private String decisionTomada; 
}
```

---

## 4. ExpedienteService (Lógica de CU-10 y CU-16)

Crear el servicio en `src/main/java/com/example/demo/services/ExpedienteService.java`.
> **Nota:** Este servicio se integrará con el `WorkflowEngineService` cuando una sección sea "completada" para disparar el evento de avance.

```java
package com.example.demo.services;

import com.example.demo.dto.CampoValorDto;
import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.CompletarSeccionRequest;
import com.example.demo.dto.GuardarSeccionRequest;
import com.example.demo.models.CampoSeccion;
import com.example.demo.models.ExpedienteDigital;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.CampoSeccionRepository;
import com.example.demo.repositories.ExpedienteDigitalRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpedienteService {

    @Autowired
    private ExpedienteDigitalRepository expedienteRepository;

    @Autowired
    private SeccionExpedienteRepository seccionRepository;

    @Autowired
    private CampoSeccionRepository campoRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private WorkflowEngineService workflowEngineService;

    // CU-10: Obtener expediente completo con sus secciones y campos
    public Map<String, Object> obtenerExpedienteCompleto(String tramiteId) {
        ExpedienteDigital expediente = expedienteRepository.findByTramiteId(tramiteId)
                .orElseThrow(() -> new RuntimeException("Expediente no encontrado para el trámite: " + tramiteId));

        List<SeccionExpediente> secciones = seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(expediente.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("expediente", expediente);
        
        List<Map<String, Object>> seccionesCompletas = secciones.stream().map(seccion -> {
            Map<String, Object> secMap = new HashMap<>();
            secMap.put("infoSeccion", seccion);
            secMap.put("campos", campoRepository.findBySeccionId(seccion.getId()));
            return secMap;
        }).toList();
        
        response.put("secciones", seccionesCompletas);
        return response;
    }

    // CU-16: Guardar valores de los campos como borrador
    public SeccionExpediente guardarSeccion(String seccionId, GuardarSeccionRequest request, String funcionarioId) {
        SeccionExpediente seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new RuntimeException("Sección no encontrada"));

        // El motor escribe "en_curso" en minúsculas (ver G1-C2 WorkflowEngineService)
        if (!"en_curso".equals(seccion.getEstado())) {
            throw new IllegalStateException("Solo se pueden editar secciones en estado 'en_curso'");
        }

        // Actualizar valores de los campos
        // El campo en CampoSeccion es "valor" (ver base mermaid.md — no existe "valorIngresado")
        if (request.getCampos() != null) {
            for (CampoValorDto cv : request.getCampos()) {
                campoRepository.findById(cv.getCampoId()).ifPresent(campo -> {
                    if (campo.getSeccionId().equals(seccionId)) {
                        campo.setValor(cv.getValor());
                        campo.setFechaGuardado(LocalDateTime.now());
                        campoRepository.save(campo);
                    }
                });
            }
        }

        // SeccionExpediente solo tiene funcionarioId (no notasFuncionario ni fechaModificacion)
        seccion.setFuncionarioId(funcionarioId);

        return seccionRepository.save(seccion);
    }

    // CU-16 + Disparo CU-08: Completar sección y avanzar workflow
    public Tramite completarSeccionYAvanzar(String seccionId, CompletarSeccionRequest request, String funcionarioId) {
        SeccionExpediente seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new RuntimeException("Sección no encontrada"));

        // Estado en minúsculas — consistente con lo que escribe el motor en G1-C2
        seccion.setEstado("completada");
        seccion.setFuncionarioId(funcionarioId);
        seccion.setFechaCompletado(LocalDateTime.now()); // campo real del modelo
        seccionRepository.save(seccion);

        // Usar orElseThrow en vez de .get() para mensajes de error útiles
        ExpedienteDigital exp = expedienteRepository.findById(seccion.getExpedienteId())
                .orElseThrow(() -> new IllegalStateException("Expediente no encontrado"));
        Tramite tramite = tramiteRepository.findById(exp.getTramiteId())
                .orElseThrow(() -> new IllegalStateException("Trámite no encontrado"));

        // Armar el request para el motor — incluye funcionarioId (campo @NotBlank en G1-C2)
        CompletarNodoRequest engineRequest = new CompletarNodoRequest();
        engineRequest.setFuncionarioId(funcionarioId);
        engineRequest.setDecision(request.getDecisionTomada());
        engineRequest.setNotas(request.getNotasOperativas());

        // El método del motor es completarNodo(tramiteId, request) — ver G1-C2 WorkflowEngineService
        return workflowEngineService.completarNodo(tramite.getId(), engineRequest);
    }
}
```

---

## 5. ExpedienteController (Endpoints REST)

Crear `src/main/java/com/example/demo/controllers/ExpedienteController.java`:

```java
package com.example.demo.controllers;

import com.example.demo.dto.CompletarSeccionRequest;
import com.example.demo.dto.GuardarSeccionRequest;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.services.ExpedienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/expedientes")
@Tag(name = "Expediente Digital", description = "CU-10 Revisar Expediente · CU-16 Informe de Actividad")
public class ExpedienteController {

    @Autowired
    private ExpedienteService expedienteService;

    @GetMapping("/tramite/{tramiteId}")
    @Operation(summary = "Obtener Expediente", description = "CU-10. Retorna el expediente único centralizado con todas sus secciones y campos.")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR', 'CLIENTE')")
    public ResponseEntity<Map<String, Object>> getExpediente(@PathVariable String tramiteId) {
        return ResponseEntity.ok(expedienteService.obtenerExpedienteCompleto(tramiteId));
    }

    @PutMapping("/seccion/{seccionId}")
    @Operation(summary = "Guardar sección (borrador)", description = "CU-16. Permite al funcionario guardar valores de la sección activa sin finalizar el nodo.")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    public ResponseEntity<SeccionExpediente> guardarSeccionBorrador(
            @PathVariable String seccionId,
            @RequestBody GuardarSeccionRequest request,
            Authentication authentication) {
        
        String usuarioId = authentication.getName(); // Asumiendo jwt username o principal id
        return ResponseEntity.ok(expedienteService.guardarSeccion(seccionId, request, usuarioId));
    }

    @PostMapping("/seccion/{seccionId}/completar")
    @Operation(summary = "Completar sección y avanzar Workflow", description = "CU-16. Cierra la sección activa y dispara el CU-08 para avanzar el trámite al siguiente nodo en el diagrama.")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')")
    public ResponseEntity<?> completarSeccion(
            @PathVariable String seccionId,
            @RequestBody CompletarSeccionRequest request,
            Authentication authentication) {

        String usuarioId = authentication.getName();
        return ResponseEntity.ok(expedienteService.completarSeccionYAvanzar(seccionId, request, usuarioId));
    }
}
```

---

## 6. Actualizar Configuración de Seguridad

Agregar las nuevas rutas en `src/main/java/com/example/demo/config/SecurityConfig.java` (Sección de expedientes):

```java
// === RUTAS G2-C2: Expediente Digital ===
.requestMatchers(HttpMethod.GET, "/api/expedientes/tramite/**")
    .hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")
.requestMatchers("/api/expedientes/seccion/**")
    .hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
```

---

## 7. Prueba del Flujo Conjunto (G1 + G2 Ciclo 2)

### 7.1. Paso a paso completo

```bash
# 1. Login como funcionario
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"funcionario@cre.bo","password":"func12345"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 2. El cliente ya inició el trámite (G1-C2). Ver el estado:
curl http://localhost:8080/api/tramites/<TRAMITE_ID>/estado \
  -H "Authorization: Bearer $TOKEN"
# → estadoActual: "En proceso", nodoActualId: <ID_NODO_ATC>

# 3. Funcionario ATC abre el expediente completo (CU-10)
curl http://localhost:8080/api/expedientes/tramite/<TRAMITE_ID> \
  -H "Authorization: Bearer $TOKEN"
# → expediente con secciones: sección ATC en "en_curso", resto en "bloqueada"

# 4. Funcionario ATC guarda borrador de su sección (CU-16 — borrador)
curl -X PUT http://localhost:8080/api/expedientes/seccion/<SECCION_ATC_ID> \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "campos": [
      {"campoId": "<CAMPO_ID_1>", "valor": "Juan Pérez"},
      {"campoId": "<CAMPO_ID_2>", "valor": "Documentación completa"}
    ]
  }'

# 5. Funcionario ATC completa la sección → motor avanza (CU-16 + CU-08)
curl -X POST http://localhost:8080/api/expedientes/seccion/<SECCION_ATC_ID>/completar \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"notasOperativas": "Documentos verificados y aprobados"}'
# → sección ATC pasa a "completada", motor activa FORK, secciones TEC pasan a "en_curso"
```

### 7.2. Verificar en Mongo Express

| Colección | Qué verificar |
|-----------|---------------|
| `secciones_expediente` | Sección ATC: `estado = "completada"`, `fechaCompletado` seteado |
| `secciones_expediente` | Secciones TEC: `estado = "en_curso"`, `fechaAsignacion` seteado |
| `campos_seccion` | Campos de la sección ATC con `valor` guardado |
| `tramites` | `nodosParalellosActivos` activos tras el FORK |

---

## 8. Checklist de entregables G2-C2

- [ ] `ExpedienteDigitalRepository` con `findByTramiteId()`
- [ ] `SeccionExpedienteRepository` con `findByExpedienteIdOrderByOrdenSeccionAsc()` (campo `ordenSeccion`)
- [ ] `CampoSeccionRepository` con `findBySeccionId()`
- [ ] 3 DTOs: `CampoValorDto`, `GuardarSeccionRequest`, `CompletarSeccionRequest`
- [ ] `ExpedienteService` con:
  - [ ] `obtenerExpedienteCompleto()` — CU-10
  - [ ] `guardarSeccion()` — borrador CU-16, verifica `estado = "en_curso"` (minúsculas)
  - [ ] `completarSeccionYAvanzar()` — CU-16, llama a `workflowEngineService.completarNodo()`
- [ ] `ExpedienteController` con 3 endpoints:
  - [ ] `GET /api/expedientes/tramite/{tramiteId}`
  - [ ] `PUT /api/expedientes/seccion/{seccionId}`
  - [ ] `POST /api/expedientes/seccion/{seccionId}/completar`
- [ ] `SecurityConfig` actualizado con rutas del expediente
- [ ] **CU-10:** funcionario ve expediente completo con secciones anteriores bloqueadas visibles
- [ ] **CU-16:** funcionario guarda borrador y luego completa → motor avanza automáticamente
- [ ] En Mongo Express: `campos_seccion.valor` se actualiza al guardar borrador
- [ ] En Mongo Express: `secciones_expediente.estado` pasa de `en_curso` a `completada` al completar

---

## 🛠️ Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| `Solo se pueden editar secciones en estado 'en_curso'` | La sección ya está `completada` o sigue `bloqueada` | Verificar que el motor (G1) desbloqueó la sección correcta |
| `NoSuchMethodError: completarNodoActivo` | Nombre de método incorrecto | El método en `WorkflowEngineService` se llama `completarNodo(tramiteId, request)` |
| `campo.setValor()` no existe | El modelo tiene `valor` no `valorIngresado` | Revisar `CampoSeccion.java` — el campo es `private String valor` |
| `findByExpedienteIdOrderByOrdenAsc` falla en runtime | Nombre de campo incorrecto | El campo en el modelo es `ordenSeccion` → método: `...OrderByOrdenSeccionAsc` |
| `.get()` lanza `NoSuchElementException` | Optional vacío sin manejo | Usar `.orElseThrow(() -> new IllegalStateException("..."))` |
| Expediente no encontrado | El trámite no tiene expediente creado | Verificar que `POST /api/tramites/iniciar` creó el expediente en G1-C2 |

---

## 9. Qué sigue (G3-C2)

Con el expediente funcionando, **G3-C2** agrega las decisiones explícitas del flujo:

| CU | Endpoint | Descripción |
|----|----------|-------------|
| CU-11 | `POST /api/tramites/{id}/derivar` | Reasignar a otro funcionario del mismo departamento |
| CU-17 | `POST /api/tramites/{id}/devolver` | Devolver a nodo anterior con observaciones obligatorias |
| CU-18 | `POST /api/tramites/{id}/decision-final` | Aprobar o rechazar formalmente con justificación |

---

*Guía 2 Ciclo 2 — Expediente Digital · CU-10 · CU-16 · Sistema de Gestión de Trámites*
