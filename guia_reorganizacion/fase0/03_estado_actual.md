# Fase 0 · Estado actual del proyecto

> Inventario de lo que tenemos hoy. Sirve de baseline para saber qué se mueve a dónde en fase 1.

---

## 1. Visión global

| Componente lógico | Tecnología | Ubicación | Estado |
|---|---|---|---|
| Backend API | Spring Boot 3 + Java 21 | `Backend/` | Funcional con bugs depurados |
| Web admin/funcionario | Angular 21 + Standalone components | `WEB_angular/` | Funcional |
| App cliente | Flutter + GetX | `mobile/` | Funcional |
| Base de datos | MongoDB | `tramites_db` | Producción local |

---

## 2. Backend: estructura actual (por capas)

```
Backend/src/main/java/com/example/demo/
├── DemoApplication.java
├── config/                    ← MongoIndexConfig, OpenApiConfig, SecurityConfig, GlobalExceptionHandler
├── controllers/               ← ~24 controllers
├── dto/                       ← ~30+ DTOs
├── models/                    ← ~25 modelos Mongo
├── repositories/              ← ~21 repositorios
├── security/                  ← JwtUtils, JwtAuthFilter
└── services/                  ← ~21 services
```

### Controllers existentes
`AuthController`, `DepartamentoController`, `ActividadController`, `HealthController`, `PoliticaNegocioController`, `RolController`, `DiagramaWorkflowController`, `NodoDiagramaController`, `PromptFlowController`, `PermisoController`, `FlujoTransicionController`, `ColaboracionController`, `ExpedienteController`, `TramiteDecisionController`, `MetricaController`, `TramiteCicloVidaController`, `HistorialTramiteController`, `NotificacionController`, `ReporteController`, `AiIntegrationController`, `TramiteController`, `UsuarioController`, `WorkflowController`.

### Services existentes
`TramiteService`, `AuthService`, `UsuarioService`, `DepartamentoService`, `RolService`, `NodoDiagramaService`, `PromptFlowService`, `FlujoTransicionService`, `AiIntegrationService`, `MetricaYCuelloService`, `NotificacionService`, `ReporteService`, `TrazabilidadService`, `ColaboracionService`, `TramiteCicloVidaService`, `ExpedienteService`, `TramiteDecisionService`, `WorkflowEngineService`, `DiagramaWorkflowService`, `ActividadService`, `PoliticaNegocioService`.

### Problemas estructurales detectados

1. **Acoplamiento horizontal:** un service inyecta directamente otros services concretos (no interfaces). Ej: `WorkflowEngineService` inyecta `NotificacionService`, `MetricaYCuelloService`, `TrazabilidadService` directamente.

2. **Sin frontera por dominio:** todo en el mismo paquete `com.example.demo.*`. No hay forma de saber qué archivo "pertenece" a qué componente de negocio.

3. **No hay puertos:** todas las clases públicas son implementaciones, no interfaces.

4. **`config/` mezcla todo:** Mongo, OpenAPI, Security y manejo global de errores en el mismo paquete.

---

## 3. Mapeo del estado actual al objetivo (componentes)

| Archivos hoy | Componente destino |
|---|---|
| `AuthController`, `AuthService`, `JwtUtils`, `JwtAuthFilter`, `LoginRequest`, `RegisterClienteRequest`, `AuthResponse`, `CrearUsuarioAdminRequest`, `UsuarioController`, `UsuarioService` | **auth** |
| `WorkflowController`, `WorkflowEngineService`, `TramiteController`, `TramiteService`, `TramiteCicloVidaController`, `TramiteCicloVidaService`, `TramiteDecisionController`, `TramiteDecisionService`, `Tramite`, `EstadoActual`, `EstadoHistorico`, `HistorialTramiteController`, `TramiteRepository`, `EstadoActualRepository`, `EstadoHistoricoRepository`, `IniciarTramiteRequest`, `CompletarNodoRequest`, `EstadoTramiteResponse`, `LineaTiempoResponse`, `HitoDTO`, `DerivarTramiteRequest`, `DevolverTramiteRequest`, `DecisionFinalRequest` | **workflow** |
| `ExpedienteController`, `ExpedienteService`, `ExpedienteDigital`, `SeccionExpediente`, `CampoSeccion`, `Adjunto`, `ExpedienteDigitalRepository`, `SeccionExpedienteRepository`, `CampoSeccionRepository`, `AdjuntoRepository`, `CompletarSeccionRequest`, `GuardarSeccionRequest`, `CampoValorDto` | **expediente** |
| `NotificacionController`, `NotificacionService`, `Notificacion`, `NotificacionRepository`, `CanalEnvio`, `CanalEnvioRepository` | **notificaciones** |
| `MetricaController`, `MetricaYCuelloService`, `MetricaTiempo`, `CuelloBotella`, `MetricaTiempoRepository`, `CuelloBotellaRepository` | **metricas** |
| `TrazabilidadService`, `Trazabilidad`, `TrazabilidadRepository` | **trazabilidad** |
| `DiagramaWorkflowController`, `DiagramaWorkflowService`, `NodoDiagramaController`, `NodoDiagramaService`, `FlujoTransicionController`, `FlujoTransicionService`, `PromptFlowController`, `PromptFlowService`, `ColaboracionController`, `ColaboracionService`, `DiagramaWorkflow`, `NodoDiagrama`, `FlujoTransicion`, `ColaboracionDiagrama`, `VersionDiagrama`, `FormularioPlantilla`, `CampoPlantilla`, sus repositorios, todos los DTOs `Diagrama*Request`, `Nodo*Request`, `Flujo*Request`, `Prompt*`, `InvitarColaboradorRequest`, `ResponderInvitacionRequest` | **workflow-design** |
| `AiIntegrationController`, `AiIntegrationService`, `LogAgente`, `TranscripcionVoz`, `AgenteRequest`, `AgenteResponse` | **ai-integration** |
| `ReporteController`, `ReporteService`, `Reporte`, `ReporteRequest` | **reportes** |
| `DepartamentoController`, `DepartamentoService`, `Departamento`, `DepartamentoRepository`, `DepartamentoRequest`, `ActividadController`, `ActividadService`, `Actividad`, `ActividadRepository`, `RolController`, `RolService`, `Rol`, `RolRepository`, `RolRequest`, `PermisoController`, `Permiso`, `PermisoRepository`, `AsignarPermisosRequest`, `PoliticaNegocioController`, `PoliticaNegocioService`, `PoliticaNegocio`, `VersionPolitica`, `PoliticaNegocioRepository`, `VersionPoliticaRepository`, `PoliticaNegocioRequest` | **catalogo-configuracion** (nuevo, agrupa entidades de configuración) |
| `ErrorResponse`, `GlobalExceptionHandler` | **shared** |
| `MongoIndexConfig`, `OpenApiConfig`, `SecurityConfig`, `HealthController` | **infrastructure** (config) |

