# G0 — Plan General del Ciclo 3

**Contexto para IA continuadora · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> Este documento es el punto de entrada para una nueva sesión de IA que continuará el desarrollo del Ciclo 3 (C3). Lee todo antes de escribir una sola línea de código.

---

## 1. Stack tecnológico (no cambia en C3)

| Componente | Versión / Valor |
|-----------|----------------|
| Spring Boot | 4.0.x |
| Java | 17 |
| Build tool | Gradle |
| Paquete raíz | `com.example.demo` |
| Puerto | 8080 |
| MongoDB | 7.0 — Docker, db: `tramites_db`, user: `admin`, pass: `12345678`, puerto: 27017 |
| Auth | Spring Security + JWT (JJWT 0.12.6) |
| Swagger | springdoc-openapi 2.8.8 |
| App móvil | Flutter (solo cliente) |

---

## 2. Convenciones obligatorias

Estas reglas fueron establecidas en C1 y deben mantenerse en C3:

- `@PreAuthorize` **por método**, nunca por clase.
- Errores: `IllegalArgumentException` → HTTP 400; `IllegalStateException` → HTTP 500. Ambos capturados por `GlobalExceptionHandler` existente.
- Fechas: usar siempre `LocalDateTime` / `LocalDateTime.now()`. **Nunca `new Date()`.**
- Evitar `Optional.get()` directo — usar siempre `.orElseThrow(() -> new IllegalArgumentException(...))`.
- Controladores obtienen el usuario del JWT con `Authentication authentication` inyectado por Spring (no con `Principal`).
- No agregar `@PreAuthorize` a nivel de clase.
- No escribir comentarios que expliquen qué hace el código — solo los que explican POR QUÉ.

### Valores de estado en colecciones (case-sensitive)

| Colección | Campo | Valores válidos |
|-----------|-------|----------------|
| `tramites` | `estadoActual` | `Nuevo`, `En proceso`, `Derivado`, `Observado`, `Rechazado`, `Aprobado` |
| `seccion_expediente` | `estado` | `bloqueada`, `en_curso`, `completada` |
| `colaboracion_diagrama` | `estado` | `pendiente`, `aceptada`, `rechazada` |
| `trazabilidad` | `accion` | `crear`, `editar`, `derivar`, `aprobar`, `rechazar`, `observar` |
| `notificacion` | `estadoEnvio` | `pendiente`, `enviada`, `fallida` |
| `notificacion` | `canal` | `push`, `web`, `email` |
| `notificacion` | `tipo` | `cambio_estado`, `asignacion`, `sla_vencido`, `observacion` |

---

## 3. Estado del proyecto al entrar a C3

### 3.1. Lo que C1 implementó

| Guía | CUs | Qué hay implementado |
|------|-----|----------------------|
| G1-C1 | CU-01, CU-02 | Auth JWT + BCrypt, registro de cliente, login, `UsuarioRepository`, `JwtUtil`, `SecurityConfig` |
| G2-C1 | CU-04, CU-05, CU-06 | CRUD departamentos, actividades, políticas de negocio |
| G3-C1 | CU-03 | Roles + Permisos, Swagger/OpenAPI, endpoint `/api/health` |
| G4-C1 | (motor base) | `WorkflowEngineService` con lógica fork/join/decision/loop |
| G5-C1 | CU-12, CU-13, CU-14 | CRUD diagramas, nodos, transiciones; mock de IA para diagrama desde prompt |

### 3.2. Lo que C2 implementó

| Guía | CUs | Qué hay implementado |
|------|-----|----------------------|
| G1-C2 | CU-07, CU-08, CU-09 | `POST /api/tramites/iniciar`, motor de workflow completo, `GET /api/tramites/mis-pendientes`, `POST /api/tramites/{id}/completar-nodo` |
| G2-C2 | CU-10, CU-16 | Expediente digital, secciones, campos, adjuntos; `POST /api/expedientes/{id}/secciones/{nodoId}/guardar`, completar sección |
| G3-C2 | CU-11, CU-17, CU-18 | Derivar trámite, devolver a corregir, aprobar/rechazar; `TramiteDecisionService`, `TrazabilidadRepository` |
| G4-C2 | CU-15 | Colaboración en diagramas, invitaciones, notificaciones básicas; `ColaboracionService` |

