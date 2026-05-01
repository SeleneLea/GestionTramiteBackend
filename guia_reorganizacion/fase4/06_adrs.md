# Fase 4.6 · Architectural Decision Records (ADRs)

> Documentos cortos que registran **por qué** se tomó cada decisión arquitectónica importante. El profesor los va a leer.

---

## 1. ¿Qué es un ADR?

Un ADR (Architectural Decision Record) es un documento corto (1 página) que sigue siempre la misma plantilla:

- **Contexto:** ¿qué problema enfrentábamos?
- **Decisión:** ¿qué elegimos?
- **Alternativas consideradas:** ¿qué otras opciones había?
- **Consecuencias:** ¿qué ganamos y qué perdemos?

Son la **memoria institucional** del proyecto. Cuando alguien se pregunta "¿por qué hicieron esto así?", la respuesta está en el ADR.

---

## 2. ADRs a producir

| # | Título | Importancia |
|---|--------|-------------|
| ADR-001 | Componentes vs Capas como organización principal | 🔴 |
| ADR-002 | Puertos y Adaptadores entre componentes | 🔴 |
| ADR-003 | Shared kernel para utilidades transversales | 🟡 |
| ADR-004 | No usar Camunda — motor propio | 🔴 |
| ADR-005 | CQRS-light en workflow (Engine + Query Port) | 🟡 |
| ADR-006 | Features por dominio (no por rol) en frontend | 🟡 |
| ADR-007 | UI Kit propio (no PrimeNG/Angular Material completo) | 🟡 |
| ADR-008 | Estado de UI consolidado en utils puros | 🟢 |

---

## 3. Plantilla

Crear `fase4/adr/_PLANTILLA.md`:

```markdown
# ADR-NNN — Título corto

**Fecha:** YYYY-MM-DD
**Estado:** propuesto | aceptado | obsoleto
**Decidido por:** (nombres)

## Contexto
(Descripción del problema o situación que motiva la decisión.
Hechos, restricciones, presiones de negocio.)

## Decisión
(Lo que se eligió hacer. En presente: "Adoptamos X.")

## Alternativas consideradas
- **Alt 1:** descripción + por qué se descartó
- **Alt 2:** descripción + por qué se descartó

## Consecuencias

### Positivas
- ...
- ...

### Negativas / costos asumidos
- ...
- ...

### Neutras / a observar
- ...

## Referencias
(Links a otros ADRs, documentos, código relevante)
```

---

## 4. ADRs concretos a escribir

### ADR-001: Componentes vs Capas

```markdown
# ADR-001 — Organizar el código por componentes de negocio en lugar de capas técnicas

**Fecha:** 2026-04-26
**Estado:** aceptado
**Decidido por:** equipo

## Contexto
La estructura inicial del backend organizaba el código por capas técnicas (`controllers/`,
`services/`, `repositories/`, `models/`). Esto generaba que cualquier cambio en un dominio
de negocio (ej: notificaciones) tocara archivos de los 4 paquetes, sin frontera clara
entre módulos. El enunciado del examen exige "arquitectura basada en componentes"
con encapsulación y reutilización a nivel de componente.

## Decisión
Reorganizamos el backend Spring Boot bajo `modules/<componente>/`, donde cada
componente representa un dominio de negocio (workflow, expediente, notificaciones,
catalogo, auth, etc.) y contiene internamente sus propias subcarpetas
`api/`, `domain/`, `internal/`.

## Alternativas consideradas
- **Mantener capas técnicas:** rechazada porque no permite componentes reutilizables
  ni encapsulación por dominio.
- **Microservicios separados desde el inicio:** rechazada por sobrediseño dado
  el tamaño actual del proyecto y los plazos del examen.

## Consecuencias

### Positivas
- Cada componente es una unidad cohesiva, comprensible por sí sola.
- Reduce el blast radius de los cambios.
- Facilita extraer un componente como microservicio en el futuro si crece.
- Cumple plenamente el requisito del enunciado.

### Negativas
- Requiere disciplina sostenida: hay que revisar cada inyección y cada import.
- Curva de aprendizaje breve para nuevos miembros del equipo.

## Referencias
- `guia_reorganizacion/fase0/01_principios.md`
- `guia_reorganizacion/fase1/`
```

### ADR-002: Puertos y Adaptadores

```markdown
# ADR-002 — Inversión de dependencias entre componentes vía Ports

**Fecha:** 2026-04-26
**Estado:** aceptado

## Contexto
Tras adoptar la organización por componentes (ADR-001), surgió la pregunta de
**cómo deben comunicarse entre sí**. Inyectar implementaciones concretas
(`@Autowired NotificacionService`) reintroduce el acoplamiento que queremos evitar.

## Decisión
Cada componente expone su funcionalidad como una interfaz Java llamada `<Nombre>Port`
ubicada en `api/`. Los demás componentes solo dependen de esa interfaz, nunca de la
implementación. Las implementaciones (`*ServiceImpl`) viven en `internal/` y son
package-private.

## Alternativas consideradas
- **Inyección de implementaciones concretas:** rechazada por acoplamiento.
- **Eventos asíncronos (event bus):** rechazada por complejidad innecesaria
  para flujos síncronos del dominio.

## Consecuencias

### Positivas
- Cualquier componente puede ser sustituido por otra implementación sin afectar
  a sus consumidores (ej: cambiar `NotificacionWebAdapter` por
  `NotificacionPushAdapter`).
- Tests unitarios pueden mockear el Port fácilmente.
- Refleja el patrón hexagonal de Alistair Cockburn.

### Negativas
- Introduce una clase extra (la interfaz) por cada operación pública.
- Ligero overhead de mantener firmas sincronizadas entre Port e Impl.
```

