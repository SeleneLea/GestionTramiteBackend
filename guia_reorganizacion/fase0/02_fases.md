# Fase 0 · Roadmap completo de fases

> Plan secuencial con entregables, tiempos estimados y orden recomendado. Cada fase produce algo verificable.

---

## Resumen ejecutivo

| Fase | Nombre | Duración | Entregable principal |
|------|--------|----------|----------------------|
| **0** | Planificación | 0.5 día | Esta guía completa |
| **1** | Backend → bounded contexts | 2-3 días | Backend reorganizado por componentes con interfaces |
| **2** | Angular → ui-kit + libs | 1.5-2 días | UI kit propio reutilizable |
| **3** | Flutter → widgets reutilizables | 1-1.5 días | Widget kit propio |
| **4** | Validación arquitectónica | 1 día | Diagramas UML + ArchUnit tests |
| | **Total estimado** | **~6-8 días** | |

---

## Fase 0 · Planificación (en curso ahora)

### Objetivo
Tener un plan claro **antes** de tocar una sola línea de código.

### Entregables
- [x] `README.md` — índice de la guía
- [x] `fase0/00_estrategia.md` — enfoque general
- [x] `fase0/01_principios.md` — 6 principios técnicos
- [x] `fase0/02_fases.md` — este documento
- [x] `fase0/03_estado_actual.md` — inventario del código actual
- [x] `fase0/04_estandares.md` — convenciones de nomenclatura

### Criterio de salida
Toda persona del equipo puede leer la fase 0 y saber **qué se va a hacer y por qué**.

---

## Fase 1 · Backend a bounded contexts

### Objetivo
Reorganizar el backend Spring Boot de paquetes-por-capa a paquetes-por-componente, con interfaces explícitas.

### Subfases

#### 1.1 — Crear shared kernel (0.5 día)
- Crear paquete `com.cre.tramites.shared`
- Mover ahí: `ErrorResponse`, `GlobalExceptionHandler`, DTOs verdaderamente compartidos

#### 1.2 — Extraer componente `auth` (0.5 día)
- Mover: `JwtUtils`, `JwtAuthFilter`, `AuthService`, `AuthController`, DTOs de auth
- Definir `AuthPort` interfaz con `login()`, `register()`, `validateToken()`
- Verificar: la app sigue logueando

#### 1.3 — Extraer componente `notificaciones` (0.5 día)
- Mover: `NotificacionService`, `NotificacionRepository`, `NotificacionController`, modelo `Notificacion`
- Definir `NotificacionPort` con `enviar(destinatario, tipo, titulo, mensaje, canal)`
- Cambiar inyección en `WorkflowEngineService` y otros: ahora dependen de `NotificacionPort`
- Verificar: las notificaciones siguen llegando

#### 1.4 — Extraer componente `trazabilidad` (0.25 día)
- Mover: `TrazabilidadService`, `TrazabilidadRepository`, modelo `Trazabilidad`
- Definir `TrazabilidadPort` con `registrar(...)`
- Verificar: hash chain sigue generándose

#### 1.5 — Extraer componente `expediente` (0.5 día)
- Mover: `ExpedienteService`, `ExpedienteController`, `SeccionExpediente`, repos
- Definir `ExpedientePort`
- Verificar: completar secciones sigue funcionando

#### 1.6 — Extraer componente `metricas` (0.25 día)
- Mover: `MetricaYCuelloService`, repos, controller
- Definir `MetricasPort`
- Verificar: registro de métricas y detección de cuellos sigue funcionando

#### 1.7 — Extraer componente `workflow-design` (0.5 día)
- Mover: `DiagramaWorkflowService`, `NodoDiagramaService`, `FlujoTransicionService`, `PromptFlowService`, `ColaboracionService`
- Definir `DiagramaDesignPort`
- Verificar: editor de diagramas sigue creando flujos

#### 1.8 — Extraer componente `workflow` (núcleo, último por ser el más grande) (0.5 día)
- Mover: `WorkflowEngineService`, `TramiteService`, `TramiteCicloVidaService`, `TramiteDecisionService`, modelo `Tramite`
- Definir `WorkflowEnginePort` con `iniciarTramite()`, `completarNodo()`, `derivar()`, `devolver()`
- Verificar: trámites siguen avanzando

#### 1.9 — Extraer `ai-integration` y `reportes` (0.25 día)
- Mover los archivos correspondientes
- Definir sus puertos
- Verificar

### Criterio de salida fase 1
- Backend compila
- Todos los endpoints siguen respondiendo
- `mvn dependency:tree` no muestra dependencias circulares entre componentes
- Cada componente tiene su `package-info.java` o README corto

---

