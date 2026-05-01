# Guía 1 — Autenticación JWT + Gestión de Usuarios y Roles

**Ciclo 1 · Sistema de Gestión de Trámites**

> 🎯 **Objetivo de esta guía:** completar lo que falta de la base. La infraestructura (Docker, MongoDB, modelos, repositorios) ya está lista. Aquí agregamos **seguridad, autenticación JWT y endpoints de usuarios/roles** para dejar el backend listo para Ciclo 2.

---

## 0. Prerequisitos (ya hecho)

✅ Docker Compose con MongoDB + Mongo Express funcionando
✅ Spring Boot con `spring-boot-starter-data-mongodb`, `validation`, `lombok`
✅ 29 modelos del diagrama Mermaid creados
✅ 18 repositorios MongoDB
✅ Índices MongoDB configurados
✅ Seed inicial con roles, permisos, departamentos

📁 Stack actual: **Spring Boot 4.0.6 + Java 17 + Gradle + MongoDB 7.0**

> 📌 **Nota sobre Spring Boot 4.0:** Esta versión introduce `spring-boot-starter-webmvc` como starter MVC explícito (separado de WebFlux). Es correcto para este proyecto. El starter de test cambió a `spring-boot-starter-test` (eliminar cualquier referencia a `webmvc-test`).

---

## 1. Lo que vamos a construir en esta guía

| Feature | Descripción |
|---------|-------------|
| 🔐 **Spring Security + JWT** | Protección de endpoints, tokens firmados |
| 🔑 **BCrypt** | Hash seguro de contraseñas |
| 📝 **Registro y Login** | `/api/auth/register-cliente` (público) + `/api/auth/login` |
| 👤 **CRUD de Usuarios** | Alta/baja/modificación solo por administrador |
| 🎭 **Autorización por rol** | `@PreAuthorize("hasRole('ADMINISTRADOR')")` |
| 🌐 **CORS** | Permitir llamadas desde Angular (localhost:4200) y Flutter |
| ⚠️ **Manejador global de errores** | Respuestas JSON uniformes |

---

## 2. Agregar dependencias a `build.gradle`

Edita el bloque `dependencies` y agrega estas líneas al final:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // === NUEVAS DEPENDENCIAS PARA LA GUÍA 1 ===
    implementation 'org.springframework.boot:spring-boot-starter-security'

    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    // ==========================================

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'      // ← NO es webmvc-test
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

Luego en terminal:

```bash
./gradlew build --refresh-dependencies
```

---

## 3. Agregar propiedades JWT a `application.yml`

```yaml
spring:
  application:
    name: tramites-backend

  data:
    mongodb:
      uri: mongodb://admin:12345678@localhost:27017/tramites_db?authSource=admin
      auto-index-creation: true

server:
  port: 8080

# === CONFIGURACIÓN JWT ===
app:
  jwt:
    secret: "clave-ultra-secreta-para-firmar-tokens-cambiar-en-produccion-minimo-256-bits"
    expiration-ms: 86400000  # 24 horas
    issuer: "tramites-backend"

logging:
  level:
    org.springframework.data.mongodb.core.MongoTemplate: INFO
    com.example.demo: DEBUG
```

> ⚠️ **Para producción:** mover `secret` a variable de entorno `APP_JWT_SECRET` y no commitear.

---

## 4. Crear los DTOs (Data Transfer Objects)

Crear la carpeta `src/main/java/com/example/demo/dto/`:

### 4.1. `LoginRequest.java`

```java
package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email es obligatorio")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "Contraseña es obligatoria")
    private String password;
}
```

### 4.2. `RegisterClienteRequest.java` (solo auto-registro de clientes)

> 🔒 **Por qué un DTO separado:** El registro público **solo permite crear clientes**. Funcionarios y Administradores los crea un Administrador desde un endpoint protegido. Si el tipo fuera un campo libre en el request, cualquier persona podría registrarse como admin y tomar control del sistema.

```java
package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterClienteRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    private String apellido;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    private String telefono;

    // tipo NO es un campo — siempre será "cliente" forzado por el servicio
}
```

