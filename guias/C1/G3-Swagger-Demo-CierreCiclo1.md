# Guía 3 — Swagger/OpenAPI + Script de Demo + Cierre Ciclo 1

**Ciclo 1 · Sistema de Gestión de Trámites**

> 🎯 **Objetivo:** dejar el backend de Ciclo 1 completamente terminado y listo para presentar el 28 de abril. Esta guía agrega documentación de API con Swagger, una ruta de perfil para cada rol, y el script exacto de demo para los 20 minutos de presentación.

---

## 0. Estado al entrar a G3

✅ **G1 completa:** Auth JWT, registro separado por rol, `/me`, `GlobalExceptionHandler`
✅ **G2 completa:** CRUD Departamentos, Actividades, Políticas (con máquina de estados)
✅ MongoDB con datos: 4 departamentos CRE, actividades, política "Nueva conexión residencial" activa
✅ Seguridad: lecturas autenticadas, escrituras solo admin

**Lo que falta para cerrar el Ciclo 1:**

| Tema | Por qué importa |
|------|----------------|
| Swagger / OpenAPI | Requerido en el PDF del examen; el profesor puede probarlo en vivo |
| Endpoint de Roles (lectura) | Admin debe poder ver y asignar roles existentes |
| Health mejorado | Muestra estado real del sistema en el demo |
| Script de demo | 20 minutos son pocos — necesitas un guión exacto |

---

## 1. Agregar Swagger a `build.gradle`

```groovy
dependencies {
    // ... todo lo anterior ...

    // === NUEVA — G3 ===
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'
}
```

```bash
./gradlew build --refresh-dependencies
```

Verificar que levanta en: `http://localhost:8080/swagger-ui.html`

---

## 2. Configurar OpenAPI con metadatos del proyecto

`src/main/java/com/example/demo/config/OpenApiConfig.java`

```java
package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Sistema de Gestión de Trámites — API")
                        .description("""
                                Backend del sistema de gestión de trámites.
                                Motor de Workflow + Expediente Digital + IA.
                                
                                **Roles del sistema:**
                                - `Administrador` — diseña flujos, gestiona configuración
                                - `Funcionario` — ejecuta actividades asignadas
                                - `Cliente` — solicita y consulta trámites
                                
                                **Autenticación:** JWT Bearer token.
                                Obtener token en `POST /api/auth/login` y pegarlo en el botón Authorize.
                                """)
                        .version("1.0.0 — Ciclo 1")
                        .contact(new Contact()
                                .name("Luis David Guzmán Rojas")
                                .email("admin@cre.bo")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Pega aquí el token obtenido en /api/auth/login")));
    }
}
```

---

## 3. Agregar Swagger a `SecurityConfig`

Swagger necesita acceso público a su UI y a los JSON de spec. Agrega estas rutas como `permitAll()`:

```java
.authorizeHttpRequests(auth -> auth
    // Swagger
    .requestMatchers(
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/v3/api-docs"
    ).permitAll()

    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/health/**").permitAll()
    // ... resto igual que antes
)
```

---

## 4. Documentar los controllers con anotaciones Swagger

Agregar en cada controller las anotaciones mínimas para que el PDF quede claro. Ejemplos:

### 4.1. `AuthController` — anotaciones

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Registro de clientes y login de todos los roles")
public class AuthController {

    @Operation(summary = "Registro de cliente",
               description = "Solo crea usuarios de tipo 'cliente'. Para crear funcionarios o admins usar POST /api/usuarios/crear")
    @PostMapping("/register-cliente")
    public ResponseEntity<AuthResponse> registerCliente(...) { ... }

    @Operation(summary = "Login", description = "Válido para los 3 roles. Devuelve JWT Bearer token")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(...) { ... }
}
```

### 4.2. `DepartamentoController` — anotaciones

```java
@Tag(name = "Departamentos", description = "GET: cualquier autenticado · POST/PUT/DELETE: solo administrador")
public class DepartamentoController {

    @Operation(summary = "Listar departamentos",
               description = "Parámetro opcional: soloActivos=true para filtrar inactivos")
    @GetMapping
    public ResponseEntity<List<Departamento>> listar(...) { ... }

    @Operation(summary = "Crear departamento", description = "Código único, máximo 5 caracteres en mayúsculas")
    @PostMapping
    public ResponseEntity<Departamento> crear(...) { ... }
}
```

### 4.3. `PoliticaNegocioController` — anotaciones

```java
@Tag(name = "Políticas de Negocio",
     description = "Estados: borrador → activa → archivada (flujo unidireccional)")
