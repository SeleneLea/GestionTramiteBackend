# Guía de Reorganización a Arquitectura Basada en Componentes

> Plan de migración del sistema de gestión de trámites desde una estructura monolítica por capas hacia una **arquitectura basada en componentes** con *bounded contexts* y puertos/adaptadores.

---

## Por qué existe esta guía

El enunciado del examen exige:

1. **Arquitectura del software** plenamente reflejada (diagramas de componentes, despliegue, etc.).
2. **Desarrollo basado en componentes**:
   - Usar componentes hechos cuando aplique.
   - Que **lo que implementemos sea reutilizable, idealmente a nivel de componente**.

La estructura actual del proyecto está organizada **por capas técnicas** (`controllers/`, `services/`, `repositories/`), no por **componentes de negocio**. Esto hace que cualquier cambio en un dominio toque varios paquetes y que no haya límites claros entre módulos.

Esta guía documenta cómo pasar a una organización por **componentes con interfaces explícitas**, sin reescribir desde cero.

---

## Estructura de la guía

```
guia_reorganizacion/
├── README.md                          ← este archivo (índice)
├── fase0/                             ← Planificación (NO se toca código)
│   ├── 00_estrategia.md               ← Cómo vamos a trabajar
│   ├── 01_principios.md               ← Principios de componentes que aplicamos
│   ├── 02_fases.md                    ← Roadmap completo de las 5 fases
│   ├── 03_estado_actual.md            ← Inventario del código actual
│   └── 04_estandares.md               ← Nomenclatura y convenciones
├── fase1/  (próximamente)             ← Backend: bounded contexts + puertos
├── fase2/  (próximamente)             ← Angular: ui-kit y libs
├── fase3/  (próximamente)             ← Flutter: widgets reutilizables
└── fase4/  (próximamente)             ← Validación: UML, ArchUnit, despliegue
```

---

## Cómo leer esta guía

1. Empezar por **fase0/00_estrategia.md** — explica el enfoque general
2. Luego **fase0/01_principios.md** — los 6 principios que guían cada decisión
3. Seguir con **fase0/02_fases.md** — el plan completo con tiempos
4. Revisar **fase0/03_estado_actual.md** — para entender de dónde partimos
5. Tener a mano **fase0/04_estandares.md** — para consultar nombres y convenciones

---

## Regla de oro del proceso

> **No se toca código en fase 0.** Es solo planificación y documentación.
> Recién en fase 1 se empiezan los movimientos de archivos.

Esto evita que arranquemos refactorizando sin un norte claro y que terminemos con código a medio mover.
