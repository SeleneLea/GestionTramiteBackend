# G0 — Plan General del Ciclo 2

**Sistema de Gestión de Trámites · Documento de contexto para continuar el desarrollo**

> Este documento está pensado para que cualquier desarrollador o IA que tome el proyecto pueda entender el estado actual, los objetivos del Ciclo 2 y el plan exacto de cada guía sin necesidad de leer toda la historia previa.

---

## 1. Descripción del sistema

Sistema web + móvil de **gestión de trámites de principio a fin**, similar a CRE (Comisión de Regulación de Energía). Un cliente inicia un trámite, el sistema lo enruta automáticamente por departamentos (Atención al cliente → Área técnica → Legal → Operaciones) hasta su resolución. El comportamiento es un **motor de workflow** que interpreta diagramas de actividad UML 2.5 con swimlanes almacenados en MongoDB.

**Stack técnico:**
- Backend: Spring Boot 4.0.x + Java 17 + Gradle
- Base de datos: MongoDB 7.0 (Docker)
- Seguridad: Spring Security + JWT (JJWT 0.12.6)
- Documentación: Swagger/OpenAPI (springdoc 2.8.8)
- Paquete base: `com.example.demo`
- Puerto: `8080`
- MongoDB: `localhost:27017`, base `tramites_db`, usuario `admin`, contraseña `12345678`

**Canales de acceso:**
- App móvil Flutter → clientes (seguimiento + notificaciones push)
- Plataforma web Angular → funcionarios y administradores

---

## 2. Estado al inicio del Ciclo 2 — Todo lo implementado en Ciclo 1

### 2.1. Casos de uso completados en Ciclo 1

| CU | Nombre | Guía C1 |
|----|--------|---------|
| CU-01 | Iniciar sesión y autenticar usuario | G1 |
| CU-02 | Gestionar usuarios | G1 |
| CU-03 | Configurar roles y permisos | G3 + G5 |
| CU-04 | Gestionar departamentos | G2 |
| CU-05 | Gestionar actividades | G2 |
| CU-06 | Gestionar políticas de negocio | G2 |
| CU-12 | Diseñar flujo de trabajo (workflow) | G5 |
| CU-13 | Crear flujo mediante diagramas | G5 |
| CU-14 | Crear flujo mediante prompts (IA mock) | G5 |

### 2.2. Endpoints REST implementados en C1

```
POST   /api/auth/register-cliente     Registro público solo para clientes
POST   /api/auth/login                Login (devuelve JWT con rol)

GET    /api/usuarios                  Listar usuarios (ADMIN)
POST   /api/usuarios/crear            Crear funcionario/admin (ADMIN)
GET    /api/usuarios/me               Perfil del usuario autenticado (todos)
GET    /api/usuarios/{id}             Buscar por ID (ADMIN)
PUT    /api/usuarios/{id}             Actualizar (ADMIN)
DELETE /api/usuarios/{id}             Desactivar (ADMIN)

GET    /api/roles                     Listar roles (ADMIN)
POST   /api/roles                     Crear rol (ADMIN)
PUT    /api/roles/{id}                Actualizar rol (ADMIN)
PATCH  /api/roles/{id}/permisos       Asignar permisos a rol (ADMIN)
DELETE /api/roles/{id}                Eliminar rol no-sistema (ADMIN)
GET    /api/permisos                  Catálogo de permisos (ADMIN)

GET    /api/departamentos             Listar departamentos (autenticado)
POST   /api/departamentos             Crear (ADMIN)
PUT    /api/departamentos/{id}        Actualizar (ADMIN)
PATCH  /api/departamentos/{id}/estado Activar/desactivar (ADMIN)

GET    /api/actividades               Listar (autenticado)
POST   /api/actividades               Crear (ADMIN)
PUT    /api/actividades/{id}          Actualizar (ADMIN)

GET    /api/politicas                 Listar (autenticado)
POST   /api/politicas                 Crear (ADMIN)
PUT    /api/politicas/{id}            Actualizar (ADMIN)
PATCH  /api/politicas/{id}/estado     Activar/archivar (ADMIN)

GET    /api/diagramas                 Listar diagramas (autenticado)
POST   /api/diagramas                 Crear diagrama (ADMIN)
GET    /api/diagramas/{id}            Buscar (autenticado)
PUT    /api/diagramas/{id}            Actualizar (ADMIN)
PATCH  /api/diagramas/{id}/estado     Publicar/archivar (ADMIN) — valida inicio+fin+salidas
DELETE /api/diagramas/{id}            Eliminar borrador en cascada (ADMIN)

GET    /api/diagramas/{id}/nodos      Listar nodos (autenticado)
POST   /api/diagramas/{id}/nodos      Agregar nodo (ADMIN)
GET    /api/nodos/{id}                Buscar nodo (autenticado)
PUT    /api/nodos/{id}                Actualizar nodo (ADMIN)
DELETE /api/nodos/{id}                Eliminar nodo (ADMIN)

GET    /api/diagramas/{id}/transiciones   Listar transiciones (autenticado)
POST   /api/diagramas/{id}/transiciones   Agregar transición (ADMIN)
GET    /api/transiciones/{id}             Buscar (autenticado)
DELETE /api/transiciones/{id}             Eliminar (ADMIN)

POST   /api/workflow-design/from-prompt   Generar diagrama desde prompt IA mock (ADMIN)

GET    /api/health                    Health check público
GET    /swagger-ui.html               Documentación Swagger
```