---

## 4. Mapa de dependencias (acoplamientos críticos a desacoplar)

```
WorkflowEngineService ──> NotificacionService     (debe ir por NotificacionPort)
                     ──> MetricaYCuelloService    (debe ir por MetricasPort)
                     ──> TrazabilidadService      (debe ir por TrazabilidadPort)
                     ──> ExpedienteService (?)    (revisar)

PromptFlowService    ──> DepartamentoRepository   (queda en su componente)

ExpedienteController ──> WorkflowEngineService(?) (revisar — ¿lo necesita o no?)
```

---

## 5. Frontend Angular: estructura actual

```
WEB_angular/src/app/
├── admin/                     ← features de admin (mezclados)
├── funcionario/               ← features de funcionario
├── core/services/             ← servicios HTTP
└── ...
```

### Componentes UI duplicados detectados
- Badge de estado con colores: lógica repetida en 4-5 plantillas
- Barra de progreso: lógica duplicada
- Tarjetas de trámite/política: estructura similar repetida
- Diálogos de confirmación: ad-hoc en cada feature

---

## 6. Mobile Flutter: estructura actual

```
mobile/lib/
├── screens/
│   ├── tramites/              ← 7+ pantallas
│   ├── comunicacion/
│   └── ...
├── services/                  ← TramitesEnvioService, TramitesSeguimientoService, ComunicacionService, AuthService
├── models/                    ← modelos por dominio
├── widgets/                   ← prácticamente vacío
└── routes/
```

### Widgets duplicados detectados
- Cards de trámite con lógica de color de estado repetida
- Barras de progreso construidas inline en cada pantalla
- Lógica de "fecha formateada" repetida (`_formatoFecha`) en varios screens

---

## 7. Base de datos (MongoDB)

Colecciones existentes y a qué componente pertenecen:

| Colección | Componente |
|---|---|
| `usuarios`, `roles`, `permisos` | auth + catalogo-configuracion |
| `tramites`, `estados_historicos` | workflow |
| `expedientes`, `secciones_expediente`, `adjuntos` | expediente |
| `notificaciones` | notificaciones |
| `metricas_tiempo`, `cuellos_botella` | metricas |
| `trazabilidad` | trazabilidad |
| `diagramas_workflow`, `nodos_diagrama`, `flujos_transicion`, `colaboracion_diagrama`, `versiones_diagrama` | workflow-design |
| `logs_agente`, `transcripciones_voz` | ai-integration |
| `reportes` | reportes |
| `departamentos`, `actividades`, `politicas_negocio`, `versiones_politica` | catalogo-configuracion |

> **Nota importante:** la BD **NO se reorganiza**. Las colecciones ya están alineadas con los componentes. Solo cambiamos quién accede a cada colección (cada componente accede únicamente a las suyas).

---

## 8. Métricas del estado actual

| Métrica | Valor aproximado |
|---|---|
| Líneas de código backend | ~15.000 |
| Archivos `.java` | ~100 |
| Endpoints REST | ~80+ |
| Pantallas Angular | ~25+ |
| Pantallas Flutter | ~15+ |
| Colecciones Mongo | 15+ |

---

## Próximo paso

Leer **`04_estandares.md`** para conocer las convenciones de nomenclatura que vamos a aplicar al refactorizar.
