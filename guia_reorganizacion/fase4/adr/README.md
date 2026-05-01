# Architectural Decision Records

> Decisiones arquitectónicas registradas. Una por archivo. Cada una documenta el contexto, lo que se decidió, las alternativas consideradas y las consecuencias.

---

## Índice

| # | Título | Estado | Importancia |
|---|--------|--------|-------------|
| [001](ADR-001-componentes-vs-capas.md) | Componentes vs Capas como organización principal | aceptado | 🔴 |
| [002](ADR-002-puertos-y-adaptadores.md) | Puertos y Adaptadores entre componentes | aceptado | 🔴 |
| [003](ADR-003-shared-kernel.md) | Shared kernel para utilidades transversales | aceptado | 🟡 |
| [004](ADR-004-no-camunda.md) | No usar Camunda — motor propio | aceptado | 🔴 |
| [005](ADR-005-cqrs-light-workflow.md) | CQRS-light en workflow (Engine + Query Port) | aceptado | 🟡 |
| [006](ADR-006-features-por-dominio.md) | Features por dominio (no por rol) en frontend | aceptado | 🟡 |
| [007](ADR-007-ui-kit-propio.md) | UI Kit propio (no PrimeNG/Angular Material completo) | aceptado | 🟡 |
| [008](ADR-008-estado-utils-puros.md) | Estado de UI consolidado en utils puros | aceptado | 🟢 |

---

## Plantilla

Para crear un nuevo ADR, copiar [_PLANTILLA.md](_PLANTILLA.md) y rellenar las secciones.

## Convenciones

- Numeración secuencial: `ADR-001`, `ADR-002`, etc. **Nunca** se reutiliza un número.
- Estados: `propuesto` → `aceptado` → `obsoleto`. Un ADR aceptado se puede marcar `obsoleto` cuando otro ADR posterior lo reemplaza, pero **no se borra** (queda como historia).
- Cada ADR es una página (~250 palabras). No es ensayo, es decisión.