### 2.3. Modelos MongoDB existentes (29 colecciones)

```
usuarios, roles, permisos, departamentos, actividades,
politicas_negocio, versiones_politica,
diagramas_workflow, versiones_diagrama,
nodos_diagrama, flujos_transicion,
colaboraciones_diagrama, formularios_plantilla, campos_plantilla,
tramites, estados_actuales, estados_historicos,
expedientes, secciones_expediente, campos_seccion,
adjuntos, transcripciones_voz,
notificaciones, canales_envio,
trazabilidad, metricas_tiempo,
cuellos_botella, reportes, logs_agente
```

### 2.4. Datos de prueba en MongoDB (seed)

```
Roles: Cliente, Funcionario, Administrador, SuperUser
Permisos: SOLICITAR_TRAMITE, EJECUTAR_TRAMITE, CREAR_FLUJO, GESTIONAR_USUARIOS, VER_REPORTES, etc.
Departamentos: Atención al Cliente (ATC), Área Técnica (TEC), Área Legal (LEG), Operaciones (OPE)
Admin de prueba: admin@cre.bo / admin12345
```

### 2.5. Estructura de carpetas del proyecto

```
src/main/java/com/example/demo/
├── config/
│   ├── MongoIndexConfig.java
│   ├── OpenApiConfig.java
│   ├── SecurityConfig.java
│   └── GlobalExceptionHandler.java
├── controllers/
│   ├── AuthController.java
│   ├── UsuarioController.java
│   ├── RolController.java
│   ├── PermisoController.java
│   ├── DepartamentoController.java
│   ├── ActividadController.java
│   ├── PoliticaNegocioController.java
│   ├── DiagramaWorkflowController.java
│   ├── NodoDiagramaController.java
│   ├── FlujoTransicionController.java
│   └── PromptFlowController.java
├── dto/
│   ├── LoginRequest.java
│   ├── RegisterClienteRequest.java
│   ├── CrearUsuarioAdminRequest.java
│   ├── AuthResponse.java
│   ├── ErrorResponse.java
│   ├── RolRequest.java
│   ├── AsignarPermisosRequest.java
│   ├── DiagramaWorkflowRequest.java
│   ├── DiagramaEstadoRequest.java
│   ├── NodoDiagramaRequest.java
│   ├── FlujoTransicionRequest.java
│   └── PromptFlujoRequest.java
├── models/
│   └── [29 modelos de MongoDB]
├── repositories/
│   └── [18+ repositorios MongoRepository]
├── security/
│   ├── JwtUtils.java
│   └── JwtAuthFilter.java
└── services/
    ├── AuthService.java
    ├── UsuarioService.java
    ├── RolService.java
    ├── DiagramaWorkflowService.java
    ├── NodoDiagramaService.java
    ├── FlujoTransicionService.java
    └── PromptFlowService.java
```

---

## 3. Casos de uso del Ciclo 2

Los CUs del Ciclo 2 según el documento oficial del examen son:

