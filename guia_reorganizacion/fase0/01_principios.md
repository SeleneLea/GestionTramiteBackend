# Fase 0 · Principios de componentes

> Los 6 principios técnicos que guían cada decisión durante la reorganización. Cuando dudemos si algo "está bien", volvemos a esta lista.

---

## Principio 1 · Una responsabilidad de negocio por componente

Cada componente representa **un solo dominio de negocio**, no una capa técnica.

| ❌ Mal | ✅ Bien |
|---|---|
| `controllers/` | `workflow/` |
| `services/` | `notificaciones/` |
| `repositories/` | `expediente/` |

**Test mental:** *si tuviera que explicarle a un usuario no técnico qué hace este componente, ¿lo entendería en una frase?*

- ✅ "El componente de notificaciones avisa al cliente y al funcionario cuando algo cambia"
- ❌ "El paquete de servicios contiene clases @Service que llaman a los repositorios"

---

## Principio 2 · Interfaces explícitas (puertos)

Un componente expone **interfaces**, nunca clases concretas.

```java
// ❌ MAL — exporta la implementación
public class NotificacionService {
    public void enviar(...) { ... }
}

// ✅ BIEN — interfaz pública + implementación interna
public interface NotificacionPort {
    void enviar(...);
}

class NotificacionWebAdapter implements NotificacionPort {
    public void enviar(...) { ... }
}
```

**Por qué:** otros componentes dependen de la interfaz. La implementación se puede cambiar (web → push → email) sin tocar a quien la consume.

---

## Principio 3 · Encapsulación por paquete

Las clases internas de un componente **no deben ser visibles desde otros componentes**.

En Java, esto se logra con visibilidad **package-private** (sin `public`):

```java
// workflow/api/WorkflowEnginePort.java
public interface WorkflowEnginePort { ... }   // ✅ pública

// workflow/internal/AvanceCalculator.java
class AvanceCalculator { ... }                 // ✅ interna (sin public)
```

**Test mental:** *¿este `public` lo necesita alguien fuera del componente?* Si no, eliminarlo.

---

## Principio 4 · Dependencias hacia adentro, no hacia los lados

Un componente puede depender de:

- ✅ Su propio dominio (clases internas)
- ✅ El **shared kernel** (DTOs comunes, errores, utils)
- ✅ Interfaces (`*Port`) de otros componentes

Un componente **no debe**:
- ❌ Importar clases internas de otro componente
- ❌ Llamar directamente al repositorio de otro componente
- ❌ Depender de la implementación concreta de otro componente

**Diagrama mental:**

```
┌──────────────┐         ┌──────────────┐
│ Componente A │ ──────> │ Componente B │
│              │  Port   │              │
└──────────────┘         └──────────────┘
       │                        │
       └────────┬───────────────┘
                ▼
        ┌──────────────┐
        │ Shared Kernel│
        └──────────────┘
```

---

## Principio 5 · Reutilización a nivel de componente, no de clase

El criterio de éxito de la refactorización es: *¿este componente se podría sacar e instalar en otro proyecto?*

Para que la respuesta sea sí:

- El componente **no** debe asumir nada del entorno fuera de sus puertos
- Sus configuraciones deben estar parametrizadas (no hard-codeadas)
- Sus dependencias externas (Mongo, RestTemplate, etc.) deben estar inyectadas, no creadas dentro

**Ejemplo concreto:** el componente `trazabilidad-component` (hash chain) debería poder usarse en cualquier sistema de auditoría, no solo en este de trámites.

---

## Principio 6 · Cohesión alta, acoplamiento bajo

| Métrica | Cómo se ve |
|---|---|
| **Cohesión alta** | Las clases dentro del componente cambian juntas y por la misma razón |
| **Acoplamiento bajo** | Cambiar el componente A no obliga a cambiar el componente B |

**Señal de alerta de cohesión baja:** un componente con clases que cambian por razones distintas (ej. lógica de notificaciones mezclada con cálculo de métricas).

**Señal de alerta de acoplamiento alto:** modificar la firma de un método interno hace que falle compilación en 4 paquetes distintos.

---

## Aplicación a nuestro proyecto

Con estos 6 principios, la división propuesta del backend queda:

```
com.cre.tramites/
├── shared/                    ← shared kernel: DTOs comunes, ErrorResponse, utils
│
├── auth/                      ← Componente de autenticación
│   ├── api/                   ← AuthPort, JwtPort
│   └── internal/              ← JwtUtils, JwtAuthFilter
│
├── workflow/                  ← Componente del motor de workflow
│   ├── api/                   ← WorkflowEnginePort, TramitePort
│   ├── domain/                ← Tramite, NodoDiagrama, FlujoTransicion
│   └── internal/              ← WorkflowEngineService, repositorios
│
├── expediente/                ← Componente de expediente digital
│   ├── api/                   ← ExpedientePort
│   ├── domain/                ← ExpedienteDigital, SeccionExpediente
│   └── internal/              ← ExpedienteService, repositorios
│
├── notificaciones/            ← Componente de notificaciones
│   ├── api/                   ← NotificacionPort
│   └── internal/              ← NotificacionService (web/push/email)
│
├── metricas/                  ← Componente de métricas y cuellos de botella
│   ├── api/                   ← MetricasPort
│   └── internal/              ← MetricaYCuelloService
│
├── trazabilidad/              ← Componente de auditoría con hash chain
│   ├── api/                   ← TrazabilidadPort
│   └── internal/              ← TrazabilidadService
│
├── workflow-design/           ← Componente de diseño de diagramas (incluye prompt-flow)
│   ├── api/                   ← DiagramaDesignPort
│   └── internal/              ← DiagramaService, NodoService, PromptFlowService
│
├── ai-integration/            ← Componente de integración con servicios IA externos
│   ├── api/                   ← VozPort, AgentePort
│   └── internal/              ← AiIntegrationService
│
└── reportes/                  ← Componente de reportería
    ├── api/                   ← ReportesPort
    └── internal/              ← ReporteService
```

---

## Próximo paso

Leer **`02_fases.md`** para ver el roadmap completo con tiempos.
