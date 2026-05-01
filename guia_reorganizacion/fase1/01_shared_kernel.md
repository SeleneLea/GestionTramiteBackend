# Fase 1.1 · Componente shared (Shared Kernel)

> Extraer el kernel compartido — clases que **todos** los componentes usan: respuesta de errores, manejador global de excepciones, configuración de Mongo y Swagger.

---

## 1. Objetivo

Crear `modules/shared/` con utilidades transversales que cualquier componente puede importar sin generar acoplamiento incorrecto (porque son "infraestructura común", no lógica de negocio ajena).

---

## 2. Archivos involucrados

### Se mueven
| Origen | Destino | Razón |
|--------|---------|-------|
| `dto/ErrorResponse.java` | `modules/shared/api/ErrorResponse.java` | Lo usan todos los exception handlers |
| `config/GlobalExceptionHandler.java` | `modules/shared/internal/GlobalExceptionHandler.java` | Manejador global, único |
| `config/OpenApiConfig.java` | `modules/shared/internal/OpenApiConfig.java` | Config Swagger global |
| `config/MongoIndexConfig.java` | `modules/shared/internal/MongoIndexConfig.java` | Índices Mongo globales |
| `controllers/HealthController.java` | `modules/shared/internal/HealthController.java` | Health check infra |

### Se quedan donde están (por ahora)
- `config/SecurityConfig.java` — se mueve en la subfase 1.2 (auth) porque pertenece a auth
- Todos los DTOs específicos de un componente — se mueven con su componente

---

## 3. Estructura final

```
modules/shared/
├── api/
│   └── ErrorResponse.java                   ← public
├── internal/
│   ├── GlobalExceptionHandler.java          ← package-private si posible
│   ├── OpenApiConfig.java
│   ├── MongoIndexConfig.java
│   └── HealthController.java
├── package-info.java
└── README.md
```

---

## 4. Pasos de migración

### Paso A — Mover archivos (IntelliJ)
1. Click derecho en `dto/ErrorResponse.java` → `Refactor → Move...` → seleccionar paquete `com.example.demo.modules.shared.api`
2. Repetir para `GlobalExceptionHandler`, `OpenApiConfig`, `MongoIndexConfig`, `HealthController` → destino `modules.shared.internal`

> IntelliJ actualiza automáticamente todos los imports en el resto del proyecto.

### Paso B — Verificar visibilidad
Tras mover, intentar quitar `public` a:
- `GlobalExceptionHandler` (si Spring lo permite — debería sí, mientras quede `@RestControllerAdvice`)
- `OpenApiConfig`
- `MongoIndexConfig`

`ErrorResponse` y `HealthController` deben quedar `public` (DTOs y controllers REST necesitan ser públicos para Spring).

### Paso C — Crear `package-info.java`

Archivo: `modules/shared/package-info.java`

```java
/**
 * Componente: shared (Shared Kernel)
 *
 * Propósito:
 *   Utilidades transversales que cualquier componente puede consumir:
 *   formato estándar de respuesta de errores, manejador global de
 *   excepciones, configuración de OpenAPI/Swagger e índices MongoDB.
 *
 * Puerto público:
 *   - ErrorResponse (DTO público) — formato estándar de errores HTTP
 *
 * Consume: ninguno
 *
 * Es consumido por:
 *   - todos los componentes (vía ErrorResponse al lanzar excepciones)
 *
 * Colecciones MongoDB:
 *   - (ninguna propia, define índices sobre colecciones de otros componentes)
 */
package com.example.demo.modules.shared;
```

### Paso D — Crear README

Archivo: `modules/shared/README.md`

```markdown
# Componente: shared

## Propósito
Utilidades transversales (errores, exception handler, config Swagger, índices Mongo).

## Puerto público
- `ErrorResponse` — DTO de respuesta estándar para errores HTTP

## Consume
- (ninguno)

## Es consumido por
- todos los componentes (cuando lanzan excepciones)

## Colecciones Mongo
- (ninguna propia)

## Notas
- `MongoIndexConfig` define índices sobre colecciones de otros componentes.
  En una refactorización futura se podría partir y mover cada bloque de
  índices al componente correspondiente.
- `GlobalExceptionHandler` debe quedar único en el proyecto.
```

---

## 5. Verificación

### 5.1 Compilar
```bash
./mvnw clean compile
```
Debe terminar `BUILD SUCCESS`.

### 5.2 Levantar la app
```bash
./mvnw spring-boot:run
```
Debe arrancar sin errores. Verificar en logs que MongoIndexConfig se ejecuta y crea los índices.

### 5.3 Probar el manejo de errores
Hacer un request inválido (ej: POST con body vacío) y verificar que la respuesta sigue teniendo formato `ErrorResponse`:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Datos inválidos",
  "path": "...",
  "details": [...]
}
```

### 5.4 Probar Swagger
Abrir `http://localhost:8080/swagger-ui.html` — debe seguir cargando.

### 5.5 Probar health
`GET http://localhost:8080/api/health` — debe responder.

---

## 6. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer shared kernel (ErrorResponse, GlobalExceptionHandler, configs)"
```

---

## 7. Lo que NO se hace en esta subfase

- **No** se modifican otros componentes que usan `ErrorResponse`. Como movimos solo el archivo, los imports se actualizan automáticamente.
- **No** se reorganiza el contenido de `MongoIndexConfig` aunque cree índices de varias colecciones — eso es trabajo de otra iteración.
- **No** se renombra el paquete raíz `com.example.demo` — esa decisión se posterga.

---

## Próximo paso

Continuar con **`02_componente_auth.md`**.