| CU | Nombre | Actor principal |
|----|--------|-----------------|
| **CU-07** | Registrar solicitud de trámite | Cliente |
| **CU-08** | Asignar siguiente actividad | Sistema (Motor de Workflow) |
| **CU-09** | Recibir trámite asignado | Sistema → Funcionario |
| **CU-10** | Revisar información del trámite | Funcionario |
| **CU-11** | Derivar trámite | Funcionario |
| **CU-15** | Solicitar Colaboración en diagrama | Administrador |
| **CU-16** | Registrar informe de actividad | Funcionario |
| **CU-17** | Devolver trámite a corregir | Funcionario |
| **CU-18** | Aprobar o rechazar trámite | Funcionario |

---

## 4. Plan de guías del Ciclo 2

### G1-C2 ✅ COMPLETADA — Motor de Workflow

**Archivo:** `C2/G1-Tramite-Motor-Workflow.md`

**CUs:** CU-07, CU-08, CU-09

**Lo que implementa:**

| Clase/Archivo | Descripción |
|---------------|-------------|
| `Tramite.java` | Actualizar modelo con `nodosParalellosActivos` + `estaEnParalelo()` |
| `IniciarTramiteRequest.java` | DTO para CU-07 |
| `CompletarNodoRequest.java` | DTO para CU-08 |
| `EstadoTramiteResponse.java` | DTO para CU-09 |
| `EstadoHistoricoRepository.java` | Nuevo repositorio |
| `TramiteRepository.java` | Agregar `findTramitesActivos()`, `findByNodoActualIdIn()` |
| `WorkflowEngineService.java` | Servicio núcleo del sistema — lee el diagrama y ejecuta el flujo |
| `WorkflowController.java` | Controller en `/api/tramites/**` |

**Endpoints nuevos:**
```
POST /api/tramites/iniciar            CU-07: cliente inicia trámite
POST /api/tramites/{id}/completar-nodo CU-08: funcionario completa nodo, motor avanza
GET  /api/tramites/mis-pendientes     CU-09: bandeja del funcionario
GET  /api/tramites/{id}/estado        Estado actual + nodo activo
GET  /api/tramites                    Listar todos (ADMIN)
GET  /api/tramites/{id}/historial     Historial básico
```

**Comportamiento clave del motor (CU-08):**
- `inicio` → salta automáticamente al primer nodo actividad
- `actividad` → se detiene, espera al funcionario
- `decision` → evalúa `"si"`/`"no"` del request y toma la transición correcta
- `fork` → activa todas las ramas en paralelo, guarda en `nodosParalellosActivos`
- `join` → espera a que todas las ramas del fork terminen, luego avanza
- `fin` → cierra el trámite con `estadoActual = "Aprobado"`

---

### G2-C2 🔲 PENDIENTE — Expediente Digital

**Archivo a crear:** `C2/G2-Expediente-Digital.md`

**CUs:** CU-10, CU-16

**Descripción de los CUs:**

**CU-10 — Revisar información del trámite (Funcionario)**
- El funcionario selecciona un trámite de su bandeja
- Ve el expediente completo organizado por secciones (una por departamento)
- Puede leer todas las secciones anteriores (contexto completo)
- Solo puede **editar** su sección activa (`estado: en_curso`)
- Las secciones de otros departamentos están bloqueadas (solo lectura)
- Puede descargar/previsualizar adjuntos

**CU-16 — Registrar informe de actividad (Funcionario)**
- El funcionario llena los campos de **su sección activa** del expediente
- Campos dinámicos según la plantilla del nodo (`FormularioPlantilla` + `CampoPlantilla`)
- Puede adjuntar documentos e imágenes
- Puede dictar por voz (transcripción IA — ver nota abajo)
- Al guardar: la sección pasa a `estado: completada` y el motor avanza automáticamente (llama a `WorkflowEngineService.completarNodo`)

**Lo que debe implementar G2-C2:**

| Clase | Descripción |
|-------|-------------|
| `ExpedienteController.java` | Endpoints del expediente |
| `ExpedienteService.java` | Lógica de acceso por rol y estado de sección |
| `SeccionExpedienteService.java` | Leer/editar sección activa, bloqueo de secciones ajenas |
| `CampoSeccionService.java` | Guardar campos dinámicos de la sección |
| `AdjuntoService.java` | Subir y recuperar adjuntos (almacenamiento local o S3 en demo) |
| DTOs necesarios | `ExpedienteResponse`, `SeccionRequest`, `CampoSeccionRequest`, `AdjuntoUploadRequest` |

