# Guía 2 — Configuración del Sistema: Departamentos, Actividades y Políticas de Negocio

**Ciclo 1 · Sistema de Gestión de Trámites**

> 🎯 **Objetivo:** construir el módulo de configuración que el administrador usa antes de diseñar flujos. Sin departamentos, actividades y políticas correctamente registradas, el Motor de Workflow (Ciclo 2) no tiene con qué trabajar.

---

## 0. Prerequisitos (G1 completada)

✅ Spring Security + JWT funcionando
✅ `/api/auth/register-cliente`, `/api/auth/login` probados
✅ `GET /api/usuarios/me` accesible para cualquier rol autenticado
✅ `POST /api/usuarios/crear` protegido por `ADMINISTRADOR`
✅ `GlobalExceptionHandler` activo

---

## 1. Lo que vamos a construir

| Módulo | Endpoints | ¿Quién? |
|--------|-----------|---------|
| **Departamentos** | CRUD completo | Admin escribe · Todos leen |
| **Actividades** | CRUD completo | Admin escribe · Todos leen |
| **Políticas de negocio** | CRUD + activar/archivar | Admin escribe · Funcionarios leen activas |

> 📌 **Límite de esta guía:** las políticas se crean con sus metadatos (nombre, categoría, estado) pero **sin diagrama de flujo**. El diagrama se asocia en Ciclo 2 cuando implementemos el Motor de Workflow. Una política en estado `borrador` sin diagrama es válida aquí.

---

## 2. Reglas de negocio importantes

Antes de escribir código, deja claras estas reglas porque afectan las validaciones:

### Departamentos
- El `codigo` es único en toda la base (ej: `ATC`, `TEC`).
- No se elimina un departamento — se **desactiva** (`activo: false`). Los trámites históricos deben seguir referenciando el departamento original.
- Un departamento puede tener un `jefeId` que apunta a un Usuario de tipo `funcionario` o `administrador`. Es opcional al crear.

### Actividades
- Una actividad pertenece a un departamento. Si el departamento no existe o está inactivo, la actividad no se puede crear.
- El `slaHoras` define el tiempo máximo para completar esa actividad. El Motor de Workflow lo usará para detectar cuellos de botella.
- `reutilizable: true` significa que puede usarse en múltiples flujos distintos — es importante para el diseño del diagrama en Ciclo 2.
- Tampoco se eliminan — se **desactivan**.

### Políticas de negocio
- El `nombre` es único. No pueden existir dos políticas con el mismo nombre.
- Estados válidos: `borrador` → `activa` → `archivada`. El flujo es unidireccional (no se puede reactivar una archivada sin crear una nueva versión).
- Solo puede haber **una política activa** con un nombre dado. Si se activa una, la anterior con el mismo nombre debe archivarse automáticamente.
- Sin `diagramaId` al crear está bien — se asigna en Ciclo 2.

---

## 3. DTOs

Crear en `src/main/java/com/example/demo/dto/`:

### 3.1. `DepartamentoRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartamentoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 5, message = "El código no puede superar 5 caracteres")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "El código solo acepta mayúsculas y números")
    private String codigo;

    private String descripcion;
    private String jefeId;  // opcional — ID de un usuario
}
```

### 3.2. `ActividadRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class ActividadRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;

    @NotBlank(message = "El departamentoId es obligatorio")
    private String departamentoId;

    @NotNull
    @Min(value = 1, message = "El SLA debe ser al menos 1 hora")
    private Integer slaHoras;

    @NotBlank
    @Pattern(regexp = "aprobar|rechazar|derivar|observar",
             message = "tipoSalida debe ser: aprobar, rechazar, derivar u observar")
    private String tipoSalida;

    private List<String> camposRequeridos;
    private boolean reutilizable;
}
```

### 3.3. `PoliticaNegocioRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class PoliticaNegocioRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;
    private String categoria;
    private Map<String, Object> parametros;
}
```

### 3.4. `PoliticaEstadoRequest.java` (para activar/archivar)

```java
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PoliticaEstadoRequest {

    @NotBlank
    @Pattern(regexp = "activa|archivada",
             message = "estado debe ser 'activa' o 'archivada'")
    private String estado;
}
```

---

## 4. Servicio de Departamentos

`src/main/java/com/example/demo/services/DepartamentoService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.DepartamentoRequest;
import com.example.demo.models.Departamento;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DepartamentoService {

    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public Departamento crear(DepartamentoRequest req) {
        if (departamentoRepository.findByCodigo(req.getCodigo()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un departamento con el código: " + req.getCodigo());
        }
        if (departamentoRepository.findByNombre(req.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un departamento con el nombre: " + req.getNombre());
        }
        if (req.getJefeId() != null) {
            usuarioRepository.findById(req.getJefeId())
                    .orElseThrow(() -> new IllegalArgumentException("El jefeId no corresponde a ningún usuario"));
        }

        Departamento d = new Departamento();
        d.setNombre(req.getNombre());
        d.setCodigo(req.getCodigo().toUpperCase());
        d.setDescripcion(req.getDescripcion());
        d.setJefeId(req.getJefeId());
        d.setActivo(true);
        d.setFechaCreacion(LocalDateTime.now());

        return departamentoRepository.save(d);
    }

    public List<Departamento> listarTodos() {
        return departamentoRepository.findAll();
    }

    public List<Departamento> listarActivos() {
        return departamentoRepository.findByActivoTrue();
    }

    public Optional<Departamento> buscarPorId(String id) {
        return departamentoRepository.findById(id);
    }

    public Departamento actualizar(String id, DepartamentoRequest req) {
        Departamento d = departamentoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));

        // Verificar que el código no lo use otro departamento
        departamentoRepository.findByCodigo(req.getCodigo())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new IllegalArgumentException("El código ya lo usa otro departamento");
                    }
                });

        if (req.getJefeId() != null) {
            usuarioRepository.findById(req.getJefeId())
                    .orElseThrow(() -> new IllegalArgumentException("El jefeId no corresponde a ningún usuario"));
        }

        d.setNombre(req.getNombre());
        d.setCodigo(req.getCodigo().toUpperCase());
        d.setDescripcion(req.getDescripcion());
        d.setJefeId(req.getJefeId());

        return departamentoRepository.save(d);
    }

    public void desactivar(String id) {
        Departamento d = departamentoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));
        d.setActivo(false);
        departamentoRepository.save(d);
    }
}
```

---

## 5. Controller de Departamentos

`src/main/java/com/example/demo/controllers/DepartamentoController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.DepartamentoRequest;
import com.example.demo.models.Departamento;
import com.example.demo.services.DepartamentoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departamentos")
public class DepartamentoController {

    @Autowired
    private DepartamentoService departamentoService;

