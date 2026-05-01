# Fase 0 · Estándares y convenciones

> Reglas de nomenclatura y estructura interna que aplicaremos consistentemente en cada componente. Si surge una duda durante el refactor, esta es la referencia.

---

## 1. Nomenclatura de paquetes (Backend Java)

### Raíz del proyecto
Hoy: `com.example.demo`
Objetivo: `com.cre.tramites` (más identificable, más profesional)

### Estructura interna de cada componente

```
com.cre.tramites.<componente>/
├── api/                       ← INTERFACES PÚBLICAS (Ports) + DTOs públicos
│   ├── <Nombre>Port.java
│   └── dto/                   (DTOs que cruzan la frontera)
├── domain/                    ← ENTIDADES DEL DOMINIO
│   └── <Entidad>.java
└── internal/                  ← IMPLEMENTACIÓN (no visible afuera)
    ├── <Nombre>ServiceImpl.java
    ├── <Nombre>Repository.java
    └── <Nombre>Controller.java
```

### Reglas
- Solo lo que está en `api/` puede ser usado por **otros** componentes
- `domain/` puede ser referenciado en `api/` (como tipos de retorno/parámetros)
- `internal/` es 100% interno: clases sin `public` cuando sea posible
- Cada componente tiene un `package-info.java` explicando su propósito

---

## 2. Nomenclatura de interfaces (Ports)

### Patrón: `<Nombre del componente>Port`

| ✅ Bien | ❌ Mal |
|---|---|
| `WorkflowEnginePort` | `WorkflowEngineInterface` |
| `NotificacionPort` | `INotificacion` |
| `TrazabilidadPort` | `TrazabilidadAPI` |
| `ExpedientePort` | `IExpedienteService` |

### Variantes especializadas
Si un componente expone más de un grupo de operaciones:
- `<Nombre>QueryPort` para lecturas
- `<Nombre>CommandPort` para escrituras

Ej: `WorkflowQueryPort` (consultar trámites) y `WorkflowCommandPort` (iniciar/avanzar).

---

## 3. Nomenclatura de implementaciones (Adapters)

### Patrón principal: `<Nombre>ServiceImpl` (cuando es la implementación principal)

```java
@Service
class WorkflowEngineServiceImpl implements WorkflowEnginePort {
    ...
}
```

### Patrón cuando hay múltiples implementaciones: `<Nombre><Variante>Adapter`

```java
class NotificacionWebAdapter implements NotificacionPort { ... }
class NotificacionPushAdapter implements NotificacionPort { ... }
class NotificacionEmailAdapter implements NotificacionPort { ... }
```

---

## 4. Visibilidad

### Regla: lo más restrictivo posible

| Modificador | Cuándo usar |
|---|---|
| `public` | Solo en `api/` (interfaces, DTOs públicos, excepciones públicas) |
| (package-private, sin modificador) | Implementaciones, repositorios internos, helpers |
| `private` | Métodos auxiliares dentro de una clase |

### Ejemplo

```java
// api/NotificacionPort.java
public interface NotificacionPort {                    // ✅ public
    void enviar(NotificacionRequest req);
}

// internal/NotificacionServiceImpl.java
@Service
class NotificacionServiceImpl implements NotificacionPort {   // ✅ package-private
    private final NotificacionRepository repo;          // ✅ private
    ...
}

// internal/NotificacionRepository.java
interface NotificacionRepository extends MongoRepository<...> {  // ✅ package-private
    ...
}
```

---

## 5. DTOs

### Tres categorías

| Categoría | Ubicación | Visibilidad | Ejemplos |
|---|---|---|---|
| **Públicos del componente** | `api/dto/` | `public` | `IniciarTramiteRequest`, `EstadoTramiteResponse` |
| **Compartidos entre componentes** | `shared/dto/` | `public` | `ErrorResponse`, `PaginacionRequest` |
| **Internos del componente** | `internal/` | package-private | DTOs solo usados dentro |

### Convención de naming
- `*Request` — entrada desde HTTP
- `*Response` — salida hacia HTTP
- `*Dto` — transferencia interna entre capas del mismo componente
- `*Command` — operación que muta estado (DDD)
- `*Query` — operación que lee estado (DDD)

---

## 6. Controllers REST

### Reglas
- Viven en `internal/` del componente correspondiente
- Solo invocan al `*Port` del componente, **nunca** a un `Repository` directo
- No contienen lógica de negocio
- Solo: validación HTTP, mapeo Request → llamada al Port → mapeo Response

### Ejemplo