### 4.3. `CrearUsuarioAdminRequest.java` (usado solo por administrador)

```java
package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CrearUsuarioAdminRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    private String apellido;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotBlank
    @Pattern(regexp = "funcionario|administrador",
             message = "Solo se puede crear funcionario o administrador por esta vía")
    private String tipo;

    private String telefono;
    private List<String> departamentosIds;
}
```

### 4.4. `AuthResponse.java`

```java
package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tipo = "Bearer";
    private String email;
    private String nombre;
    private String rol;
    private String userId;
}
```

### 4.5. `ErrorResponse.java`

```java
package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String path;
    private List<String> details;
}
```

---

## 5. Utilidad JWT

`src/main/java/com/example/demo/security/JwtUtils.java`

```java
package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${app.jwt.issuer}")
    private String issuer;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(String userId, String email, String rol) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expirationMs);

        return Jwts.builder()
                .issuer(issuer)
                .subject(userId)
                .claim("email", email)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(getKey())
                .compact();
    }

    public Claims validarYExtraer(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean esValido(String token) {
        try {
            validarYExtraer(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extraerUserId(String token) {
        return validarYExtraer(token).getSubject();
    }

    public String extraerEmail(String token) {
        return validarYExtraer(token).get("email", String.class);
    }

    public String extraerRol(String token) {
        return validarYExtraer(token).get("rol", String.class);
    }
}
```

---

## 6. Filtro JWT

`src/main/java/com/example/demo/security/JwtAuthFilter.java`

```java
package com.example.demo.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtils.esValido(token)) {
                Claims claims = jwtUtils.validarYExtraer(token);
                String userId = claims.getSubject();
                String rol = claims.get("rol", String.class);

                var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(request, response);
    }
}
```

---

## 7. Configuración de Spring Security

`src/main/java/com/example/demo/config/SecurityConfig.java`

```java
package com.example.demo.config;

import com.example.demo.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                // /me es para cualquier rol — el @PreAuthorize del método lo controla
                .requestMatchers("/api/usuarios/me").authenticated()
                // el resto de /usuarios requiere admin — doble protección
                .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
            "http://localhost:4200",   // Angular
            "http://localhost:8100",   // Ionic / web dev
            "http://localhost:3000"    // Flutter web dev
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
```

---

## 8. Servicio de autenticación

`src/main/java/com/example/demo/services/AuthService.java`

```java
package com.example.demo.services;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.CrearUsuarioAdminRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterClienteRequest;
import com.example.demo.models.Rol;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.RolRepository;
import com.example.demo.repositories.UsuarioRepository;
import com.example.demo.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtils jwtUtils;

    // ─── Registro público: tipo SIEMPRE es "cliente" ────────────────────────
    public AuthResponse registrarCliente(RegisterClienteRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        Rol rol = rolRepository.findByNombre("Cliente")
                .orElseThrow(() -> new IllegalStateException("Rol 'Cliente' no encontrado. ¿Corrió el seed?"));

        Usuario u = new Usuario();
        u.setNombre(req.getNombre());
        u.setApellido(req.getApellido());
        u.setEmail(req.getEmail());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRolId(rol.getId());
        u.setTipo("cliente");                      // hardcoded — no viene del request
        u.setTelefono(req.getTelefono());
        u.setActivo(true);
        u.setFechaRegistro(LocalDateTime.now());

        return construirAuthResponse(usuarioRepository.save(u), rol);
    }

    // ─── Creación por admin: puede crear funcionario o administrador ─────────
    public Usuario crearUsuarioPorAdmin(CrearUsuarioAdminRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        String nombreRol = capitalizar(req.getTipo());
        Rol rol = rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new IllegalStateException("Rol no encontrado: " + nombreRol));

        Usuario u = new Usuario();
        u.setNombre(req.getNombre());
        u.setApellido(req.getApellido());
        u.setEmail(req.getEmail());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRolId(rol.getId());
        u.setDepartamentosIds(req.getDepartamentosIds());
        u.setTipo(req.getTipo());
        u.setTelefono(req.getTelefono());
        u.setActivo(true);
        u.setFechaRegistro(LocalDateTime.now());

        return usuarioRepository.save(u);
    }

    // ─── Login ────────────────────────────────────────────────────────────────
    public AuthResponse login(LoginRequest req) {
        Usuario u = usuarioRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!u.isActivo()) {
            throw new IllegalArgumentException("Usuario inactivo. Contacte al administrador");
        }

        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        Rol rol = rolRepository.findById(u.getRolId())
                .orElseThrow(() -> new IllegalStateException("Rol del usuario no existe"));

        u.setUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(u);

        return construirAuthResponse(u, rol);
    }

    private AuthResponse construirAuthResponse(Usuario u, Rol rol) {
        String token = jwtUtils.generarToken(u.getId(), u.getEmail(), rol.getNombre());
        return new AuthResponse(token, "Bearer", u.getEmail(),
                u.getNombre() + " " + u.getApellido(), rol.getNombre(), u.getId());
    }

    private String capitalizar(String tipo) {
        if (tipo == null || tipo.isEmpty()) return tipo;
        return Character.toUpperCase(tipo.charAt(0)) + tipo.substring(1).toLowerCase();
    }
}
```