### ADR-004: No Camunda

```markdown
# ADR-004 — Implementar motor de workflow propio en lugar de Camunda 7

**Fecha:** 2026-04-26
**Estado:** aceptado

## Contexto
El sistema requiere un motor de workflow que ejecute diagramas UML 2.5 con
swimlanes, soportando flujos lineales, paralelos (fork/join), condicionales
(decision) e iterativos. Existen motores listos como Camunda 7, Flowable o JBPM.

## Decisión
Implementamos un motor propio (`WorkflowEnginePort`) en lugar de integrar Camunda.

## Alternativas consideradas
- **Camunda 7 + bpmn-js:** habría reemplazado el ~60% del backend. Rechazada
  porque el motor de workflow es exactamente lo que el examen evalúa: usar
  un motor importado equivale a no haber implementado el núcleo.
- **Flowable:** mismas razones que Camunda.
- **JBPM:** descartado por curva de aprendizaje y peso de Drools.

## Consecuencias

### Positivas
- Demostramos comprensión y control total del flujo de ejecución.
- Sin dependencias pesadas adicionales en el classpath.
- Notación UML 2.5 propia, no obligados a BPMN.
- Modelo de datos en MongoDB simple y a la medida.

### Negativas
- No tenemos editor visual gratis (lo construimos con un componente nuestro).
- Algunas características avanzadas (timers complejos, compensaciones) no
  están y se postergan.

### Neutras
- En el futuro, si el sistema crece, el motor se podría reemplazar por Camunda
  manteniendo `WorkflowEnginePort` igual; los consumidores no se enterarían.
```

### ADR-005: CQRS-light

```markdown
# ADR-005 — Separar puerto de comandos y de consultas en workflow

**Fecha:** 2026-04-26
**Estado:** aceptado

## Contexto
El componente `workflow` expone tanto operaciones que mutan estado (iniciar,
completar, derivar) como operaciones que leen (estado del trámite, mis trámites,
línea de tiempo). Mezclarlas en una sola interfaz `WorkflowPort` con muchos
métodos diluye la responsabilidad.

## Decisión
Definimos dos interfaces:
- `WorkflowEnginePort` — comandos que mutan estado.
- `TramiteQueryPort` — consultas de solo lectura.

## Alternativas consideradas
- **Un único `WorkflowPort` con todo dentro:** rechazada por baja cohesión.
- **CQRS completo con event sourcing y proyecciones separadas:** sobrediseño
  para el alcance actual.

## Consecuencias

### Positivas
- Cada Port tiene una responsabilidad clara.
- Los controllers que solo consultan (la app móvil, el dashboard) pueden depender
  solo de `TramiteQueryPort`, sin acceso a operaciones de mando.
- Tests más simples (mockear lecturas o escrituras independientemente).

### Negativas
- Dos interfaces en lugar de una; mínimo overhead.
```

---

## 5. Crear el resto

Continuar con la misma plantilla para:

- `ADR-003-shared-kernel.md` — por qué hay un componente `shared` y qué entra ahí
- `ADR-006-features-por-dominio.md` — por qué Angular y Flutter usan `features/<dominio>` en lugar de `admin/`/`funcionario/`
- `ADR-007-ui-kit-propio.md` — por qué no PrimeNG/Material completos, sino componentes propios
- `ADR-008-estado-utils-puros.md` — por qué `colorEstadoTramite()` es función pura en `core/utils/`, no método de servicio

---

## 6. Pasos

### Paso A — Crear plantilla
Guardar el bloque del punto 3 como `fase4/adr/_PLANTILLA.md`.

### Paso B — Escribir los ADRs uno a uno
Para cada uno, partir de la plantilla y completar las 4 secciones.
Mantener cada ADR **en una página** (~250 palabras). No es ensayo.

### Paso C — Guardar
- `fase4/adr/ADR-001-componentes-vs-capas.md`
- `fase4/adr/ADR-002-puertos-y-adaptadores.md`
- `fase4/adr/ADR-003-shared-kernel.md`
- `fase4/adr/ADR-004-no-camunda.md`
- `fase4/adr/ADR-005-cqrs-light-workflow.md`
- `fase4/adr/ADR-006-features-por-dominio.md`
- `fase4/adr/ADR-007-ui-kit-propio.md`
- `fase4/adr/ADR-008-estado-utils-puros.md`

### Paso D — Crear índice

`fase4/adr/README.md`:

```markdown
# Architectural Decision Records

| # | Título | Estado |
|---|--------|--------|
| 001 | Componentes vs Capas | aceptado |
| 002 | Puertos y Adaptadores | aceptado |
| 003 | Shared kernel | aceptado |
| 004 | No Camunda — motor propio | aceptado |
| 005 | CQRS-light en workflow | aceptado |
| 006 | Features por dominio en frontend | aceptado |
| 007 | UI Kit propio | aceptado |
| 008 | Estado en utils puros | aceptado |
```

---

## 7. Commit

```bash
git add fase4/adr/
git commit -m "docs(arquitectura): ADRs de las decisiones clave"
```

---

## Próximo paso

Continuar con **`07_documento_final.md`**.