public class PoliticaNegocioController {

    @Operation(summary = "Cambiar estado de política",
               description = "Solo transiciones válidas: borrador→activa o activa→archivada. Al activar, archiva automáticamente cualquier versión anterior activa con el mismo nombre.")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<PoliticaNegocio> cambiarEstado(...) { ... }
}
```

> 💡 **Para el PDF:** con estas anotaciones, captura pantalla de `http://localhost:8080/swagger-ui.html` y pégala en el documento. Muestra todos los endpoints organizados por módulo.

---

## 5. Endpoint de Roles (lectura)

El administrador necesita ver los roles disponibles para asignarlos al crear usuarios.

### 5.1. `RolService.java`

```java
package com.example.demo.services;

import com.example.demo.models.Rol;
import com.example.demo.repositories.RolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RolService {

    @Autowired
    private RolRepository rolRepository;

    public List<Rol> listarTodos() {
        return rolRepository.findAll();
    }

    public Optional<Rol> buscarPorId(String id) {
        return rolRepository.findById(id);
    }

    public Optional<Rol> buscarPorNombre(String nombre) {
        return rolRepository.findByNombre(nombre);
    }
}
```

### 5.2. `RolController.java`

```java
package com.example.demo.controllers;

import com.example.demo.models.Rol;
import com.example.demo.services.RolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Consulta de roles disponibles — solo lectura")
public class RolController {

    @Autowired
    private RolService rolService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar roles", description = "Devuelve los 4 roles del sistema: SuperUser, Administrador, Funcionario, Cliente")
    public ResponseEntity<List<Rol>> listar() {
        return ResponseEntity.ok(rolService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Buscar rol por ID")
    public ResponseEntity<Rol> buscar(@PathVariable String id) {
        return rolService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

---

## 6. Mejorar el HealthController para el demo

El health check actual es básico. Agrega información útil para mostrar en vivo:

```java
package com.example.demo.controllers;

import com.example.demo.repositories.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Estado del sistema y conteo de colecciones MongoDB")
public class HealthController {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private TramiteRepository tramiteRepository;

    @GetMapping
    @Operation(summary = "Estado del sistema",
               description = "Muestra estado, timestamp y conteo de colecciones principales. No requiere autenticación.")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", LocalDateTime.now());
        result.put("service", "tramites-backend");
        result.put("ciclo", "Ciclo 1 — Completado");

        Map<String, Long> colecciones = new LinkedHashMap<>();
        colecciones.put("usuarios", usuarioRepository.count());
        colecciones.put("roles", rolRepository.count());
        colecciones.put("departamentos", departamentoRepository.count());
        colecciones.put("actividades", actividadRepository.count());
        colecciones.put("politicas_negocio", politicaRepository.count());
        colecciones.put("tramites", tramiteRepository.count());
        result.put("colecciones", colecciones);

        return result;
    }
}
```

**Respuesta esperada tras G1 + G2 completas:**
```json
{
  "status": "UP",
  "timestamp": "2025-04-27T20:00:00",
  "service": "tramites-backend",
  "ciclo": "Ciclo 1 — Completado",
  "colecciones": {
    "usuarios": 3,
    "roles": 4,
    "departamentos": 4,
    "actividades": 6,
    "politicas_negocio": 1,
    "tramites": 0
  }
}
```

---

## 7. Resumen de todos los endpoints del Ciclo 1

### Públicos (sin token)
| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET`  | `/api/health` | Estado y conteo de colecciones |
| `POST` | `/api/auth/register-cliente` | Registro de clientes |
| `POST` | `/api/auth/login` | Login — devuelve JWT |
| `GET`  | `/swagger-ui.html` | Documentación interactiva |
| `GET`  | `/v3/api-docs` | Spec OpenAPI en JSON |