---

## 9. Controller de autenticación

`src/main/java/com/example/demo/controllers/AuthController.java`

```java
package com.example.demo.controllers;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterClienteRequest;
import com.example.demo.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    // Solo para clientes — endpoint público
    @PostMapping("/register-cliente")
    public ResponseEntity<AuthResponse> registerCliente(@Valid @RequestBody RegisterClienteRequest req) {
        return ResponseEntity.ok(authService.registrarCliente(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
```

> 🔒 **No existe `/api/auth/register` genérico.** El único registro público crea clientes. Para crear funcionarios o administradores, ver el endpoint `POST /api/usuarios/crear` en la sección 10.

---

## 10. Servicio y controller de usuarios (solo admin)

### 10.1. `UsuarioService.java`

```java
package com.example.demo.services;

import com.example.demo.models.Usuario;
import com.example.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public List<Usuario> listarActivos() {
        return usuarioRepository.findByActivoTrue();
    }

    public List<Usuario> listarPorTipo(String tipo) {
        return usuarioRepository.findByTipo(tipo);
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario actualizar(String id, Usuario datos) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        existente.setNombre(datos.getNombre());
        existente.setApellido(datos.getApellido());
        existente.setTelefono(datos.getTelefono());
        existente.setDepartamentosIds(datos.getDepartamentosIds());
        existente.setActivo(datos.isActivo());

        return usuarioRepository.save(existente);
    }

    public void desactivar(String id) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        u.setActivo(false);
        usuarioRepository.save(u);
    }
}
```

### 10.2. `UsuarioController.java`

> 🔑 **Decisión de diseño:** el `@PreAuthorize` va **por método**, no en la clase. Así `/me` puede usarlo cualquier rol autenticado (cliente, funcionario, administrador) sin romper la protección del resto de endpoints.

```java
package com.example.demo.controllers;

import com.example.demo.dto.CrearUsuarioAdminRequest;
import com.example.demo.models.Usuario;
import com.example.demo.services.AuthService;
import com.example.demo.services.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired private UsuarioService usuarioService;
    @Autowired private AuthService authService;

    // ── Cualquier usuario autenticado puede ver su propio perfil ───────────
    // Authentication.getName() devuelve el userId que pusimos como subject en el JWT
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Usuario> miPerfil(Authentication auth) {
        return usuarioService.buscarPorId(auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Los siguientes endpoints solo son para administradores ────────────
    @PostMapping("/crear")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Usuario> crear(@Valid @RequestBody CrearUsuarioAdminRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.crearUsuarioPorAdmin(req));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<Usuario>> listar(@RequestParam(required = false) String tipo) {
        if (tipo != null) {
            return ResponseEntity.ok(usuarioService.listarPorTipo(tipo));
        }
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @GetMapping("/activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<Usuario>> listarActivos() {
        return ResponseEntity.ok(usuarioService.listarActivos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Usuario> buscar(@PathVariable String id) {
        return usuarioService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Usuario> actualizar(@PathVariable String id, @RequestBody Usuario datos) {
        return ResponseEntity.ok(usuarioService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> desactivar(@PathVariable String id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 11. Manejador global de errores

`src/main/java/com/example/demo/config/GlobalExceptionHandler.java`

```java
package com.example.demo.config;

