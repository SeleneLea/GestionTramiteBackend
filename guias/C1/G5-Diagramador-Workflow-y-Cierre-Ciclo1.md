# Guía 5 — Diagramador de Workflow + IA + Cierre Ciclo 1

**Ciclo 1 · Sistema de Gestión de Trámites**

> 🎯 **Objetivo:** cerrar los 9 casos de uso del Ciclo 1 que faltan después de G1–G4. G4 implementó el **motor que ejecuta** el diagrama, pero faltan los endpoints para que el administrador **diseñe** ese diagrama desde la API. Esta guía agrega: CRUD del diagrama (CU-12), CRUD de nodos y transiciones (CU-13), generador por prompt con IA mock (CU-14) y CRUD de roles + permisos (completar CU-03).

---

## 0. Estado al entrar a G5

✅ **G1:** Auth JWT + Usuarios (CU-01, CU-02)
✅ **G2:** Departamentos + Actividades + Políticas (CU-04, CU-05, CU-06)
✅ **G3:** Swagger + Health + Roles solo lectura (CU-03 parcial)
✅ **G4:** Motor de Workflow ejecuta diagramas precargados en Mongo (CU-12 motor)

**Lo que falta para cerrar el Ciclo 1:**

| CU | Estado actual | Qué falta |
|----|---------------|-----------|
| **CU-03** | Solo `GET /api/roles` | POST/PUT/DELETE de roles + asignar permisos |
| **CU-12** | Motor lee diagrama | Endpoints para que el admin **cree** el lienzo (diagrama) |
| **CU-13** | — | API para agregar nodos y transiciones al lienzo |
| **CU-14** | — | Endpoint que recibe un prompt y devuelve el diagrama generado |

> 📌 **Sobre CU-14:** la guía no levanta un microservicio FastAPI real. Implementamos un **generador mock** que detecta palabras clave del prompt y genera nodos/transiciones plausibles. Para la demo del 28 de abril es suficiente — la integración real con LLM se haría en Ciclo 3.

---

## 1. Lo que vamos a construir

| Módulo | Endpoints | Quién |
|--------|-----------|-------|
| **Diagramas** | CRUD + asociar a política + publicar | Admin |
| **Nodos** | CRUD por diagrama (inicio/actividad/fork/join/decision/fin) | Admin |
| **Transiciones** | CRUD por diagrama | Admin |
| **Diseño por IA** | `POST /api/workflow-design/from-prompt` | Admin |
| **Roles** | CRUD + asignar permisos | Solo SuperUser/Administrador |
| **Permisos** | Listar (desde seed) | Admin |

---

## 2. Reglas de negocio

### Diagramas
- Un diagrama puede estar en estado `borrador` → `publicado` → `archivado`.
- Solo un diagrama `publicado` puede asignarse al `diagramaId` de una política.
- Validación obligatoria al publicar: debe tener al menos **1 nodo `inicio`**, **1 nodo `fin`** y todos los nodos deben tener salida (excepto `fin`).
- Eliminar un diagrama borra en cascada sus nodos y transiciones.

### Nodos
- `tipo` válido: `inicio`, `actividad`, `decision`, `fork`, `join`, `fin`.
- Nodos `actividad` requieren `departamentoId`. Otros tipos no.
- El campo `orden` define el orden visual y la creación de secciones del expediente.

### Transiciones
- `nodoOrigenId` y `nodoDestinoId` deben existir y pertenecer al **mismo diagrama**.
- Para nodos `decision`, las transiciones salientes deben tener `etiqueta` (`si` o `no`).
- No se permite ciclo trivial (un nodo no apunta a sí mismo) excepto en flujos iterativos legítimos.

### Roles y permisos
- Solo el `SuperUser` puede crear/eliminar roles.
- Cualquier `Administrador` puede asignar permisos a roles existentes.
- Los roles con `esSistema: true` no pueden eliminarse (Cliente, Funcionario, Administrador, SuperUser).

---

## 3. DTOs

Crear en `src/main/java/com/example/demo/dto/`:

### 3.1. `DiagramaWorkflowRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DiagramaWorkflowRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String politicaId;
    private List<String> swimlanes;
    private Map<String, Object> canvasData;
}
```

### 3.2. `DiagramaEstadoRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DiagramaEstadoRequest {

    @NotBlank
    @Pattern(regexp = "publicado|archivado",
             message = "estado debe ser 'publicado' o 'archivado'")
    private String estado;
}
```

### 3.3. `NodoDiagramaRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

