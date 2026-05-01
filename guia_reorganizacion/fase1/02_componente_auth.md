# Fase 1.2 · Componente auth

> Extraer todo lo relacionado con autenticación, JWT, gestión de usuarios y configuración de seguridad.

---

## 1. Objetivo

Encapsular la autenticación tras un puerto `AuthPort` y un `JwtPort`, de modo que otros componentes puedan saber **quién es el usuario actual** sin acoplarse a cómo se valida un token.

---

## 2. Archivos involucrados

### Modelos y repositorios
| Origen | Destino |
|--------|---------|
| `models/Usuario.java` | `modules/auth/domain/Usuario.java` |
| `models/Rol.java` | `modules/auth/domain/Rol.java` *(o queda en catalogo si se prefiere)* |
| `repositories/UsuarioRepository.java` | `modules/auth/internal/UsuarioRepository.java` |

### Services y controllers
| Origen | Destino |
|--------|---------|
| `services/AuthService.java` | `modules/auth/internal/AuthServiceImpl.java` (renombrado) |
| `services/UsuarioService.java` | `modules/auth/internal/UsuarioServiceImpl.java` (renombrado) |
| `controllers/AuthController.java` | `modules/auth/internal/AuthController.java` |
| `controllers/UsuarioController.java` | `modules/auth/internal/UsuarioController.java` |

### Seguridad
| Origen | Destino |
|--------|---------|
| `security/JwtUtils.java` | `modules/auth/internal/JwtUtils.java` |
| `security/JwtAuthFilter.java` | `modules/auth/internal/JwtAuthFilter.java` |
| `config/SecurityConfig.java` | `modules/auth/internal/SecurityConfig.java` |

### DTOs
| Origen | Destino |
|--------|---------|
| `dto/LoginRequest.java` | `modules/auth/api/dto/LoginRequest.java` |
| `dto/AuthResponse.java` | `modules/auth/api/dto/AuthResponse.java` |
| `dto/RegisterClienteRequest.java` | `modules/auth/api/dto/RegisterClienteRequest.java` |
| `dto/CrearUsuarioAdminRequest.java` | `modules/auth/api/dto/CrearUsuarioAdminRequest.java` |

> **Decisión sobre `Rol`:** los roles los administra el admin desde la web, así que conceptualmente pertenecen a `catalogo-configuracion`. Pero `Usuario` referencia `rolId`. Para evitar dependencia circular, dejamos `Rol` en `catalogo` y `auth` solo lo consume (vía `CatalogoPort.buscarRol(id)` cuando se necesite, o directamente por `id` String al validar JWT).

---

## 3. Estructura final

```
modules/auth/
├── api/
│   ├── AuthPort.java                        ← interfaz pública
│   ├── JwtPort.java                         ← interfaz para validar tokens
│   └── dto/
│       ├── LoginRequest.java
│       ├── AuthResponse.java
│       ├── RegisterClienteRequest.java
│       ├── CrearUsuarioAdminRequest.java
│       └── UsuarioInfo.java                 ← NUEVA, DTO público con info del usuario
├── domain/
│   └── Usuario.java
├── internal/
│   ├── AuthServiceImpl.java                 ← implementa AuthPort
│   ├── UsuarioServiceImpl.java
│   ├── UsuarioRepository.java
│   ├── AuthController.java
│   ├── UsuarioController.java
│   ├── JwtUtils.java                        ← implementa JwtPort
│   ├── JwtAuthFilter.java
│   └── SecurityConfig.java
├── package-info.java
└── README.md
```

---

## 4. Definición del puerto

### `api/AuthPort.java`

```java
package com.example.demo.modules.auth.api;

import com.example.demo.modules.auth.api.dto.*;

public interface AuthPort {

    /** Autentica un usuario con email y password, devuelve token + datos. */
    AuthResponse login(LoginRequest req);

    /** Registra un nuevo cliente (rol fijo CLIENTE). */
    AuthResponse registerCliente(RegisterClienteRequest req);

    /** Crea un usuario administrador o funcionario (solo lo usa el admin). */
    UsuarioInfo crearUsuarioAdmin(CrearUsuarioAdminRequest req);

    /** Obtiene los datos del usuario actualmente autenticado. */
    UsuarioInfo obtenerActual(String userId);
}
```

### `api/JwtPort.java`