```java
// internal/NotificacionController.java
@RestController
@RequestMapping("/api/notificaciones")
class NotificacionController {

    private final NotificacionPort notificaciones;

    @GetMapping("/mis-notificaciones")
    public List<NotificacionResponse> mias(Authentication auth) {
        return notificaciones.listarPorDestinatario(auth.getName());
    }
}
```

---

## 7. Cómo un componente consume a otro

### ✅ Bien — vía Port

```java
// En componente workflow
@Service
class WorkflowEngineServiceImpl implements WorkflowEnginePort {

    private final NotificacionPort notificaciones;     // ✅ depende de la interfaz

    public Tramite completarNodo(...) {
        ...
        notificaciones.enviar(new NotificacionRequest(...));
    }
}
```

### ❌ Mal — implementación concreta

```java
@Service
class WorkflowEngineServiceImpl {
    private final NotificacionService notificaciones;  // ❌ acopla a impl
}
```

### ❌ Peor — repositorio ajeno

```java
@Service
class WorkflowEngineServiceImpl {
    private final NotificacionRepository notifRepo;    // ❌❌ rompe encapsulación
}
```

---

## 8. Frontend Angular: convenciones

### Estructura por feature

```
src/app/features/<feature>/
├── pages/                     ← componentes ruteables (smart)
├── components/                ← componentes presentacionales del feature (dumb)
├── services/                  ← servicios específicos del feature
└── <feature>.routes.ts
```

### UI Kit propio

```
src/app/ui-kit/
└── badge-estado/
    ├── badge-estado.component.ts
    ├── badge-estado.component.html
    ├── badge-estado.component.scss
    └── index.ts                ← export default
```

### Naming
- Selectores: `app-<nombre>` siempre (`app-badge-estado`)
- Servicios: `<Nombre>Service` (`TramitesService`)
- Modelos: `<Nombre>` simple, sin prefijos (`Tramite`, `Departamento`)
- Interfaces de DTOs: `<Nombre>Request`, `<Nombre>Response`

---

## 9. Frontend Flutter: convenciones

### Estructura por feature

```
mobile/lib/features/<feature>/
├── screens/
├── widgets/                   ← widgets propios del feature
├── services/
└── models/
```

### Widgets reutilizables (cross-feature)

```
mobile/lib/widgets/
├── estado_badge.dart
├── progreso_bar.dart
└── tramite_card.dart
```

### Naming
- Archivos: `snake_case.dart`
- Clases: `PascalCase`
- Variables: `camelCase`
- Constantes: `lowerCamelCase` con `static const`
- Enums: `PascalCase` con valores `lowerCamelCase`

---

## 10. Convenciones de commits

| Prefijo | Cuándo usar | Ejemplo |
|---|---|---|
| `refactor:` | Mover/renombrar sin cambiar comportamiento | `refactor: mover NotificacionService a notificaciones/internal` |
| `feat:` | Agregar interfaz/componente nuevo | `feat: extraer NotificacionPort` |
| `docs:` | Documentación pura | `docs: agregar README de componente expediente` |
| `fix:` | Bug que aparece durante refactor | `fix: corregir import roto tras mover JwtUtils` |
| `chore:` | Configuración, build, deps | `chore: actualizar package-info.java` |

### Granularidad recomendada
Un commit por componente extraído. Si el commit "refactor: extraer auth" tiene 80 archivos modificados, pero todos pertenecen a esa extracción, está bien.

---

## 11. Tests durante el refactor

Estrategia minimalista:

- **Antes** de extraer cada componente: verificar manualmente 1-2 flujos críticos del componente (login, completar nodo, etc.)
- **Después** de extraer: re-verificar los mismos flujos
- Si algo se rompe entre los dos, sabemos que fue ese commit

No agregamos tests unitarios masivos durante el refactor (no es el objetivo). En la fase 4 sí agregaremos los **ArchUnit tests** para validar la arquitectura.

---

## 12. README por componente

Cada componente del backend debe tener un `README.md` corto en su paquete raíz:

```markdown
# Componente: notificaciones

## Propósito
Enviar notificaciones a usuarios por web/push/email cuando ocurren
eventos de negocio (cambio de estado de trámite, asignación, etc.).

## Puerto público
- `NotificacionPort` — `enviar(req)`, `marcarLeida(id)`, `listarPor(usuario)`

## Consume
- (ninguno) — es un componente hoja

## Es consumido por
- `workflow` — al cambiar de nodo
- `expediente` — al subir un adjunto

## Colecciones Mongo
- `notificaciones`
```

Este README es **lo que el profesor leerá** para evaluar si el componente está bien definido.

---

## Próximo paso

Con las 5 fichas de fase 0 completas, el siguiente paso es **arrancar la fase 1**: extraer el primer componente del backend (recomendado: `auth` por ser el más aislado).