### 3.3. Colecciones MongoDB ya existentes (las 29 del schema)

```
usuarios, roles, permisos, departamentos, actividades, politicas_negocio,
version_politica, diagrama_workflow, version_diagrama, nodo_diagrama,
flujo_transicion, formulario_plantilla, campo_plantilla, colaboracion_diagrama,
tramites, estado_actual, estado_historico, expediente_digital, seccion_expediente,
campo_seccion, adjunto, transcripcion_voz, notificacion, canal_envio,
trazabilidad, metrica_tiempo, cuello_botella, reporte, log_agente
```

Las colecciones `metrica_tiempo`, `cuello_botella`, `reporte`, `log_agente` y `transcripcion_voz` aún no tienen implementación Java — serán creadas en C3.

---

## 4. Casos de uso del Ciclo 3

### Tabla resumen

| CU | Nombre | Actor principal | Guía sugerida |
|----|--------|----------------|---------------|
| CU-19 | Cancelar trámite | Cliente | G1-C3 |
| CU-20 | Actualizar estado del trámite | Sistema (Motor) | G1-C3 |
| CU-21 | Consultar estado del trámite | Cliente | G1-C3 |
| CU-22 | Controlar ejecución del workflow | Sistema (Motor) | G1-C3 |
| CU-23 | Registrar trazabilidad del trámite | Sistema | G2-C3 |
| CU-24 | Medir tiempos de atención | Sistema | G2-C3 |
| CU-25 | Detectar cuellos de botella | Sistema (Motor IA) | G2-C3 |
| CU-26 | Generar reportes de proceso | Administrador | G3-C3 |
| CU-29 | Ver historial de trámites | Administrador | G3-C3 |
| CU-27 | Enviar notificaciones del sistema | Sistema | G4-C3 |
| CU-28 | Recibir notificaciones | Cliente, Sistema | G4-C3 |
| CU-30 | Completar formulario por voz | Funcionario | G5-C3 |
| CU-31 | Interactuar con el agente de asistencia | Cliente, Funcionario, Admin | G5-C3 |

### Descripción detallada por CU

**CU-19 — Cancelar trámite (Cliente)**
- El cliente selecciona un trámite activo y confirma la cancelación.
- El sistema detiene el motor de workflow, elimina el trámite de la bandeja del funcionario y actualiza estado a `"Cancelado por el usuario"`.
- Restricción: si el trámite está en etapa de aprobación final (política lo prohíbe), se rechaza con error 400.
- Post: estado permanentemente inactivo; notificación al funcionario.
- Endpoint: `POST /api/tramites/{id}/cancelar`

**CU-20 — Actualizar estado del trámite (Sistema)**
- Se ejecuta automáticamente al finalizar una actividad en el motor.
- Actualiza `estadoActual` en `tramites`, contadores de tiempo e inserta en `estado_historico`.
- En deadlock: 3 reintentos; si estado inválido: revierte y notifica al admin.
- No hay endpoint propio — es invocado internamente por `WorkflowEngineService`.

**CU-21 — Consultar estado del trámite (Cliente)**
- El cliente accede a "Mis Trámites", selecciona uno.
- El sistema devuelve una línea de tiempo con fechas, departamentos, estados y la etapa actual.
- Operación de solo lectura.
- Endpoint: `GET /api/tramites/{id}/linea-tiempo`

**CU-22 — Controlar ejecución del workflow (Sistema)**
- Orquesta If-Else, Fork, Join, Loop en el grafo de actividad.
- Valida que las transiciones no violen restricciones de integridad.
- En condición ambigua: pausa y genera `IllegalStateException("Excepción de Regla de Negocio")`.
- Ya existe una versión base en `WorkflowEngineService` (G4-C1/G1-C2); en C3 se robustece y se integra con CU-20/23.