@Data
public class NodoDiagramaRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank
    @Pattern(regexp = "inicio|actividad|decision|fork|join|fin",
             message = "tipo debe ser: inicio, actividad, decision, fork, join o fin")
    private String tipo;

    private String actividadId;
    private String departamentoId;
    private String swimlane;
    private String formularioPlantillaId;
    private Map<String, Object> posicion;

    @Min(value = 0, message = "El orden no puede ser negativo")
    private int orden;
}
```

### 3.4. `FlujoTransicionRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FlujoTransicionRequest {

    @NotBlank(message = "nodoOrigenId es obligatorio")
    private String nodoOrigenId;

    @NotBlank(message = "nodoDestinoId es obligatorio")
    private String nodoDestinoId;

    @NotBlank
    @Pattern(regexp = "secuencial|condicional|paralelo|iterativo",
             message = "tipo debe ser: secuencial, condicional, paralelo o iterativo")
    private String tipo;

    private String etiqueta;
    private String condicion;
}
```

### 3.5. `PromptFlujoRequest.java` (CU-14)

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptFlujoRequest {

    @NotBlank(message = "El prompt es obligatorio")
    @Size(min = 20, max = 2000, message = "El prompt debe tener entre 20 y 2000 caracteres")
    private String prompt;

    @NotBlank(message = "El nombre del diagrama es obligatorio")
    private String nombreDiagrama;

    private String politicaId;
}
```

### 3.6. `RolRequest.java` (CU-03)

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RolRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;
    private List<String> permisos;
}
```

### 3.7. `AsignarPermisosRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AsignarPermisosRequest {

    @NotEmpty(message = "Debe especificar al menos un permiso")
    private List<String> permisos;
}
```

---

## 4. Servicio y Controller de Diagramas (CU-12)

### 4.1. `DiagramaWorkflowService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.DiagramaWorkflowRequest;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DiagramaWorkflowService {

    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;

    public DiagramaWorkflow crear(DiagramaWorkflowRequest req, String creadorId) {
        if (req.getPoliticaId() != null) {
            politicaRepository.findById(req.getPoliticaId())
                    .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));
        }

        DiagramaWorkflow d = new DiagramaWorkflow();
        d.setNombre(req.getNombre());
        d.setPoliticaId(req.getPoliticaId());
        d.setCreadorId(creadorId);
        d.setSwimlanes(req.getSwimlanes());
        d.setCanvasData(req.getCanvasData());
        d.setVersionActual(1);
        d.setEstado("borrador");
        d.setGeneradoPorIa(false);
        d.setFechaCreacion(LocalDateTime.now());
        d.setUltimaModificacion(LocalDateTime.now());

        return diagramaRepository.save(d);
    }

    public List<DiagramaWorkflow> listarTodos() {
        return diagramaRepository.findAll();
    }

    public List<DiagramaWorkflow> listarPorEstado(String estado) {
        return diagramaRepository.findByEstado(estado);
    }

    public Optional<DiagramaWorkflow> buscarPorId(String id) {
        return diagramaRepository.findById(id);
    }

    public DiagramaWorkflow actualizar(String id, DiagramaWorkflowRequest req) {
        DiagramaWorkflow d = diagramaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if ("archivado".equals(d.getEstado())) {
            throw new IllegalArgumentException("No se puede editar un diagrama archivado");
        }

        d.setNombre(req.getNombre());
        d.setPoliticaId(req.getPoliticaId());
        d.setSwimlanes(req.getSwimlanes());
        d.setCanvasData(req.getCanvasData());
        d.setUltimaModificacion(LocalDateTime.now());

        return diagramaRepository.save(d);
    }

    public DiagramaWorkflow cambiarEstado(String diagramaId, String nuevoEstado) {
        DiagramaWorkflow d = diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if ("publicado".equals(nuevoEstado)) {
            validarParaPublicar(d);
        }

        d.setEstado(nuevoEstado);
        d.setUltimaModificacion(LocalDateTime.now());
        return diagramaRepository.save(d);
    }

    private void validarParaPublicar(DiagramaWorkflow d) {
        List<NodoDiagrama> nodos = nodoRepository.findByDiagramaId(d.getId());
        if (nodos.isEmpty()) {
            throw new IllegalArgumentException("El diagrama no tiene nodos");
        }
        boolean tieneInicio = nodos.stream().anyMatch(n -> "inicio".equals(n.getTipo()));
        boolean tieneFin = nodos.stream().anyMatch(n -> "fin".equals(n.getTipo()));
        if (!tieneInicio) throw new IllegalArgumentException("Falta nodo de inicio");
        if (!tieneFin) throw new IllegalArgumentException("Falta nodo de fin");

        // Validar que cada nodo (excepto fin) tenga al menos una transición saliente
        for (NodoDiagrama n : nodos) {
            if ("fin".equals(n.getTipo())) continue;
            List<?> salientes = flujoRepository.findByNodoOrigenId(n.getId());
            if (salientes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Nodo '" + n.getNombre() + "' no tiene transición saliente");
            }
        }
    }

    public void eliminar(String id) {
        DiagramaWorkflow d = diagramaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if ("publicado".equals(d.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede eliminar un diagrama publicado. Archívalo primero");
        }

        // Cascada: borrar nodos y transiciones
        flujoRepository.findByDiagramaId(id)
                .forEach(t -> flujoRepository.deleteById(t.getId()));
        nodoRepository.findByDiagramaId(id)
                .forEach(n -> nodoRepository.deleteById(n.getId()));
        diagramaRepository.deleteById(id);
    }
}
```

### 4.2. `DiagramaWorkflowController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.DiagramaEstadoRequest;
import com.example.demo.dto.DiagramaWorkflowRequest;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.services.DiagramaWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diagramas")
@Tag(name = "Diagramas de Workflow", description = "CU-12: el administrador diseña los diagramas UML del flujo de trabajo")
public class DiagramaWorkflowController {