import com.example.demo.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> manejarIllegalArgument(IllegalArgumentException ex,
                                                                HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> manejarIllegalState(IllegalStateException ex,
                                                             HttpServletRequest req) {
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(AccessDeniedException ex,
                                                                HttpServletRequest req) {
        return construir(HttpStatus.FORBIDDEN, "Acceso denegado", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex,
                                                            HttpServletRequest req) {
        List<String> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return construir(HttpStatus.BAD_REQUEST, "Datos inválidos", req, errores);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarGenerico(Exception ex, HttpServletRequest req) {
        return construir(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error inesperado: " + ex.getMessage(), req, null);
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String mensaje,
                                                     HttpServletRequest req, List<String> detalles) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                mensaje,
                req.getRequestURI(),
                detalles
        );
        return ResponseEntity.status(status).body(body);
    }
}
```

---

## 12. Probar con Postman / curl

### 12.1. Levantar todo

```bash
# Terminal 1
docker-compose up -d

# Terminal 2
./gradlew bootRun
```

### 12.2. Crear el primer administrador (via seed o directamente en Mongo)

El primer administrador **no puede crearse por API** porque el endpoint requiere token de admin. Hay dos opciones:

**Opción A — Insertar directamente en Mongo Express** (`http://localhost:8081`):
```json
// Colección: usuarios
{
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "admin@cre.bo",
  "passwordHash": "$2a$10$hash-generado-con-bcrypt",
  "tipo": "administrador",
  "activo": true,
  "fechaRegistro": { "$date": "2025-04-24T00:00:00Z" }
}
```

**Opción B — Agregar al seed** `mongo-init/01-seed.js` (más limpio para demo):
```javascript
// Añadir al 01-seed.js antes del print final
var rolAdmin = db.roles.findOne({ nombre: "Administrador" });
db.usuarios.insertOne({
    nombre: "Admin",
    apellido: "Sistema",
    email: "admin@cre.bo",
    passwordHash: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWq", // "admin12345"
    rolId: rolAdmin._id,
    tipo: "administrador",
    activo: true,
    fechaRegistro: new Date()
});
```

> El hash corresponde a `admin12345` generado con BCrypt factor 10. Para tu propia password, usa un [BCrypt generator](https://bcrypt-generator.com/).

### 12.3. Registrar un cliente (endpoint público)

```bash
curl -X POST http://localhost:8080/api/auth/register-cliente \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "María",
    "apellido": "García",
    "email": "cliente@correo.com",
    "password": "cliente12345",
    "telefono": "71234567"
  }'
```

**Respuesta:** token JWT con rol `Cliente`.

### 12.4. Login (cualquier rol)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cre.bo","password":"admin12345"}'
```

### 12.5. Admin crea un funcionario

```bash
curl -X POST http://localhost:8080/api/usuarios/crear \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN_DEL_ADMIN" \
  -d '{
    "nombre": "Carlos",
    "apellido": "Lima",
    "email": "funcionario@cre.bo",
    "password": "func12345",
    "tipo": "funcionario",
    "departamentosIds": ["ID_DEL_DEPARTAMENTO"]
  }'
```

### 12.6. Cualquier usuario ve su propio perfil con `/me`

```bash
# Con token de cliente, funcionario o admin — todos pueden
curl -X GET http://localhost:8080/api/usuarios/me \
  -H "Authorization: Bearer TOKEN_CUALQUIER_ROL"
# HTTP 200 — devuelve el documento del usuario autenticado
```

### 12.7. Sin token → 401

```bash
curl -X GET http://localhost:8080/api/usuarios
# HTTP 401 Unauthorized
```

### 12.7. Cliente intenta listar usuarios → 403

```bash
curl -X GET http://localhost:8080/api/usuarios \
  -H "Authorization: Bearer TOKEN_DEL_CLIENTE"
# HTTP 403 Forbidden — solo admin puede hacer esto
```

### 12.8. Intentar registrarse como admin por /register-cliente → imposible

```bash
# Este endpoint NO tiene campo "tipo" — siempre crea cliente
# No hay forma de escalarse a admin por el registro público
```

---

## 13. Verificar en Mongo Express

Abrir `http://localhost:8081` → base `tramites_db` → colección `usuarios`.

Debes ver tu usuario con `passwordHash` **hasheado con BCrypt** (empieza por `$2a$...`), nunca en texto plano.

---

## 14. Checklist de entregables Guía 1

- [ ] Dependencias de Spring Security + JJWT + `spring-boot-starter-test` en `build.gradle`
- [ ] Propiedades JWT en `application.yml`
- [ ] DTOs separados: `RegisterClienteRequest`, `CrearUsuarioAdminRequest`, `LoginRequest`, `AuthResponse`, `ErrorResponse`
- [ ] `JwtUtils` + `JwtAuthFilter` funcionando
- [ ] `SecurityConfig` con CORS, rutas públicas, autorización por rol
- [ ] `AuthService` con métodos separados: `registrarCliente()` y `crearUsuarioPorAdmin()`
- [ ] `AuthController`: solo `/register-cliente` público + `/login`
- [ ] `UsuarioController`: `POST /crear` (admin) + CRUD protegido
- [ ] `GlobalExceptionHandler` con respuestas JSON uniformes
- [ ] Primer admin creado vía seed (no por API)
- [ ] Cliente se registra por `/register-cliente` → sin campo tipo en el body
- [ ] Admin crea funcionario por `/api/usuarios/crear` → requiere token admin
- [ ] Login devuelve token JWT válido con el rol correcto
- [ ] `401` sin token / `403` con rol insuficiente verificados
- [ ] Password guardado como hash BCrypt (`$2a$...`) en Mongo Express
- [ ] `GET /api/usuarios/me` funciona con token de cualquier rol (cliente, funcionario, admin)
- [ ] **CRÍTICO:** No existe forma de registrarse como admin/funcionario por endpoint público

---

## 15. Qué sigue (Guía 2)

Con la base segura, la **Guía 2** cubrirá el CRUD de las entidades de configuración:

- **Departamentos** (con jefe asignado)
- **Actividades** (con SLA configurable)
- **Políticas de negocio** (cabecera sin flujo aún — el flujo se diseña en Ciclo 2)

Y la **Guía 3** cerrará el Ciclo 1 con pruebas de integración y documentación Swagger/OpenAPI.

---

## 🛠️ Troubleshooting

| Problema | Solución |
|----------|----------|
| `401 Unauthorized` en login correcto | Revisar que el header sea `Authorization: Bearer <token>` (con espacio) |
| `BCryptPasswordEncoder` bean not found | Confirmar que `SecurityConfig` esté anotado con `@Configuration` |
| `Rol no encontrado: Cliente` | El seed no se ejecutó. Recrea el contenedor: `docker-compose down -v && docker-compose up -d` |
| JWT `WeakKeyException` | El `secret` debe tener mínimo 32 caracteres para HMAC-SHA256 |
| CORS error desde Angular | Agregar `http://localhost:4200` a `corsConfigurationSource()` |
| Build falla con `webmvc-test` | Cambiar a `spring-boot-starter-test` en `build.gradle` |
| `403` al crear funcionario | Usar token de **administrador**, no de cliente o funcionario |
| `Pattern constraint violation` en tipo | Solo se aceptan `"funcionario"` o `"administrador"` en `CrearUsuarioAdminRequest` |

---

*Guía 1 — Ciclo 1 · Sistema de Gestión de Trámites · Primer Examen Parcial*