### Autenticados (cualquier rol)
| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET`  | `/api/usuarios/me` | Ver mi propio perfil |
| `GET`  | `/api/departamentos` | Listar departamentos |
| `GET`  | `/api/departamentos/{id}` | Ver departamento |
| `GET`  | `/api/actividades` | Listar actividades (filtros: dpto, reutilizables) |
| `GET`  | `/api/actividades/{id}` | Ver actividad |
| `GET`  | `/api/politicas` | Listar políticas (filtro: soloActivas) |
| `GET`  | `/api/politicas/{id}` | Ver política |

### Solo Administrador
| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST`   | `/api/usuarios/crear` | Crear funcionario o administrador |
| `GET`    | `/api/usuarios` | Listar todos los usuarios |
| `GET`    | `/api/usuarios/{id}` | Ver usuario por ID |
| `PUT`    | `/api/usuarios/{id}` | Actualizar usuario |
| `DELETE` | `/api/usuarios/{id}` | Desactivar usuario |
| `GET`    | `/api/roles` | Listar roles disponibles |
| `GET`    | `/api/roles/{id}` | Ver rol por ID |
| `POST`   | `/api/departamentos` | Crear departamento |
| `PUT`    | `/api/departamentos/{id}` | Actualizar departamento |
| `DELETE` | `/api/departamentos/{id}` | Desactivar departamento |
| `POST`   | `/api/actividades` | Crear actividad |
| `PUT`    | `/api/actividades/{id}` | Actualizar actividad |
| `DELETE` | `/api/actividades/{id}` | Eliminar actividad |
| `POST`   | `/api/politicas` | Crear política (estado inicial: borrador) |
| `PUT`    | `/api/politicas/{id}` | Actualizar política |
| `PATCH`  | `/api/politicas/{id}/estado` | Cambiar estado (activar/archivar) |
| `DELETE` | `/api/politicas/{id}` | Eliminar política (solo borrador/archivada) |

---

## 8. Script de demo — Presentación 28 de abril

> ⏱️ **Total 20 minutos.** Distribuye así:

### Minuto 0–2: Arrancar el sistema

```bash
# Terminal 1 — Docker
docker-compose up -d
# Esperar 5 segundos y verificar
curl http://localhost:8080/api/health
```

Mostrar en el navegador:
- `http://localhost:8081` → Mongo Express (demostrar colecciones con datos)
- `http://localhost:8080/swagger-ui.html` → Swagger (demostrar endpoints)

---

### Minuto 2–5: Demostrar autenticación y roles

En Swagger o Postman:

1. **Login como admin:**
```json
POST /api/auth/login
{ "email": "admin@cre.bo", "password": "admin12345" }
```
Copiar el token → clic en **Authorize** en Swagger.

2. **Mostrar `/me`:** el sistema sabe quién eres.

3. **Crear un funcionario:**
```json
POST /api/usuarios/crear
{
  "nombre": "Carlos", "apellido": "Lima",
  "email": "carlos@cre.bo", "password": "func12345",
  "tipo": "funcionario", "departamentosIds": ["ID_TEC"]
}
```

4. **Probar que un cliente no puede crear usuarios:**
   - Login como cliente → intentar `POST /api/usuarios/crear` → `403 Forbidden`
   - **Decir al profesor:** "La separación de roles está implementada a nivel de JWT y de `@PreAuthorize` por método."

---

### Minuto 5–10: Demostrar configuración del sistema

1. **Listar departamentos** — mostrar los 4 del ejemplo CRE (ATC, TEC, LEG, OPE).

2. **Listar actividades** — mostrar las del flujo CRE con sus SLA.

3. **Mostrar la política activa:**
```
GET /api/politicas?soloActivas=true
```
Resultado: "Nueva conexión residencial" en estado `activa`.

4. **Demostrar la máquina de estados** — intentar archivar la política y mostrar que el sistema acepta solo la transición válida:
```json
PATCH /api/politicas/{id}/estado
{ "estado": "archivada" }
```

5. **Mostrar en Mongo Express** la colección `politicas_negocio` con el campo `estado` actualizado en tiempo real.

---

### Minuto 10–14: Mostrar el diagrama de actividad UML 2.5

> Este punto es **el más importante según el enunciado**.

Abrir Enterprise Architect y mostrar:
- Swimlanes: Atención al Cliente / Área Técnica / Área Legal / Operaciones
- Nodos: inicio, actividades, fork/join (paralelo inspección+presupuesto), decision node (legal aprueba/devuelve), fin
- El diagrama corresponde exactamente a la política "Nueva conexión residencial"

**Frase clave para decir:** *"Este diagrama UML 2.5 es la representación visual del flujo que el Motor de Workflow va a interpretar y ejecutar en Ciclo 2. Cada calle es un departamento ya creado en la base de datos."*

---

### Minuto 14–17: Mostrar Swagger como documentación viva

En `http://localhost:8080/swagger-ui.html`:
- Expandir el tag **Autenticación** — mostrar los 2 endpoints con su descripción
- Expandir **Políticas de Negocio** — mostrar el `PATCH /estado` y explicar la máquina de estados
- Expandir **Departamentos** — ejecutar el `GET` en vivo desde Swagger mismo
- **Decir al profesor:** *"La documentación se genera automáticamente desde el código con OpenAPI 3.0. El PDF incluye capturas de esta interfaz."*