**CU-23 — Registrar trazabilidad del trámite (Sistema)**
- Registro automático e inalterable de cada evento.
- Genera hash SHA-256: `hashActual = SHA256(tramiteId + accion + timestamp + hashAnterior)`.
- Si falla la escritura en la BD de auditoría: bloquea la transacción principal (sin rastro = sin acción).
- No se puede modificar ni borrar estos registros.
- Ya existe `TrazabilidadRepository`; en C3 se añade la cadena de hashes.

**CU-24 — Medir tiempos de atención (Sistema)**
- Al completar una actividad: calcula diferencia `fechaFin - fechaInicio` (excluyendo no laborales).
- Categoriza por trámite, actividad, departamento y funcionario.
- Compara contra el SLA definido en la política.
- Persiste en colección `metrica_tiempo`.
- Si falta registro de inicio: `superoSla = false` y marca como `"No calculable"`.
- Endpoint de consulta: `GET /api/metricas/tramite/{tramiteId}`

**CU-25 — Detectar cuellos de botella (Sistema/IA)**
- Análisis periódico (scheduled) comparando tiempos reales vs SLA.
- Identifica acumulación de trámites o desviación estadística.
- Genera alertas en colección `cuello_botella` y las muestra en el tablero del Administrador.
- Si no hay historial suficiente: pospone el análisis.
- Endpoint: `GET /api/metricas/cuellos-botella`

**CU-26 — Generar reportes de proceso (Administrador)**
- Filtros: rango de fechas, departamento, tipo de trámite.
- Formatos: PDF, Excel, CSV.
- Vista previa antes de exportar.
- En consultas masivas: procesamiento en background con notificación.
- Endpoints: `POST /api/reportes/generar`, `GET /api/reportes/{id}/descargar`

**CU-27 — Enviar notificaciones del sistema (Sistema)**
- Recupera plantilla, inyecta datos reales (nombres, IDs).
- Canales: Push (app Flutter via FCM), Email (Spring Mail), Web (interna).
- Si proveedor falla: encola en `notificacion` con `estadoEnvio = "pendiente"` y reintenta.
- Si faltan datos para la plantilla: envía versión genérica.

**CU-28 — Recibir notificaciones (Cliente)**
- Al detectar cambio de estado, identifica al propietario y envía notificación.
- Canales: Push (Flutter), Email, aviso interno.
- Endpoints: `GET /api/notificaciones/mis-notificaciones`, `PUT /api/notificaciones/{id}/marcar-leida`

**CU-29 — Ver historial de trámites (Administrador)**
- Tabla filtrable con ID, estado, cronología, participantes, departamentos.
- Puede exportar el historial visualizado.
- Endpoint: `GET /api/tramites/historial` (con query params: `?estado=&departamentoId=&desde=&hasta=`)

**CU-30 — Completar formulario por voz (Funcionario)**
- El funcionario activa dictado en el formulario de sección activa.
- Spring llama a microservicio FastAPI externo que transcribe audio → texto.
- Si el microservicio cae: permite escritura manual.
- Endpoint de transcripción: `POST /api/expedientes/secciones/{seccionId}/transcribir-voz`
- Persiste resultado en colección `transcripcion_voz`.

**CU-31 — Interactuar con el agente de asistencia (todos)**
- Panel flotante en la app. El agente detecta el módulo y rol del usuario.
- Spring llama a microservicio FastAPI (RAG sobre base de conocimiento).
- Si el microservicio falla: devuelve enlace al manual de usuario.
- Registra interacciones en `log_agente`.
- Endpoint: `POST /api/agente/consultar`

---

## 5. Plan de guías para C3

### G1-C3 — Ciclo de vida completo del trámite
**CUs:** CU-19, CU-20, CU-21, CU-22

Implementa:
- `POST /api/tramites/{id}/cancelar` — cancelación con validación de política
- `GET /api/tramites/{id}/linea-tiempo` — DTO con lista de hitos cronológicos
- Mejora de `WorkflowEngineService`: integrar `CU-20` (actualización automática de estado tras cada transición) y robustecer `CU-22` (manejo de excepciones de regla de negocio)