**Endpoints a crear:**
```
GET  /api/expedientes/{tramiteId}            CU-10: ver expediente completo del trámite
GET  /api/expedientes/{tramiteId}/seccion/{nodoId}  Ver sección específica
PUT  /api/expedientes/{tramiteId}/seccion/{nodoId}  CU-16: editar sección activa
POST /api/expedientes/{tramiteId}/seccion/{nodoId}/adjuntos  Subir adjunto
GET  /api/expedientes/{tramiteId}/seccion/{nodoId}/adjuntos  Listar adjuntos
```

**Reglas de negocio clave:**
- Un funcionario solo puede editar su sección si `estado = "en_curso"` y el nodo pertenece a su departamento
- Todas las secciones son visibles para cualquier actor del trámite (solo lectura para las no activas)
- Al guardar la sección, internamente se llama a `WorkflowEngineService.completarNodo()` para que el motor avance
- Los campos se guardan en la colección `campos_seccion` referenciados por `seccionId` + `campoPlantillaId`

**Nota sobre voz (CU-30 — Ciclo 3):** la funcionalidad de "voz a sección" es CU-30 del Ciclo 3. En G2-C2 se puede dejar un stub/mock del endpoint `POST /api/ia/voz-a-seccion` que devuelva texto hardcodeado. La integración real con FastAPI es del Ciclo 3.

---

### G3-C2 🔲 PENDIENTE — Decisiones del Flujo del Trámite

**Archivo a crear:** `C2/G3-Decisiones-Flujo.md`

**CUs:** CU-11, CU-17, CU-18

**Descripción de los CUs:**

**CU-11 — Derivar trámite (Funcionario)**
- El funcionario reasigna el trámite a otro funcionario **del mismo departamento** (o con permisos)
- Requiere motivo obligatorio
- El trámite no cambia de nodo — solo cambia el `funcionarioActualId`
- Actualiza trazabilidad con quién derivó, a quién y el motivo

**CU-17 — Devolver trámite a corregir (Funcionario)**
- El funcionario detecta un error y devuelve el trámite a un nodo anterior
- La política debe permitir flujos iterativos en esa etapa
- El funcionario selecciona el nodo destino de la lista de nodos anteriores válidos
- Debe escribir las observaciones (campo obligatorio)
- El motor retrocede el trámite: `estado = "Observado"`, desbloquea la sección del nodo destino
- Equivale a tomar la rama `"no"` de un decision node (pero iniciada manualmente)

**CU-18 — Aprobar o rechazar trámite (Funcionario)**
- El funcionario emite el veredicto formal tras completar su informe (CU-16)
- Opciones según política: `"Aprobar"`, `"Rechazar"`, `"Aprobado con observaciones"`
- Requiere justificación escrita obligatoria
- Al aprobar: motor avanza al siguiente nodo (`decision = "si"`)
- Al rechazar: motor toma la rama de rechazo (`decision = "no"`) o cierra con `estado = "Rechazado"`
- Dispara notificaciones al cliente (base para CU-27/CU-28 del Ciclo 3)

**Lo que debe implementar G3-C2:**

| Clase | Descripción |
|-------|-------------|
| `TramiteDecisionController.java` | Endpoints de decisión |
| `TramiteDecisionService.java` | Lógica de derivar, devolver, aprobar/rechazar |
| DTOs necesarios | `DerivarTramiteRequest`, `DevolverTramiteRequest`, `DecisionFinalRequest` |

**Endpoints a crear:**
```
POST /api/tramites/{id}/derivar          CU-11: reasignar a otro funcionario
POST /api/tramites/{id}/devolver         CU-17: devolver a nodo anterior con observaciones
POST /api/tramites/{id}/decision-final   CU-18: aprobar o rechazar formalmente
GET  /api/tramites/{id}/nodos-anteriores Lista de nodos a los que se puede devolver
```

**Reglas de negocio clave:**
- `derivar` solo cambia `funcionarioActualId`, no el nodo
- `devolver` retrocede el motor: llama internamente a una variante de `completarNodo` con `decision = "no"` o busca la transición iterativa del nodo activo
- `decision-final` es la acción que dispara el avance del motor (equivale a `completarNodo` con `decision = "si"/"no"` pero más explícita para el examen)
- Toda acción registra un `EstadoHistorico` y una entrada en `Trazabilidad`

---

### G4-C2 🔲 PENDIENTE — Colaboración Online en Diagramas

**Archivo a crear:** `C2/G4-Colaboracion-Diagramas.md`

**CU:** CU-15

**Descripción del CU:**