---

### Minuto 17–20: Cierre y preguntas

Mostrar Mongo Express con todas las colecciones del Ciclo 1:
- `usuarios` — con `passwordHash` hasheado (nunca texto plano)
- `departamentos` — 4 departamentos CRE
- `actividades` — con `slaHoras` configurado
- `politicas_negocio` — con campo `estado`

**Frase de cierre:** *"El Ciclo 1 entrega la base completa: autenticación segura, configuración flexible del sistema, y la política de negocio activa. El Ciclo 2 conecta esto al Motor de Workflow que ejecuta el diagrama UML en vivo, moviendo trámites entre departamentos automáticamente."*

---

## 9. Checklist de entregables G3

- [ ] Dependencia `springdoc-openapi-starter-webmvc-ui:2.8.8` en `build.gradle`
- [ ] `OpenApiConfig.java` con metadata del proyecto + esquema JWT Bearer
- [ ] Rutas de Swagger agregadas al `SecurityConfig` como `permitAll()`
- [ ] `@Tag` y `@Operation` en todos los controllers (Auth, Usuarios, Roles, Departamentos, Actividades, Políticas)
- [ ] `RolService` + `RolController` (lectura — solo admin)
- [ ] `HealthController` actualizado con conteo de las 6 colecciones principales
- [ ] `http://localhost:8080/swagger-ui.html` abre sin token y muestra todos los módulos
- [ ] Login desde Swagger → botón **Authorize** → endpoints protegidos funcionan
- [ ] Script de demo ensayado al menos una vez cronometrado (20 min)
- [ ] Captura de Swagger incluida en el PDF del examen
- [ ] Mongo Express muestra datos reales en todas las colecciones

---

## 10. Resumen de Ciclo 1 — Qué se construyó

```
Ciclo 1 — Completado
│
├── Infraestructura
│   ├── MongoDB 7.0 + Mongo Express (Docker)
│   ├── 29 colecciones modeladas + 18 repositorios
│   └── 11 índices de performance
│
├── G1 — Autenticación y Usuarios
│   ├── Spring Security + JWT (JJWT 0.12.6)
│   ├── BCrypt para passwords
│   ├── Registro público: solo clientes (/register-cliente)
│   ├── Admin crea funcionarios/admins (/usuarios/crear)
│   ├── /me para cualquier rol autenticado
│   └── GlobalExceptionHandler (respuestas JSON uniformes)
│
├── G2 — Configuración del Sistema
│   ├── Departamentos (CRUD + desactivar, código único)
│   ├── Actividades (CRUD + SLA + validación de departamento activo)
│   └── Políticas de negocio (CRUD + máquina de estados borrador→activa→archivada)
│
└── G3 — Documentación y Demo
    ├── Swagger / OpenAPI con auth JWT Bearer
    ├── Roles (lectura para admin)
    ├── Health check mejorado
    └── Script de demo para presentación 28 de abril
```

---

## 11. Qué viene en Ciclo 2

| Módulo | Descripción |
|--------|-------------|
| **Motor de Workflow** | Parser del diagrama, ejecutor de nodos, fork/join, decision nodes |
| **Expediente Digital** | Secciones por nodo, bloqueos, campos dinámicos |
| **Trámites** | Crear, avanzar, observar, aprobar, rechazar |
| **Notificaciones** | Push para clientes (FCM), alertas web para funcionarios |
| **Colaboración** | Editor en tiempo real de diagramas (WebSocket) |

---

## 🛠️ Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| `swagger-ui.html` devuelve 401 | Rutas Swagger no están en `permitAll()` | Agregar las 4 rutas de Swagger al SecurityConfig |
| Swagger no muestra el botón Authorize | Falta `SecurityRequirement` en OpenApiConfig | Verificar `.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))` |
| Token en Swagger expira durante el demo | JWT por defecto dura 24h | Para el demo, hacer login justo antes. Si expira, login de nuevo |
| `Failed to load API definition` en Swagger UI | Spring Security bloqueando `/v3/api-docs` | Agregar `/v3/api-docs/**` al `permitAll()` |
| Colecciones vacías en health | Seed no se ejecutó o volumen corrupto | `docker-compose down -v && docker-compose up -d` |

---

*Guía 3 — Ciclo 1 · Sistema de Gestión de Trámites · Primer Examen Parcial*