Nuevos modelos Java: ninguno adicional (usa `Tramite`, `EstadoHistorico`, `Trazabilidad` ya existentes).

Nuevo DTO: `LineaTiempoResponse` (lista de `HitoDTO` con `fecha`, `estado`, `departamento`, `actor`, `esActual`).

### G2-C3 — Trazabilidad con integridad + métricas + cuellos de botella
**CUs:** CU-23, CU-24, CU-25

Implementa:
- Cadena de hashes SHA-256 en `TrazabilidadService` — cada registro referencia el hash del anterior
- `MetricaTiempoService`: cálculo al completar nodo, persistencia en `metrica_tiempo`
- `@Scheduled` task para detección de cuellos de botella → `cuello_botella`
- `GET /api/metricas/tramite/{tramiteId}`
- `GET /api/metricas/cuellos-botella`

Nuevos modelos Java: `MetricaTiempo`, `CuelloBotella`.

Dependencia nueva en `build.gradle`: ninguna (SHA-256 vía `java.security.MessageDigest`; `@Scheduled` vía `@EnableScheduling` en main class).

### G3-C3 — Reportes y historial
**CUs:** CU-26, CU-29

Implementa:
- `GET /api/tramites/historial` con paginación y filtros (Spring Data `Pageable`)
- `POST /api/reportes/generar` + `GET /api/reportes/{id}/descargar`
- Exportación CSV en memoria (sin dependencia extra); PDF/Excel con Apache POI / iText

Nuevos modelos Java: `Reporte`.

Dependencia nueva en `build.gradle`:
```groovy
implementation 'org.apache.poi:poi-ooxml:5.2.5'   // Excel
implementation 'com.itextpdf:itext7-core:7.2.5'    // PDF
```

### G4-C3 — Sistema de notificaciones
**CUs:** CU-27, CU-28

Implementa:
- `NotificacionService`: envío real vía FCM (push) y Spring Mail (email)
- Cola de reintentos con `@Scheduled`: procesa `notificacion` donde `estadoEnvio = "pendiente"`
- `GET /api/notificaciones/mis-notificaciones`
- `PUT /api/notificaciones/{id}/marcar-leida`

Dependencias nuevas en `build.gradle`:
```groovy
implementation 'com.google.firebase:firebase-admin:9.3.0'   // FCM push
implementation 'org.springframework.boot:spring-boot-starter-mail' // Email
```

### G5-C3 — Microservicios externos: voz + agente IA
**CUs:** CU-30, CU-31

Implementa:
- `VozService`: llama al microservicio FastAPI de Speech-to-Text via `RestTemplate` o `WebClient`; persiste en `transcripcion_voz`
- `AgenteService`: llama al microservicio FastAPI RAG; persiste interacción en `log_agente`
- `POST /api/expedientes/secciones/{seccionId}/transcribir-voz`
- `POST /api/agente/consultar`

Dependencia nueva (si se usa WebClient reactivo):
```groovy
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

Los microservicios FastAPI son externos y corren en puertos separados (ej. 8001 para voz, 8002 para agente). Spring solo los invoca via HTTP; su implementación en Python es fuera de scope de estas guías pero se documenta el contrato de la API.

---

## 6. Modelos Java nuevos para C3

### 6.1. `MetricaTiempo.java` (colección `metrica_tiempo`)

Campos del schema Mermaid:
```
_id, tramite_id, actividad_id, departamento_id, tiempo_segundos,
supero_sla, fecha_inicio_actividad
```

### 6.2. `CuelloBotella.java` (colección `cuello_botella`)

Leer `guias/base mermaid.md` sección `CUELLO_BOTELLA` para los campos exactos.

### 6.3. `Reporte.java` (colección `reporte`)

Leer `guias/base mermaid.md` sección `REPORTE` para los campos exactos.

### 6.4. `TranscripcionVoz.java` (colección `transcripcion_voz`)

Campos del schema Mermaid:
```
_id, seccion_id, funcionario_id, texto_transcrito, duracion_segundos,
confianza_transcripcion, fecha_transcripcion
```

### 6.5. `LogAgente.java` (colección `log_agente`)

Leer `guias/base mermaid.md` sección `LOG_AGENTE` para los campos exactos.

> **Regla crítica:** Antes de escribir cualquier setter o getter, leer la sección correspondiente en `guias/base mermaid.md` y mapear `snake_case` → `camelCase`. Los bugs más comunes en C2 fueron exactamente setters con nombres inventados.

---

## 7. Endpoints completos al finalizar C3

Al terminar C3, el sistema expondrá todos estos endpoints (adicionales a los de C1 y C2):

```
# Ciclo de vida del trámite
POST   /api/tramites/{id}/cancelar
GET    /api/tramites/{id}/linea-tiempo
GET    /api/tramites/historial