**CU-15 — Solicitar Colaboración (Administrador)**
- El admin tiene un diagrama abierto en el editor visual
- Selecciona "Invitar colaborador" y elige otros administradores o funcionarios del sistema
- El sistema envía una notificación (interna en la plataforma web) con enlace al diagrama
- El invitado acepta y puede editar el diagrama en tiempo real (colaboración tipo Figma/Google Docs)
- El sistema registra quién modificó qué nodo y cuándo (historial de versiones del diagrama)
- Restricción: no se puede invitar a alguien que ya tiene 4 diagramas en edición activa simultánea

**Lo que debe implementar G4-C2:**

| Clase | Descripción |
|-------|-------------|
| `ColaboracionDiagramaController.java` | Endpoints de colaboración |
| `ColaboracionDiagramaService.java` | Lógica de invitación, aceptación, restricción de carga |
| `VersionDiagramaService.java` | Historial de cambios por nodo |
| WebSocket (opcional para demo) | Tiempo real — si no hay tiempo, simular con polling |
| DTOs | `InvitarColaboradorRequest`, `RespuestaColaboracionRequest` |

**Endpoints a crear:**
```
POST /api/diagramas/{id}/colaboradores         CU-15: invitar colaborador
GET  /api/diagramas/{id}/colaboradores         Ver colaboradores activos
PATCH /api/diagramas/{id}/colaboradores/{userId}/respuesta  Aceptar/rechazar invitación
DELETE /api/diagramas/{id}/colaboradores/{userId}           Revocar acceso
GET  /api/diagramas/{id}/versiones             Historial de versiones del diagrama
```

**Reglas de negocio clave:**
- Máximo 4 diagramas en edición activa por usuario (verificar contando `ColaboracionDiagrama` con `estado = "aceptada"` del invitado)
- El `rol_colaboracion` puede ser `editor` o `visualizador`
- Registrar en `VersionDiagrama` cada cambio: quién, qué nodo, cuándo
- Para la demo: la colaboración en tiempo real puede simularse sin WebSocket (polling cada 3s desde el frontend) — documentar esta simplificación

---

## 5. Relación entre CUs del Ciclo 2 — Flujo completo

```
Cliente inicia trámite
        │
        ▼
CU-07: POST /api/tramites/iniciar
        │  Motor (CU-08) avanza al primer nodo
        ▼
CU-09: Funcionario ATC ve trámite en su bandeja (GET /mis-pendientes)
        │
        ▼
CU-10: Funcionario ATC revisa el expediente (GET /expedientes/{id})
        │
        ▼
CU-16: Funcionario ATC llena su sección (PUT /expedientes/{id}/seccion/{nodoId})
        │  Motor (CU-08) avanza automáticamente
        ▼
CU-18: Aprobar → motor continúa al siguiente departamento
CU-17: Devolver → motor retrocede al nodo indicado
CU-11: Derivar → cambia el funcionario responsable (mismo nodo)
        │
        ▼
[Siguiente departamento... hasta llegar a FIN]
        │
        ▼
CU-08: Motor cierra trámite → estadoActual = "Aprobado" / "Rechazado"
```

CU-15 es independiente: el admin puede invitar colaboradores a un diagrama en cualquier momento, no está en el flujo del trámite.

---

## 6. Convenciones de código del proyecto

| Convención | Detalle |
|-----------|---------|
| Paquete base | `com.example.demo` |
| Naming servicios | `NombreService.java` en `/services/` |
| Naming controllers | `NombreController.java` en `/controllers/` |
| Naming DTOs | `AccionEntidadRequest.java` / `EntidadResponse.java` en `/dto/` |
| Naming modelos | `NombreEnPascalCase.java` en `/models/` |
| Colección MongoDB | `@Document(collection = "nombre_en_snake_case")` |
| Seguridad por método | `@PreAuthorize("hasRole('ADMINISTRADOR')")` — nunca en clase |
| Errores | Lanzar `IllegalArgumentException` para 400, `IllegalStateException` para 500 — los captura `GlobalExceptionHandler` |
| Respuestas | Siempre `ResponseEntity<T>` — no usar `@ResponseBody` directamente |
| Trazabilidad | Toda acción relevante debe crear un registro en `EstadoHistorico` |
| IDs | MongoDB usa `String` como tipo de ID (ObjectId serializado) |
| Fechas | `LocalDateTime` — nunca `Date` |

---