```java
package com.example.demo.modules.auth.api;

import java.util.Optional;

public interface JwtPort {

    /** Valida un token JWT y devuelve el userId si es válido. */
    Optional<String> validarYExtraerUserId(String token);

    /** Valida un token y devuelve el rol si es válido. */
    Optional<String> validarYExtraerRol(String token);

    /** Genera un token nuevo para el usuario dado. */
    String generar(String userId, String email, String rol);
}
```

### `api/dto/UsuarioInfo.java` (nuevo, evita exponer la entidad `Usuario` cruda)

```java
package com.example.demo.modules.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UsuarioInfo {
    private String id;
    private String email;
    private String nombre;
    private String apellido;
    private String tipo;          // CLIENTE / FUNCIONARIO / ADMINISTRADOR
    private boolean activo;
}
```

---

## 5. Pasos de migración

### Paso A — Mover archivos (IntelliJ)

Refactor → Move para cada archivo de la tabla del punto 2.

### Paso B — Crear las interfaces `AuthPort` y `JwtPort`

1. Crear `api/AuthPort.java` con el contenido del punto 4.
2. Crear `api/JwtPort.java` con el contenido del punto 4.
3. Crear `api/dto/UsuarioInfo.java`.

### Paso C — Adaptar las implementaciones

#### `AuthServiceImpl`
- Anotar con `implements AuthPort`
- Asegurar que los métodos públicos coinciden con la interfaz
- Agregar método `obtenerActual(String userId)` si no existe (puede delegar al `UsuarioServiceImpl`)
- Quitar `public` de la clase si Spring lo permite

#### `JwtUtils` → `implements JwtPort`
- Cambiar firmas de `validarYExtraer` para devolver `Optional<String>` en lugar de `Claims`
- Mantener el método `generar` pero ajustar a la firma del Port
- Renombrar archivo a `JwtServiceImpl` (opcional pero alinea con convención)

#### `JwtAuthFilter`
- Cambiar dependencia: en vez de `@Autowired private JwtUtils`, usar `@Autowired private JwtPort jwtPort`
- Reemplazar las llamadas: `jwtUtils.esValido(token)` → `jwtPort.validarYExtraerUserId(token).isPresent()`

### Paso D — Adaptar consumidores externos

Buscar en todo el proyecto quién usa `JwtUtils`, `AuthService`, `UsuarioService`. Probable lista:
- `JwtAuthFilter` → usa `JwtUtils` (lo cambiamos arriba)
- Todos los `*Controller` que usen `Authentication auth` — esos NO cambian (Spring inyecta `Authentication`, no `AuthService`)

### Paso E — Crear `package-info.java`

```java
/**
 * Componente: auth
 *
 * Propósito:
 *   Autenticación y gestión de usuarios. Genera y valida JWTs.
 *
 * Puerto público:
 *   - AuthPort — login, registro, gestión de usuarios
 *   - JwtPort — generación y validación de tokens
 *
 * Consume: ninguno (no depende de otros componentes de negocio)
 *
 * Es consumido por:
 *   - todos los componentes (a través del JwtAuthFilter de Spring Security)
 *
 * Colecciones MongoDB:
 *   - usuarios
 */
package com.example.demo.modules.auth;
```

### Paso F — Crear README del componente

Plantilla en `00_preparacion.md` punto 6, completar con datos.

---

## 6. Verificación

### 6.1 Compilar
`./mvnw clean compile` → debe pasar.

### 6.2 Levantar
`./mvnw spring-boot:run` → debe arrancar.

### 6.3 Smoke tests específicos
| Acción | Esperado |
|--------|----------|
| POST `/api/auth/login` con credenciales válidas | 200 + JWT |
| POST `/api/auth/login` con credenciales inválidas | 401 |
| GET `/api/usuarios/me` con JWT válido | 200 + datos del usuario |
| GET `/api/usuarios/me` sin JWT | 401 |
| GET `/api/departamentos` con JWT válido | 200 (verifica que JwtAuthFilter sigue inyectando rol correcto) |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente auth con AuthPort y JwtPort"
```

---

## 8. Riesgos y notas

- **Cuidado con `SecurityConfig`**: define el filter chain. Tras moverlo, verificar que sigue siendo detectado por Spring (debe quedar `@Configuration` y dentro del classpath de `@SpringBootApplication`, lo cual sí cumple si está bajo `com.example.demo`).
- **`@PreAuthorize` en otros controllers**: no se rompen, ya que solo evalúan el `Authentication` de Spring Security.
- **Bug que ya arreglamos** del `rol` null en `JwtAuthFilter` debe estar presente en la versión movida.

---

## Próximo paso

Continuar con **`03_componente_notificaciones.md`**.