## Fase 2 · Angular ui-kit y libs

### Objetivo
Extraer componentes de UI repetidos en un kit propio reutilizable.

### Componentes a crear (mínimo viable)

| Componente | Reutiliza en | Tiempo |
|---|---|---|
| `<app-badge-estado>` | mis-tramites, bandeja, observados, detalle | 1 hora |
| `<app-progress-bar-tramite>` | mis-tramites, detalle | 1 hora |
| `<app-card-tramite>` | mis-tramites, observados | 2 horas |
| `<app-data-table>` (genérica) | listados de admin | 3 horas |
| `<app-form-dinamico>` | secciones de expediente | 4 horas |
| `<app-timeline>` | detalle de trámite | 2 horas |

### Estructura objetivo

```
src/app/
├── ui-kit/                    ← componentes reutilizables propios
│   ├── badge-estado/
│   ├── progress-bar-tramite/
│   ├── card-tramite/
│   └── ...
├── core/                      ← servicios HTTP, modelos compartidos
│   ├── services/
│   ├── models/
│   └── interceptors/
├── features/                  ← features de negocio (renombrado de admin/, funcionario/)
│   ├── tramites/
│   ├── diagramas/
│   ├── auth/
│   └── ...
└── app.config.ts
```

### Criterio de salida fase 2
- Al menos 5 componentes del ui-kit creados
- Todas las pantallas que mostraban badges/progress/cards usan los componentes nuevos
- Cero duplicación de lógica de "color por estado" en plantillas

---

## Fase 3 · Flutter widgets reutilizables

### Objetivo
Mismo patrón que Angular: extraer widgets repetidos.

### Widgets a crear

| Widget | Reutiliza en | Tiempo |
|---|---|---|
| `EstadoBadge` | varias pantallas | 1 hora |
| `ProgresoBar` | mis-tramites, detalle | 1 hora |
| `TramiteCard` | mis-tramites, observados | 2 horas |
| `TimelineCustomTile` | detalle | 1 hora |

### Estructura objetivo

```
mobile/lib/
├── widgets/                   ← widgets propios reutilizables
│   ├── estado_badge.dart
│   ├── progreso_bar.dart
│   ├── tramite_card.dart
│   └── timeline_custom_tile.dart
├── core/                      ← config, env, http
├── features/                  ← features (renombrado de screens/)
│   ├── auth/
│   ├── tramites/
│   ├── comunicacion/
│   └── ...
├── services/                  ← se mantienen, pero migran a features cuando aplique
└── main.dart
```

### Criterio de salida fase 3
- Pantallas que mostraban badges/cards usan los widgets reutilizables
- Lógica de color/icono de estado consolidada en un único lugar

---

## Fase 4 · Validación arquitectónica

### Objetivo
Demostrar que la arquitectura es real y verificable, no solo cosmética.

### Entregables

#### 4.1 — Diagrama de componentes UML (Enterprise Architect)
- Cada componente del backend como caja con su interfaz `*Port`
- Conexiones via `<<provides>>` y `<<requires>>`
- Subir como imagen + archivo .eap a `fase4/diagramas/`

#### 4.2 — Diagrama de despliegue
- Cliente Flutter → API REST → Backend Spring Boot → MongoDB
- Microservicios IA (FastAPI) como nodos opcionales

#### 4.3 — ArchUnit tests
Tests Java que verifican automáticamente:
- Componente `workflow` no importa de `internal/` de otros componentes
- Solo paquetes `internal/` contienen `@Service`, `@Repository`
- Las clases en `api/` son interfaces o DTOs, nunca `@Service`

#### 4.4 — Documento de decisiones arquitectónicas (ADRs)
- Por qué se eligió este corte y no otro
- Por qué no se usó Camunda
- Por qué se hicieron componentes propios y no se usó PrimeNG

### Criterio de salida fase 4
- Diagrama de componentes UML listo para presentar
- Build de CI corre ArchUnit y pasa
- Documento PDF con la arquitectura para anexar a la entrega

---

## Mapa de prioridades si se acaba el tiempo

Si por cualquier razón no llegamos al final, este es el orden de importancia para la nota:

1. 🥇 **Fase 1 + Fase 4.1** (backend componentizado + diagrama UML) — **imprescindible**
2. 🥈 **Fase 4.4** (decisiones arquitectónicas escritas) — **muy importante**
3. 🥉 **Fase 2** (Angular ui-kit) — **importante**
4. **Fase 4.3** (ArchUnit tests) — **bonito tener**
5. **Fase 3** (Flutter) — **bonito tener**

---

## Próximo paso

Leer **`03_estado_actual.md`** para ver el inventario detallado del código que vamos a reorganizar.