    // Cualquier autenticado puede leer — funcionarios necesitan ver su departamento
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Departamento>> listar(
            @RequestParam(required = false, defaultValue = "false") boolean soloActivos) {
        if (soloActivos) {
            return ResponseEntity.ok(departamentoService.listarActivos());
        }
        return ResponseEntity.ok(departamentoService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Departamento> buscar(@PathVariable String id) {
        return departamentoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Solo administrador puede modificar la configuración
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Departamento> crear(@Valid @RequestBody DepartamentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departamentoService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Departamento> actualizar(@PathVariable String id,
                                                    @Valid @RequestBody DepartamentoRequest req) {
        return ResponseEntity.ok(departamentoService.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> desactivar(@PathVariable String id) {
        departamentoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 6. Servicio de Actividades

`src/main/java/com/example/demo/services/ActividadService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.ActividadRequest;
import com.example.demo.models.Actividad;
import com.example.demo.models.Departamento;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DepartamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ActividadService {

    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DepartamentoRepository departamentoRepository;

    public Actividad crear(ActividadRequest req) {
        Departamento depto = departamentoRepository.findById(req.getDepartamentoId())
                .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));

        if (!depto.isActivo()) {
            throw new IllegalArgumentException("No se pueden crear actividades en un departamento inactivo");
        }

        Actividad a = new Actividad();
        a.setNombre(req.getNombre());
        a.setDescripcion(req.getDescripcion());
        a.setDepartamentoId(req.getDepartamentoId());
        a.setSlaHoras(req.getSlaHoras());
        a.setTipoSalida(req.getTipoSalida());
        a.setCamposRequeridos(req.getCamposRequeridos());
        a.setReutilizable(req.isReutilizable());
        a.setFechaCreacion(LocalDateTime.now());

        return actividadRepository.save(a);
    }

    public List<Actividad> listarTodas() {
        return actividadRepository.findAll();
    }

    public List<Actividad> listarPorDepartamento(String departamentoId) {
        return actividadRepository.findByDepartamentoId(departamentoId);
    }

    public List<Actividad> listarReutilizables() {
        return actividadRepository.findByReutilizableTrue();
    }

    public Optional<Actividad> buscarPorId(String id) {
        return actividadRepository.findById(id);
    }

    public Actividad actualizar(String id, ActividadRequest req) {
        Actividad a = actividadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Actividad no encontrada"));

        if (!req.getDepartamentoId().equals(a.getDepartamentoId())) {
            // Validar el nuevo departamento si cambió
            Departamento depto = departamentoRepository.findById(req.getDepartamentoId())
                    .orElseThrow(() -> new IllegalArgumentException("Departamento destino no encontrado"));
            if (!depto.isActivo()) {
                throw new IllegalArgumentException("El departamento destino está inactivo");
            }
        }

        a.setNombre(req.getNombre());
        a.setDescripcion(req.getDescripcion());
        a.setDepartamentoId(req.getDepartamentoId());
        a.setSlaHoras(req.getSlaHoras());
        a.setTipoSalida(req.getTipoSalida());
        a.setCamposRequeridos(req.getCamposRequeridos());
        a.setReutilizable(req.isReutilizable());

        return actividadRepository.save(a);
    }

    public void eliminar(String id) {
        if (!actividadRepository.existsById(id)) {
            throw new IllegalArgumentException("Actividad no encontrada");
        }
        actividadRepository.deleteById(id);
    }
}
```

---

## 7. Controller de Actividades

`src/main/java/com/example/demo/controllers/ActividadController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.ActividadRequest;
import com.example.demo.models.Actividad;
import com.example.demo.services.ActividadService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/actividades")
public class ActividadController {

    @Autowired
    private ActividadService actividadService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Actividad>> listar(
            @RequestParam(required = false) String departamentoId,
            @RequestParam(required = false, defaultValue = "false") boolean reutilizables) {

        if (departamentoId != null) {
            return ResponseEntity.ok(actividadService.listarPorDepartamento(departamentoId));
        }
        if (reutilizables) {
            return ResponseEntity.ok(actividadService.listarReutilizables());
        }
        return ResponseEntity.ok(actividadService.listarTodas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Actividad> buscar(@PathVariable String id) {
        return actividadService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Actividad> crear(@Valid @RequestBody ActividadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actividadService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Actividad> actualizar(@PathVariable String id,
                                                 @Valid @RequestBody ActividadRequest req) {
        return ResponseEntity.ok(actividadService.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        actividadService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 8. Servicio de Políticas de Negocio

`src/main/java/com/example/demo/services/PoliticaNegocioService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.PoliticaNegocioRequest;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.repositories.PoliticaNegocioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PoliticaNegocioService {

    @Autowired private PoliticaNegocioRepository politicaRepository;

    public PoliticaNegocio crear(PoliticaNegocioRequest req, String creadorId) {
        if (politicaRepository.findByNombre(req.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una política con el nombre: " + req.getNombre());
        }

        PoliticaNegocio p = new PoliticaNegocio();
        p.setNombre(req.getNombre());
        p.setDescripcion(req.getDescripcion());
        p.setCategoria(req.getCategoria());
        p.setParametros(req.getParametros());
        p.setCreadorId(creadorId);
        p.setVersionActual(1);
        p.setEstado("borrador");      // siempre empieza como borrador
        p.setFechaCreacion(LocalDateTime.now());

        return politicaRepository.save(p);
    }

    public List<PoliticaNegocio> listarTodas() {
        return politicaRepository.findAll();
    }

    public List<PoliticaNegocio> listarActivas() {
        return politicaRepository.findByEstado("activa");
    }

    public Optional<PoliticaNegocio> buscarPorId(String id) {
        return politicaRepository.findById(id);
    }

    public PoliticaNegocio actualizar(String id, PoliticaNegocioRequest req) {
        PoliticaNegocio p = politicaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        if ("archivada".equals(p.getEstado())) {
            throw new IllegalArgumentException("No se puede editar una política archivada");
        }

        // Verificar nombre único si cambió
        politicaRepository.findByNombre(req.getNombre())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new IllegalArgumentException("El nombre ya lo usa otra política");
                    }
                });

        p.setNombre(req.getNombre());
        p.setDescripcion(req.getDescripcion());
        p.setCategoria(req.getCategoria());
        p.setParametros(req.getParametros());

        return politicaRepository.save(p);
    }

    public PoliticaNegocio cambiarEstado(String id, String nuevoEstado) {
        PoliticaNegocio p = politicaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        validarTransicionEstado(p.getEstado(), nuevoEstado);

        // Si se activa, archivar cualquier otra política activa con el mismo nombre
        if ("activa".equals(nuevoEstado)) {
            politicaRepository.findByEstado("activa").stream()
                    .filter(activa -> activa.getNombre().equals(p.getNombre())
                            && !activa.getId().equals(id))
                    .forEach(activa -> {
                        activa.setEstado("archivada");
                        politicaRepository.save(activa);
                    });
            p.setFechaActivacion(LocalDateTime.now());
        }

        p.setEstado(nuevoEstado);
        return politicaRepository.save(p);
    }

    private void validarTransicionEstado(String actual, String nuevo) {
        boolean valida = switch (actual) {
            case "borrador"  -> "activa".equals(nuevo);
            case "activa"    -> "archivada".equals(nuevo);
            case "archivada" -> false;    // estado terminal
            default -> false;
        };
        if (!valida) {
            throw new IllegalArgumentException(
                    String.format("Transición inválida: '%s' → '%s'", actual, nuevo));
        }
    }

    public void eliminar(String id) {
        PoliticaNegocio p = politicaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));

        if ("activa".equals(p.getEstado())) {
            throw new IllegalArgumentException("No se puede eliminar una política activa. Archívala primero");
        }
        politicaRepository.deleteById(id);
    }
}
```

---

## 9. Controller de Políticas de Negocio

`src/main/java/com/example/demo/controllers/PoliticaNegocioController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.PoliticaEstadoRequest;
import com.example.demo.dto.PoliticaNegocioRequest;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.services.PoliticaNegocioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/politicas")
public class PoliticaNegocioController {

    @Autowired
    private PoliticaNegocioService politicaService;

    // Funcionarios ven políticas activas para seleccionar al iniciar un trámite
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PoliticaNegocio>> listar(
            @RequestParam(required = false, defaultValue = "false") boolean soloActivas) {
        if (soloActivas) {
            return ResponseEntity.ok(politicaService.listarActivas());
        }
        return ResponseEntity.ok(politicaService.listarTodas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PoliticaNegocio> buscar(@PathVariable String id) {
        return politicaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Solo admin crea y gestiona políticas
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PoliticaNegocio> crear(@Valid @RequestBody PoliticaNegocioRequest req,
                                                  Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(politicaService.crear(req, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PoliticaNegocio> actualizar(@PathVariable String id,
                                                       @Valid @RequestBody PoliticaNegocioRequest req) {
        return ResponseEntity.ok(politicaService.actualizar(id, req));
    }

    // Endpoint dedicado para transiciones de estado (borrador→activa→archivada)
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PoliticaNegocio> cambiarEstado(@PathVariable String id,
                                                          @Valid @RequestBody PoliticaEstadoRequest req) {
        return ResponseEntity.ok(politicaService.cambiarEstado(id, req.getEstado()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        politicaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 10. Actualizar `SecurityConfig` — agregar rutas de lectura

En `SecurityConfig.java`, agrega estas líneas en `.authorizeHttpRequests`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/health/**").permitAll()
    .requestMatchers("/api/usuarios/me").authenticated()
    .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")

    // === NUEVAS REGLAS PARA G2 ===
    // Lecturas abiertas a cualquier autenticado
    .requestMatchers(HttpMethod.GET, "/api/departamentos/**").authenticated()
    .requestMatchers(HttpMethod.GET, "/api/actividades/**").authenticated()
    .requestMatchers(HttpMethod.GET, "/api/politicas/**").authenticated()
    // Escrituras solo admin (el @PreAuthorize del método ya lo controla,
    // esto es una segunda capa a nivel de ruta)
    .requestMatchers("/api/departamentos/**").hasRole("ADMINISTRADOR")
    .requestMatchers("/api/actividades/**").hasRole("ADMINISTRADOR")
    .requestMatchers("/api/politicas/**").hasRole("ADMINISTRADOR")
    // ============================

    .anyRequest().authenticated()
)
```

> Agrega `import org.springframework.http.HttpMethod;` al tope del archivo.

---

## 11. Probar con Postman / curl

### 11.1. Setup previo

1. Levantar Docker: `docker-compose up -d`
2. Correr la app: `./gradlew bootRun`
3. Hacer login como admin y guardar el token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cre.bo","password":"admin12345"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo $TOKEN
```

### 11.2. Crear los 4 departamentos del ejemplo CRE

```bash
# Atención al Cliente
curl -X POST http://localhost:8080/api/departamentos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Atención al Cliente","codigo":"ATC","descripcion":"Recepción de solicitudes"}'

# Área Técnica
curl -X POST http://localhost:8080/api/departamentos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Área Técnica","codigo":"TEC","descripcion":"Inspección y presupuesto"}'

# Área Legal
curl -X POST http://localhost:8080/api/departamentos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Área Legal","codigo":"LEG","descripcion":"Revisión de contratos"}'

# Operaciones
curl -X POST http://localhost:8080/api/departamentos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Operaciones","codigo":"OPE","descripcion":"Ejecución y cierre"}'
```

> ⚠️ Si el seed ya creó estos departamentos, este POST devolverá `400 Bad Request — Ya existe un departamento con el código: ATC`. Eso es correcto. Los datos del seed son válidos para el demo.

### 11.3. Crear actividades del flujo CRE

```bash
# Obtener el ID del departamento ATC primero
curl -s http://localhost:8080/api/departamentos \
  -H "Authorization: Bearer $TOKEN"

# Luego crear actividad (reemplaza ID_ATC con el real)
curl -X POST http://localhost:8080/api/actividades \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Verificar documentos del solicitante",
    "descripcion": "Revisión de documentos y datos del cliente",
    "departamentoId": "ID_ATC",
    "slaHoras": 24,
    "tipoSalida": "derivar",
    "reutilizable": true
  }'
```

### 11.4. Crear política de negocio y activarla

```bash
# Crear en borrador
curl -X POST http://localhost:8080/api/politicas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Nueva conexión residencial",
    "descripcion": "Proceso de solicitud de nueva conexión eléctrica residencial",
    "categoria": "conexiones"
  }'

# Guardar el ID devuelto y activar
curl -X PATCH http://localhost:8080/api/politicas/ID_POLITICA/estado \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"estado":"activa"}'
```

### 11.5. Probar que un funcionario lee pero no escribe

```bash
# Login como funcionario
TOKEN_FUNC=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"funcionario@cre.bo","password":"func12345"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# Puede listar políticas activas ✅
curl -X GET "http://localhost:8080/api/politicas?soloActivas=true" \
  -H "Authorization: Bearer $TOKEN_FUNC"

# No puede crear departamento ❌ → 403
curl -X POST http://localhost:8080/api/departamentos \
  -H "Authorization: Bearer $TOKEN_FUNC" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Hacker","codigo":"HAK"}'
```

### 11.6. Probar transición de estado inválida → debe dar 400

```bash
# Intentar pasar de 'activa' a 'borrador' (no permitido)
curl -X PATCH http://localhost:8080/api/politicas/ID_POLITICA/estado \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"estado":"borrador"}'
# HTTP 400 — Transición inválida: 'activa' → 'borrador'
```

---

## 12. Estado del sistema al terminar G2

Al completar esta guía, tienes en Mongo Express las colecciones:

```
tramites_db
 ├── usuarios         → al menos 1 admin + 1 funcionario + 1 cliente
 ├── roles            → Cliente, Funcionario, Administrador, SuperUser
 ├── permisos         → 9 permisos de negocio
 ├── departamentos    → ATC, TEC, LEG, OPE (+ los del seed)
 ├── actividades      → al menos las del flujo CRE
 └── politicas_negocio → "Nueva conexión residencial" en estado 'activa'
```

Esto es exactamente lo que el Motor de Workflow necesita en Ciclo 2 para ejecutar el flujo en vivo durante el examen.

---

## 13. Checklist de entregables G2

- [ ] 4 DTOs creados: `DepartamentoRequest`, `ActividadRequest`, `PoliticaNegocioRequest`, `PoliticaEstadoRequest`
- [ ] `DepartamentoService` con validaciones (código único, jefe existe, desactivar en lugar de eliminar)
- [ ] `DepartamentoController` con `GET` abierto a autenticados y escritura solo admin
- [ ] `ActividadService` con validación de departamento activo y SLA mínimo 1h
- [ ] `ActividadController` con filtros por `departamentoId` y `reutilizables`
- [ ] `PoliticaNegocioService` con máquina de estados (`borrador → activa → archivada`)
- [ ] `PoliticaNegocioController` con `PATCH /estado` para transiciones
- [ ] `SecurityConfig` actualizado con reglas HTTP GET diferenciadas
- [ ] Los 4 departamentos CRE creados (o confirmados del seed)
- [ ] Al menos 1 actividad por departamento creada
- [ ] Política "Nueva conexión residencial" en estado `activa`
- [ ] Funcionario puede **leer** departamentos y políticas pero recibe **403** al intentar crear
- [ ] Transición de estado inválida devuelve **400** con mensaje descriptivo
- [ ] No se puede eliminar una política activa sin archivarla primero

---

## 14. Qué sigue (G3 — cierre del Ciclo 1)

Con G2 lista el Ciclo 1 está casi completo. La **G3** cerrará con:

- **Documentación de API con Swagger/OpenAPI** (para el PDF del examen)
- **Resumen de todas las colecciones** listas en MongoDB
- **Script de demo** completo para la presentación del 28 de abril: cómo levantar todo y mostrar los datos cargados en Mongo Express

---

## 🛠️ Troubleshooting

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| `400 — Ya existe un departamento con código ATC` | El seed ya los creó | Es correcto, usar los IDs del seed |
| `400 — Departamento destino está inactivo` | Se intenta crear actividad en depto desactivado | Reactivar el departamento o usar uno activo |
| `400 — Transición inválida` | Se saltó un estado o se intenta revertir | Respetar flujo `borrador → activa → archivada` |
| `400 — No se puede eliminar política activa` | Política en estado `activa` | Archivar con `PATCH /estado` primero |
| `403` para funcionario en GET | Falta la regla `GET authenticated` en SecurityConfig | Agregar `.requestMatchers(HttpMethod.GET, ...)` |
| `HttpMethod` no resuelve | Falta import | `import org.springframework.http.HttpMethod;` |

---

*Guía 2 — Ciclo 1 · Sistema de Gestión de Trámites · Primer Examen Parcial*