    @Autowired private DiagramaWorkflowService diagramaService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar diagramas",
               description = "Filtro opcional: estado=borrador|publicado|archivado")
    public ResponseEntity<List<DiagramaWorkflow>> listar(
            @RequestParam(required = false) String estado) {
        if (estado != null) {
            return ResponseEntity.ok(diagramaService.listarPorEstado(estado));
        }
        return ResponseEntity.ok(diagramaService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DiagramaWorkflow> buscar(@PathVariable String id) {
        return diagramaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear diagrama",
               description = "Inicia en estado 'borrador'. Después agrega nodos y transiciones, y publícalo.")
    public ResponseEntity<DiagramaWorkflow> crear(@Valid @RequestBody DiagramaWorkflowRequest req,
                                                   Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(diagramaService.crear(req, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DiagramaWorkflow> actualizar(@PathVariable String id,
                                                        @Valid @RequestBody DiagramaWorkflowRequest req) {
        return ResponseEntity.ok(diagramaService.actualizar(id, req));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cambiar estado del diagrama",
               description = "Al publicar, valida que tenga inicio, fin y todas las salidas conectadas.")
    public ResponseEntity<DiagramaWorkflow> cambiarEstado(@PathVariable String id,
                                                           @Valid @RequestBody DiagramaEstadoRequest req) {
        return ResponseEntity.ok(diagramaService.cambiarEstado(id, req.getEstado()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Eliminar diagrama",
               description = "Solo se puede eliminar si NO está publicado. Borra en cascada nodos y transiciones.")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        diagramaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 5. Servicio y Controller de Nodos (CU-13)

### 5.1. `NodoDiagramaService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.NodoDiagramaRequest;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NodoDiagramaService {

    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;

    public NodoDiagrama agregarNodo(String diagramaId, NodoDiagramaRequest req) {
        var diagrama = diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede modificar un diagrama en estado 'borrador'");
        }

        if ("actividad".equals(req.getTipo())) {
            if (req.getDepartamentoId() == null) {
                throw new IllegalArgumentException(
                        "Un nodo 'actividad' requiere departamentoId");
            }
            departamentoRepository.findById(req.getDepartamentoId())
                    .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));
        }

        // Solo un nodo INICIO por diagrama
        if ("inicio".equals(req.getTipo())) {
            boolean yaTiene = nodoRepository.findByDiagramaIdAndTipo(diagramaId, "inicio").size() > 0;
            if (yaTiene) {
                throw new IllegalArgumentException("Ya existe un nodo de inicio en este diagrama");
            }
        }

        NodoDiagrama n = new NodoDiagrama();
        n.setDiagramaId(diagramaId);
        n.setTipo(req.getTipo());
        n.setNombre(req.getNombre());
        n.setActividadId(req.getActividadId());
        n.setDepartamentoId(req.getDepartamentoId());
        n.setSwimlane(req.getSwimlane());
        n.setFormularioPlantillaId(req.getFormularioPlantillaId());
        n.setPosicion(req.getPosicion());
        n.setOrden(req.getOrden());

        return nodoRepository.save(n);
    }

    public List<NodoDiagrama> listarPorDiagrama(String diagramaId) {
        return nodoRepository.findByDiagramaId(diagramaId);
    }

    public Optional<NodoDiagrama> buscarPorId(String id) {
        return nodoRepository.findById(id);
    }

    public NodoDiagrama actualizar(String id, NodoDiagramaRequest req) {
        NodoDiagrama n = nodoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nodo no encontrado"));

        var diagrama = diagramaRepository.findById(n.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama del nodo no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede modificar nodos de un diagrama en 'borrador'");
        }

        n.setNombre(req.getNombre());
        n.setTipo(req.getTipo());
        n.setActividadId(req.getActividadId());
        n.setDepartamentoId(req.getDepartamentoId());
        n.setSwimlane(req.getSwimlane());
        n.setFormularioPlantillaId(req.getFormularioPlantillaId());
        n.setPosicion(req.getPosicion());
        n.setOrden(req.getOrden());

        return nodoRepository.save(n);
    }

    public void eliminar(String id) {
        NodoDiagrama n = nodoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nodo no encontrado"));

        var diagrama = diagramaRepository.findById(n.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede eliminar nodos de un diagrama en 'borrador'");
        }

        // Borrar transiciones que toquen este nodo
        flujoRepository.findByNodoOrigenId(id)
                .forEach(t -> flujoRepository.deleteById(t.getId()));
        flujoRepository.findByNodoDestinoId(id)
                .forEach(t -> flujoRepository.deleteById(t.getId()));

        nodoRepository.deleteById(id);
    }
}
```

### 5.2. `NodoDiagramaController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.NodoDiagramaRequest;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.services.NodoDiagramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Nodos del Diagrama", description = "CU-13: agregar/editar/eliminar nodos del lienzo")
public class NodoDiagramaController {

    @Autowired private NodoDiagramaService nodoService;

    @GetMapping("/diagramas/{diagramaId}/nodos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar nodos del diagrama")
    public ResponseEntity<List<NodoDiagrama>> listar(@PathVariable String diagramaId) {
        return ResponseEntity.ok(nodoService.listarPorDiagrama(diagramaId));
    }

    @PostMapping("/diagramas/{diagramaId}/nodos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Agregar nodo al diagrama",
               description = "Tipo válido: inicio, actividad, decision, fork, join, fin")
    public ResponseEntity<NodoDiagrama> crear(@PathVariable String diagramaId,
                                               @Valid @RequestBody NodoDiagramaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(nodoService.agregarNodo(diagramaId, req));
    }

    @GetMapping("/nodos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NodoDiagrama> buscar(@PathVariable String id) {
        return nodoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/nodos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<NodoDiagrama> actualizar(@PathVariable String id,
                                                    @Valid @RequestBody NodoDiagramaRequest req) {
        return ResponseEntity.ok(nodoService.actualizar(id, req));
    }

    @DeleteMapping("/nodos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        nodoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 6. Servicio y Controller de Transiciones (CU-13)

### 6.1. `FlujoTransicionService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.FlujoTransicionRequest;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FlujoTransicionService {

    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;

    public FlujoTransicion agregarTransicion(String diagramaId, FlujoTransicionRequest req) {
        var diagrama = diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden agregar transiciones en diagramas 'borrador'");
        }

        NodoDiagrama origen = nodoRepository.findById(req.getNodoOrigenId())
                .orElseThrow(() -> new IllegalArgumentException("Nodo origen no encontrado"));
        NodoDiagrama destino = nodoRepository.findById(req.getNodoDestinoId())
                .orElseThrow(() -> new IllegalArgumentException("Nodo destino no encontrado"));

        if (!diagramaId.equals(origen.getDiagramaId()) || !diagramaId.equals(destino.getDiagramaId())) {
            throw new IllegalArgumentException(
                    "Origen y destino deben pertenecer al mismo diagrama");
        }

        if ("fin".equals(origen.getTipo())) {
            throw new IllegalArgumentException("Un nodo 'fin' no puede tener transiciones salientes");
        }
        if ("inicio".equals(destino.getTipo())) {
            throw new IllegalArgumentException("No se puede apuntar al nodo de inicio");
        }

        // Decision requiere etiqueta "si" o "no"
        if ("decision".equals(origen.getTipo())) {
            if (req.getEtiqueta() == null
                    || !(req.getEtiqueta().equalsIgnoreCase("si")
                         || req.getEtiqueta().equalsIgnoreCase("no"))) {
                throw new IllegalArgumentException(
                        "Las transiciones desde 'decision' requieren etiqueta 'si' o 'no'");
            }
        }

        FlujoTransicion t = new FlujoTransicion();
        t.setDiagramaId(diagramaId);
        t.setNodoOrigenId(req.getNodoOrigenId());
        t.setNodoDestinoId(req.getNodoDestinoId());
        t.setTipo(req.getTipo());
        t.setEtiqueta(req.getEtiqueta());
        t.setCondicion(req.getCondicion());

        return flujoRepository.save(t);
    }

    public List<FlujoTransicion> listarPorDiagrama(String diagramaId) {
        return flujoRepository.findByDiagramaId(diagramaId);
    }

    public Optional<FlujoTransicion> buscarPorId(String id) {
        return flujoRepository.findById(id);
    }

    public void eliminar(String id) {
        FlujoTransicion t = flujoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transición no encontrada"));

        var diagrama = diagramaRepository.findById(t.getDiagramaId())
                .orElseThrow(() -> new IllegalStateException("Diagrama no existe"));

        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden eliminar transiciones en diagramas 'borrador'");
        }

        flujoRepository.deleteById(id);
    }
}
```

### 6.2. `FlujoTransicionController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.FlujoTransicionRequest;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.services.FlujoTransicionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Transiciones del Diagrama", description = "CU-13: conectar nodos del lienzo con flechas")
public class FlujoTransicionController {

    @Autowired private FlujoTransicionService flujoService;

    @GetMapping("/diagramas/{diagramaId}/transiciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FlujoTransicion>> listar(@PathVariable String diagramaId) {
        return ResponseEntity.ok(flujoService.listarPorDiagrama(diagramaId));
    }

    @PostMapping("/diagramas/{diagramaId}/transiciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Conectar dos nodos",
               description = "Tipo: secuencial, condicional, paralelo o iterativo. Para 'decision' la etiqueta debe ser 'si' o 'no'.")
    public ResponseEntity<FlujoTransicion> crear(@PathVariable String diagramaId,
                                                  @Valid @RequestBody FlujoTransicionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flujoService.agregarTransicion(diagramaId, req));
    }

    @GetMapping("/transiciones/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FlujoTransicion> buscar(@PathVariable String id) {
        return flujoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/transiciones/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        flujoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 7. Generador IA — CU-14

> 🤖 **Implementación mock para la demo.** Detecta departamentos y palabras clave del prompt y genera un diagrama plausible. La integración con un LLM real (FastAPI + LangChain) queda para el módulo de IA en Ciclo 3, pero el endpoint queda definido y funcional.

### 7.1. `PromptFlowService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.PromptFlujoRequest;
import com.example.demo.models.Departamento;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.FlujoTransicion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FlujoTransicionRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PromptFlowService {

    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository flujoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;

    /**
     * Genera un diagrama desde un prompt en lenguaje natural.
     * Implementación mock — detecta departamentos mencionados y crea un flujo
     * lineal que pasa por cada uno en el orden detectado.
     */
    public DiagramaWorkflow generarDesdePrompt(PromptFlujoRequest req, String creadorId) {
        String promptLower = req.getPrompt().toLowerCase(Locale.ROOT);

        // 1. Detectar departamentos mencionados (en el orden del prompt)
        List<Departamento> todos = departamentoRepository.findByActivoTrue();
        List<Departamento> mencionados = new ArrayList<>();
        for (Departamento d : todos) {
            int idx = promptLower.indexOf(d.getNombre().toLowerCase(Locale.ROOT));
            if (idx >= 0 && !mencionados.contains(d)) {
                mencionados.add(d);
            }
        }
        if (mencionados.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se detectó ningún departamento en el prompt. " +
                    "Menciona al menos uno de los departamentos registrados.");
        }
        // Ordenar por posición en el prompt
        mencionados.sort((a, b) -> Integer.compare(
                promptLower.indexOf(a.getNombre().toLowerCase(Locale.ROOT)),
                promptLower.indexOf(b.getNombre().toLowerCase(Locale.ROOT))));

        boolean tieneDecision = promptLower.contains("aprueba") || promptLower.contains("rechaza")
                || promptLower.contains("condición") || promptLower.contains("decisión");
        boolean tieneParalelo = promptLower.contains("paralelo")
                || promptLower.contains("simultaneo") || promptLower.contains("simultáneo");

        // 2. Crear el diagrama
        DiagramaWorkflow d = new DiagramaWorkflow();
        d.setNombre(req.getNombreDiagrama());
        d.setPoliticaId(req.getPoliticaId());
        d.setCreadorId(creadorId);
        d.setSwimlanes(mencionados.stream().map(Departamento::getNombre).toList());
        d.setVersionActual(1);
        d.setEstado("borrador");
        d.setGeneradoPorIa(true);
        d.setPromptOriginal(req.getPrompt());
        d.setFechaCreacion(LocalDateTime.now());
        d.setUltimaModificacion(LocalDateTime.now());
        d = diagramaRepository.save(d);

        // 3. Generar nodos
        List<NodoDiagrama> nodosCreados = new ArrayList<>();
        int orden = 0;
        nodosCreados.add(crearNodo(d.getId(), "inicio", "Inicio", null, null, orden++));

        for (int i = 0; i < mencionados.size(); i++) {
            Departamento dep = mencionados.get(i);
            NodoDiagrama actividad = crearNodo(d.getId(), "actividad",
                    "Actividad " + dep.getNombre(), dep.getId(), dep.getNombre(), orden++);
            nodosCreados.add(actividad);

            // Si hay paralelo y es el primer departamento operativo, agregar fork/join
            if (tieneParalelo && i == 0 && mencionados.size() >= 3) {
                nodosCreados.add(crearNodo(d.getId(), "fork", "Fork paralelo", null, null, orden++));
            }
        }

        // Si hay decisión, agregarla antes del fin
        if (tieneDecision) {
            nodosCreados.add(crearNodo(d.getId(), "decision", "¿Aprobar?", null, null, orden++));
        }
        nodosCreados.add(crearNodo(d.getId(), "fin", "Fin", null, null, orden));

        // 4. Generar transiciones lineales
        for (int i = 0; i < nodosCreados.size() - 1; i++) {
            NodoDiagrama actual = nodosCreados.get(i);
            NodoDiagrama siguiente = nodosCreados.get(i + 1);

            FlujoTransicion t = new FlujoTransicion();
            t.setDiagramaId(d.getId());
            t.setNodoOrigenId(actual.getId());
            t.setNodoDestinoId(siguiente.getId());

            if ("decision".equals(actual.getTipo())) {
                t.setTipo("condicional");
                t.setEtiqueta("si");
            } else {
                t.setTipo("secuencial");
            }
            flujoRepository.save(t);
        }

        // Si hubo decisión, agregar la rama "no" hacia la actividad anterior (iterativo)
        if (tieneDecision && nodosCreados.size() >= 4) {
            NodoDiagrama decision = nodosCreados.stream()
                    .filter(n -> "decision".equals(n.getTipo()))
                    .findFirst().orElseThrow();
            NodoDiagrama actividadAnterior = nodosCreados.get(nodosCreados.indexOf(decision) - 1);

            FlujoTransicion no = new FlujoTransicion();
            no.setDiagramaId(d.getId());
            no.setNodoOrigenId(decision.getId());
            no.setNodoDestinoId(actividadAnterior.getId());
            no.setTipo("iterativo");
            no.setEtiqueta("no");
            flujoRepository.save(no);
        }

        return d;
    }

    private NodoDiagrama crearNodo(String diagramaId, String tipo, String nombre,
                                    String departamentoId, String swimlane, int orden) {
        NodoDiagrama n = new NodoDiagrama();
        n.setDiagramaId(diagramaId);
        n.setTipo(tipo);
        n.setNombre(nombre);
        n.setDepartamentoId(departamentoId);
        n.setSwimlane(swimlane);
        n.setOrden(orden);
        return nodoRepository.save(n);
    }
}
```

### 7.2. `PromptFlowController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.PromptFlujoRequest;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.services.PromptFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow-design")
@Tag(name = "Diseño por IA",
     description = "CU-14: el administrador describe el flujo en texto y la IA genera el diagrama")
public class PromptFlowController {

    @Autowired private PromptFlowService promptFlowService;

    @PostMapping("/from-prompt")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Generar diagrama desde un prompt",
               description = """
                       Recibe una descripción en lenguaje natural del proceso y genera un diagrama
                       con sus nodos y transiciones. El diagrama queda en estado 'borrador' para que
                       el administrador lo revise antes de publicarlo.

                       **Ejemplo de prompt:** "Crea un flujo donde Atención al Cliente recibe la
                       solicitud, luego Área Técnica hace la inspección, después Área Legal aprueba
                       y finalmente Operaciones cierra. Si Legal rechaza, vuelve a Técnica."

                       **Nota:** la implementación actual es un generador heurístico para demo.
                       La integración con LLM real (FastAPI) está prevista para el Ciclo 3.
                       """)
    public ResponseEntity<DiagramaWorkflow> generar(@Valid @RequestBody PromptFlujoRequest req,
                                                     Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(promptFlowService.generarDesdePrompt(req, auth.getName()));
    }
}
```

---

## 8. Roles y Permisos completos — CU-03

### 8.1. Actualizar `RolService.java`

Reemplazar el archivo existente con:

```java
package com.example.demo.services;

import com.example.demo.dto.RolRequest;
import com.example.demo.models.Permiso;
import com.example.demo.models.Rol;
import com.example.demo.repositories.PermisoRepository;
import com.example.demo.repositories.RolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RolService {

    @Autowired private RolRepository rolRepository;
    @Autowired private PermisoRepository permisoRepository;

    public List<Rol> listarTodos() {
        return rolRepository.findAll();
    }

    public Optional<Rol> buscarPorId(String id) {
        return rolRepository.findById(id);
    }

    public Optional<Rol> buscarPorNombre(String nombre) {
        return rolRepository.findByNombre(nombre);
    }

    public Rol crear(RolRequest req) {
        if (rolRepository.findByNombre(req.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un rol con el nombre: " + req.getNombre());
        }
        validarPermisos(req.getPermisos());

        Rol r = new Rol();
        r.setNombre(req.getNombre());
        r.setDescripcion(req.getDescripcion());
        r.setPermisos(req.getPermisos());
        r.setEsSistema(false);
        r.setFechaCreacion(LocalDateTime.now());
        return rolRepository.save(r);
    }

    public Rol actualizar(String id, RolRequest req) {
        Rol r = rolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));

        if (r.isEsSistema() && !r.getNombre().equals(req.getNombre())) {
            throw new IllegalArgumentException("No se puede renombrar un rol del sistema");
        }
        validarPermisos(req.getPermisos());

        r.setDescripcion(req.getDescripcion());
        r.setPermisos(req.getPermisos());
        return rolRepository.save(r);
    }

    public Rol asignarPermisos(String rolId, List<String> permisos) {
        validarPermisos(permisos);
        Rol r = rolRepository.findById(rolId)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        r.setPermisos(permisos);
        return rolRepository.save(r);
    }

    public void eliminar(String id) {
        Rol r = rolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        if (r.isEsSistema()) {
            throw new IllegalArgumentException(
                    "No se puede eliminar un rol del sistema (Cliente, Funcionario, Administrador, SuperUser)");
        }
        rolRepository.deleteById(id);
    }

    private void validarPermisos(List<String> permisos) {
        if (permisos == null) return;
        for (String codigo : permisos) {
            if ("*".equals(codigo)) continue;  // wildcard del SuperUser
            permisoRepository.findByCodigo(codigo)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Permiso no existe: " + codigo));
        }
    }
}
```

### 8.2. Actualizar `RolController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.AsignarPermisosRequest;
import com.example.demo.dto.RolRequest;
import com.example.demo.models.Rol;
import com.example.demo.services.RolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "CU-03: gestión de roles y asignación de permisos")
public class RolController {