# Métricas y monitoreo
GET    /api/metricas/tramite/{tramiteId}
GET    /api/metricas/cuellos-botella

# Reportes
POST   /api/reportes/generar
GET    /api/reportes/{id}/descargar

# Notificaciones
GET    /api/notificaciones/mis-notificaciones
PUT    /api/notificaciones/{id}/marcar-leida

# Voz y agente
POST   /api/expedientes/secciones/{seccionId}/transcribir-voz
POST   /api/agente/consultar
```

---

## 8. Dependencias nuevas en `build.gradle`

Agregar solo cuando se llegue a la guía que las necesita:

```groovy
// G3-C3: Exportación
implementation 'org.apache.poi:poi-ooxml:5.2.5'
implementation 'com.itextpdf:itext7-core:7.2.5'

// G4-C3: Notificaciones
implementation 'com.google.firebase:firebase-admin:9.3.0'
implementation 'org.springframework.boot:spring-boot-starter-mail'

// G5-C3: WebClient para llamadas a FastAPI (opcional si ya hay RestTemplate)
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

---

## 9. Credenciales de prueba (heredadas de C1)

```
Admin:       admin@tramites.com   / Admin1234!
Funcionario: func@tramites.com    / Func1234!
Cliente:     cliente@tramites.com / Cliente1234!
```

MongoDB Compass / Mongo Express: `mongodb://admin:12345678@localhost:27017/tramites_db`

---

## 10. Instrucciones para la IA que continúa

1. **Lee siempre `guias/base mermaid.md`** antes de escribir cualquier modelo o repositorio en C3. Es la fuente de verdad de los campos.
2. **Lee la guía de la funcionalidad que vas a extender**: si tocas `WorkflowEngineService`, lee `guias/C2/G1-Tramite-Motor-Workflow.md`; si tocas `Trazabilidad`, lee `guias/C2/G3-Decisiones-Flujo.md`.
3. **No inventes nombres de métodos de repositorio.** Spring Data deriva el método del nombre del campo Java (camelCase). Si el campo Java es `fechaCambio`, el método es `findByTramiteIdOrderByFechaCambioAsc`.
4. **El `@Scheduled` requiere `@EnableScheduling`** en la clase principal `DemoApplication.java`. Verifica si ya está antes de agregarlo.
5. **Para los microservicios externos (CU-30, CU-31):** Spring llama a FastAPI via HTTP. Usar `RestTemplate` (ya disponible) o `WebClient`. Los endpoints FastAPI son configurables via `application.properties` (ej. `app.voz.url=http://localhost:8001`).
6. **Orden sugerido de implementación:** G1-C3 → G2-C3 → G4-C3 → G3-C3 → G5-C3. G4 antes de G3 porque los reportes pueden disparar notificaciones.
7. **Exportación en G3-C3:** Para CSV, no necesitas dependencia externa — usa `StringBuilder` o `PrintWriter`. Para Excel/PDF sí necesitas POI/iText.
8. **Hashing en G2-C3 (CU-23):** `java.security.MessageDigest.getInstance("SHA-256")` no requiere dependencia nueva.
