# ADR-001 — Organizar el código por componentes de negocio en lugar de capas técnicas

**Fecha:** 2026-04-26
**Estado:** aceptado
**Decidido por:** equipo del proyecto

---

## Contexto

La estructura inicial del backend Spring Boot organizaba el código por capas técnicas:

```
src/main/java/com/example/demo/
├── controllers/    (~24 archivos)
├── services/       (~21 archivos)
├── repositories/   (~29 archivos)
├── models/         (~29 archivos)
└── dto/            (~30+ archivos)
```

Cualquier cambio en un dominio (ej: notificaciones) tocaba archivos en los 5 paquetes. No había frontera clara entre módulos: cualquier service podía inyectar cualquier repository de cualquier dominio.

El enunciado del primer parcial exige:
1. Arquitectura del software plenamente reflejada en diagramas.
2. Desarrollo basado en componentes.
3. Que lo que implementemos sea reutilizable, idealmente a nivel de componente.

## Decisión

Reorganizamos el backend bajo `modules/<componente>/`, donde cada componente representa un **dominio de negocio** y contiene internamente subcarpetas estándar:

```
modules/<componente>/
├── api/        ← interfaces públicas (Ports) + DTOs públicos
├── domain/     ← entidades del dominio
└── internal/   ← implementaciones, repositorios, controllers (package-private)
```

Componentes definidos: `auth`, `catalogo`, `notificaciones`, `trazabilidad`, `metricas`, `expediente`, `workflowdesign`, `workflow` (núcleo), `aiintegration`, `reportes`, más un `shared` kernel.

## Alternativas consideradas

- **Mantener capas técnicas:** rechazada porque no cumple el requisito de componentes reutilizables ni provee encapsulación por dominio.
- **Microservicios separados desde el inicio:** rechazada por sobrediseño dado el tamaño actual del proyecto y los plazos del examen. La arquitectura por componentes es un paso intermedio que **permite** esa migración futura.

## Consecuencias

### Positivas
- Cada componente es una unidad cohesiva, comprensible por sí sola.
- El blast radius de un cambio queda contenido al componente afectado.
- Facilita extraer un componente como microservicio en el futuro.
- Cumple plenamente el requisito del enunciado.

### Negativas
- Requiere disciplina sostenida en cada PR (revisar visibilidad e imports).
- Costo único de movimiento masivo de archivos durante el refactor.

## Referencias

- `guia_reorganizacion/fase0/01_principios.md` — los 6 principios de componentes
- `guia_reorganizacion/fase1/` — detalle de la migración
- ADR-002 (siguiente) — cómo se comunican los componentes entre sí