## 7. Datos de acceso para pruebas

```
# Levantar infraestructura
docker-compose up -d

# MongoDB: localhost:27017
# Mongo Express: http://localhost:8081
# Spring Boot: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html

# Credenciales de prueba
Admin:      admin@cre.bo       / admin12345
Cliente:    (registrar vía POST /api/auth/register-cliente)
Funcionario:(crear vía POST /api/usuarios/crear con token admin)

# Para obtener token:
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cre.bo","password":"admin12345"}'
```

---

## 8. Lo que NO hace el Ciclo 2 (queda para Ciclo 3)

| Funcionalidad | CU | Motivo |
|---------------|----|--------|
| Cancelar trámite (cliente) | CU-19 | Ciclo 3 |
| Actualizar estado del trámite | CU-20 | Ciclo 3 — automatiza estados en tiempo real |
| Consultar estado del trámite (app móvil) | CU-21 | Ciclo 3 — Flutter |
| Controlar ejecución del workflow (avanzado) | CU-22 | Ciclo 3 |
| Trazabilidad completa con hash SHA-256 | CU-23 | Ciclo 3 |
| Métricas de tiempo por actividad | CU-24 | Ciclo 3 |
| Detección de cuellos de botella (IA) | CU-25 | Ciclo 3 |
| Reportes de proceso | CU-26 | Ciclo 3 |
| Notificaciones push (Flutter FCM) | CU-27/28 | Ciclo 3 |
| Historial completo de trámites | CU-29 | Ciclo 3 |
| Completar formulario por voz (IA real) | CU-30 | Ciclo 3 — FastAPI |
| Agente conversacional (RAG + n8n) | CU-31 | Ciclo 3 |

---

## 9. Orden recomendado de implementación en C2

```
1. G1-C2 ✅  Motor de Workflow (CU-07, CU-08, CU-09) — YA HECHA
              Archivo: C2/G1-Tramite-Motor-Workflow.md

2. G2-C2 🔲  Expediente Digital (CU-10, CU-16)
              Depende de: G1-C2 (necesita tramites existentes con expediente creado)
              Archivo a crear: C2/G2-Expediente-Digital.md

3. G3-C2 🔲  Decisiones del Flujo (CU-11, CU-17, CU-18)
              Depende de: G2-C2 (las decisiones se toman después de registrar el informe)
              Archivo a crear: C2/G3-Decisiones-Flujo.md

4. G4-C2 🔲  Colaboración en Diagramas (CU-15)
              Independiente — puede hacerse en paralelo con G2/G3
              Archivo a crear: C2/G4-Colaboracion-Diagramas.md
```

---

## 10. Instrucciones para la IA que continúe este trabajo

**Al crear G2-C2:**
1. Leer este G0 completo para entender el contexto
2. Leer `C2/G1-Tramite-Motor-Workflow.md` para entender cómo quedó el motor
3. Leer `base mermaid.md` para ver los modelos `ExpedienteDigital`, `SeccionExpediente`, `CampoSeccion`, `Adjunto`, `FormularioPlantilla`, `CampoPlantilla`
4. Implementar: `ExpedienteService`, `SeccionExpedienteService`, `AdjuntoService`, `ExpedienteController`
5. La sección activa se identifica por `estado = "en_curso"` — es la que desbloqueó el motor en G1
6. Al guardar una sección completada, internamente llamar a `WorkflowEngineService.completarNodo()` para que el motor avance

**Al crear G3-C2:**
1. Leer G0, G1, G2 de C2
2. Leer en el .docx los CUs: CU-11, CU-17, CU-18 (son los que tienen la lógica de decisión)
3. Los endpoints de G3-C2 complementan (no reemplazan) el `completarNodo` de G1 — son una capa más semántica encima del motor
4. `devolver` = activar la transición iterativa del diagrama (equivale a `decision = "no"`)
5. `decision-final` = activar la transición positiva (equivale a `decision = "si"`)

**Al crear G4-C2:**
1. Leer G0 y los endpoints de diagramas de C1 (G5)
2. El modelo `ColaboracionDiagrama` ya existe en MongoDB (ver `base mermaid.md`)
3. Para el demo: la parte de tiempo real puede ser polling — documentarlo como simplificación
4. Implementar el historial de versiones en `VersionDiagrama`

---

*G0 — Plan General Ciclo 2 · Sistema de Gestión de Trámites · Para continuar el desarrollo*