    @Autowired private RolService rolService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar roles")
    public ResponseEntity<List<Rol>> listar() {
        return ResponseEntity.ok(rolService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Rol> buscar(@PathVariable String id) {
        return rolService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear rol personalizado",
               description = "Crea un rol no-sistema. Para SuperUser/Administrador/Funcionario/Cliente usar el seed.")
    public ResponseEntity<Rol> crear(@Valid @RequestBody RolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rolService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Rol> actualizar(@PathVariable String id,
                                           @Valid @RequestBody RolRequest req) {
        return ResponseEntity.ok(rolService.actualizar(id, req));
    }

    @PatchMapping("/{id}/permisos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Asignar permisos a un rol",
               description = "Reemplaza la lista de permisos del rol. Cada código debe existir en la colección 'permisos'.")
    public ResponseEntity<Rol> asignarPermisos(@PathVariable String id,
                                                @Valid @RequestBody AsignarPermisosRequest req) {
        return ResponseEntity.ok(rolService.asignarPermisos(id, req.getPermisos()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        rolService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 8.3. `PermisoController.java`

```java
package com.example.demo.controllers;

import com.example.demo.models.Permiso;
import com.example.demo.repositories.PermisoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permisos")
@Tag(name = "Permisos", description = "Catálogo de permisos del sistema (definidos en el seed)")
public class PermisoController {

    @Autowired private PermisoRepository permisoRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar permisos disponibles",
               description = "Filtro opcional: modulo=usuarios|tramites|reportes|...")
    public ResponseEntity<List<Permiso>> listar(@RequestParam(required = false) String modulo) {
        if (modulo != null) {
            return ResponseEntity.ok(permisoRepository.findByModulo(modulo));
        }
        return ResponseEntity.ok(permisoRepository.findAll());
    }
}
```

---

## 9. Actualizar `SecurityConfig`

Agregar las nuevas rutas en `.authorizeHttpRequests`:

```java
// === RUTAS G5 ===
// Diagramas, nodos y transiciones — lectura autenticada, escritura admin
.requestMatchers(HttpMethod.GET, "/api/diagramas/**").authenticated()
.requestMatchers(HttpMethod.GET, "/api/nodos/**").authenticated()
.requestMatchers(HttpMethod.GET, "/api/transiciones/**").authenticated()
.requestMatchers("/api/diagramas/**").hasRole("ADMINISTRADOR")
.requestMatchers("/api/nodos/**").hasRole("ADMINISTRADOR")
.requestMatchers("/api/transiciones/**").hasRole("ADMINISTRADOR")

// Generador IA — solo admin
.requestMatchers("/api/workflow-design/**").hasRole("ADMINISTRADOR")

// Permisos — solo admin (lectura)
.requestMatchers("/api/permisos/**").hasRole("ADMINISTRADOR")
```

---

## 10. Probar el flujo CRE completo desde la API

### 10.1. Hacer login como admin

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cre.bo","password":"admin12345"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
```

### 10.2. Crear el diagrama

```bash
curl -X POST http://localhost:8080/api/diagramas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Flujo Nueva Conexión Residencial",
    "swimlanes": ["Atención al Cliente","Área Técnica","Área Legal","Operaciones"]
  }'
# Devuelve { "id": "<DIAG_ID>", "estado": "borrador", ... }
```

### 10.3. Agregar nodos al diagrama

```bash
# Nodo INICIO
curl -X POST http://localhost:8080/api/diagramas/<DIAG_ID>/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Inicio","tipo":"inicio","orden":0}'

# Nodo ATC
curl -X POST http://localhost:8080/api/diagramas/<DIAG_ID>/nodos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Verificar documentos","tipo":"actividad",
       "departamentoId":"<ID_ATC>","swimlane":"Atención al Cliente","orden":1}'

# Nodo FORK + actividades paralelas + JOIN + DECISION + LEG + OPE + FIN
# (repetir para cada nodo del flujo CRE)
```

### 10.4. Conectar nodos con transiciones

```bash
curl -X POST http://localhost:8080/api/diagramas/<DIAG_ID>/transiciones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nodoOrigenId":"<ID_INICIO>","nodoDestinoId":"<ID_ATC>","tipo":"secuencial"}'
```

### 10.5. Publicar el diagrama

```bash
curl -X PATCH http://localhost:8080/api/diagramas/<DIAG_ID>/estado \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"estado":"publicado"}'
# Si falta inicio/fin/transición → 400 con mensaje claro
```

### 10.6. Asignar diagrama a la política activa

Por ahora se hace directamente en Mongo Express modificando el campo `diagramaId` de la política. En G2 de C2 se agregará un endpoint dedicado.

### 10.7. Probar generador IA (CU-14)

```bash
curl -X POST http://localhost:8080/api/workflow-design/from-prompt \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "nombreDiagrama": "Flujo generado por IA",
    "prompt": "Crea un flujo donde Atención al Cliente recibe la solicitud, luego Área Técnica hace la inspección en paralelo, después Área Legal aprueba y finalmente Operaciones cierra. Si Legal rechaza vuelve a Técnica."
  }'
# Devuelve un diagrama en borrador con nodos y transiciones plausibles
```

### 10.8. Probar gestión de roles (CU-03)

```bash
# Crear rol personalizado
curl -X POST http://localhost:8080/api/roles \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Auditor","descripcion":"Solo ve reportes",
       "permisos":["VER_REPORTES"]}'

# Asignar permisos
curl -X PATCH http://localhost:8080/api/roles/<ROL_ID>/permisos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"permisos":["VER_REPORTES","CONSULTAR_MIS_TRAMITES"]}'

# Intentar eliminar rol del sistema → 400
curl -X DELETE http://localhost:8080/api/roles/<ID_ADMINISTRADOR> \
  -H "Authorization: Bearer $TOKEN"
# HTTP 400 — No se puede eliminar un rol del sistema
```

---

## 11. Estado final del Ciclo 1

Al terminar G5, los **9 casos de uso del Ciclo 1 quedan completos**:

| CU | Nombre | Endpoints | Guía |
|----|--------|-----------|------|
| CU-01 | Iniciar sesión | `POST /api/auth/login` | G1 |
| CU-02 | Gestionar usuarios | `/api/usuarios/**` + `/api/auth/register-cliente` | G1 |
| CU-03 | Configurar roles y permisos | `/api/roles/**` + `/api/permisos` | G3 + **G5** |
| CU-04 | Gestionar departamentos | `/api/departamentos/**` | G2 |
| CU-05 | Gestionar actividades | `/api/actividades/**` | G2 |
| CU-06 | Gestionar políticas de negocio | `/api/politicas/**` | G2 |
| CU-12 | Diseñar workflow | `/api/diagramas/**` + motor en `/api/workflow/**` | **G5** + G4 |
| CU-13 | Crear flujo por diagramas | `/api/diagramas/**/nodos` + `/api/diagramas/**/transiciones` | **G5** |
| CU-14 | Crear flujo por prompts (IA) | `POST /api/workflow-design/from-prompt` | **G5** |

---

## 12. Checklist de entregables G5

- [ ] 7 DTOs creados: `DiagramaWorkflowRequest`, `DiagramaEstadoRequest`, `NodoDiagramaRequest`, `FlujoTransicionRequest`, `PromptFlujoRequest`, `RolRequest`, `AsignarPermisosRequest`
- [ ] `DiagramaWorkflowService` + `DiagramaWorkflowController` con CRUD + cambio de estado + validación al publicar
- [ ] `NodoDiagramaService` + `NodoDiagramaController` con CRUD anidado bajo diagrama
- [ ] `FlujoTransicionService` + `FlujoTransicionController` con CRUD anidado + validación de etiquetas en `decision`
- [ ] `PromptFlowService` + `PromptFlowController` con generador heurístico (mock)
- [ ] `RolService` extendido con CRUD + `asignarPermisos`
- [ ] `RolController` actualizado con POST/PUT/PATCH/DELETE
- [ ] `PermisoController` con `GET` y filtro por módulo
- [ ] `SecurityConfig` actualizado con todas las nuevas rutas
- [ ] Demo: crear diagrama CRE completo desde Postman + publicar + asignar a política
- [ ] Demo: generar diagrama desde prompt en lenguaje natural
- [ ] Demo: crear rol personalizado y asignarle permisos
- [ ] **9/9 casos de uso del Ciclo 1 completados**

---

## 13. Qué sigue (Ciclo 2)

Con G5 cierras el Ciclo 1. El **Ciclo 2** abrirá con:

| Guía | Tema | CUs |
|------|------|-----|
| G1-C2 | Motor de Workflow (ya escrita en G4) | Ejecución del diagrama |
| G2-C2 | Expediente Digital + secciones por nodo | CU-09, CU-10, CU-16 |
| G3-C2 | Flujo del trámite (iniciar, derivar, aprobar/rechazar, devolver) | CU-07, CU-08, CU-11, CU-17, CU-18 |
| G4-C2 | Colaboración online en diagramas | CU-15 |

---

## 🛠️ Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| `400 — Solo se puede modificar un diagrama en 'borrador'` | El diagrama ya fue publicado | Crear una nueva versión o desarchivar |
| `400 — Falta nodo de inicio` al publicar | El diagrama no tiene un `tipo:"inicio"` | Agregar el nodo antes de publicar |
| `400 — Las transiciones desde 'decision' requieren etiqueta 'si' o 'no'` | Falta `etiqueta` en el body | Enviar `"etiqueta":"si"` o `"no"` |
| Generador IA crea diagrama vacío | El prompt no menciona ningún departamento registrado | Mencionar nombres exactos: "Atención al Cliente", "Área Técnica", etc. |
| `400 — No se puede eliminar un rol del sistema` | Intento de borrar Cliente/Funcionario/Admin/SuperUser | Esos roles son inmutables por diseño |
| `403` en `/api/roles` para funcionario | Solo admin gestiona roles | Usar token de administrador |

---

*Guía 5 — Ciclo 1 cierre · Sistema de Gestión de Trámites · Primer Examen Parcial*
