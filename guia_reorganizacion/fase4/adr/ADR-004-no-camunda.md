# ADR-004 — Implementar motor de workflow propio en lugar de Camunda 7

**Fecha:** 2026-04-26
**Estado:** aceptado
**Decidido por:** equipo del proyecto

---

## Contexto

El sistema requiere un motor de workflow que ejecute **diagramas UML 2.5 con swimlanes**, soportando flujos lineales, paralelos (fork/join), condicionales (decision) e iterativos (vuelta al nodo anterior). Existen motores listos: Camunda 7, Flowable, JBPM, Activiti.

Ante el ahorro de tiempo que implicaría adoptar uno, la pregunta natural es: **¿conviene usarlo?**

El enunciado aclara textualmente:
> *"La IA es una herramienta de apoyo, no el núcleo. El profesor evaluará que el software es un resultado de ingeniería, no simplemente IA generando código. Deben poder demostrar y modificar cualquier parte en vivo."*

El espíritu se aplica también a librerías "todopoderosas". El motor de workflow **es** lo que el examen evalúa. Importarlo equivale a no haberlo implementado.

## Decisión

Implementamos un motor propio (`WorkflowEnginePort` + `WorkflowEngineServiceImpl`) que:
- Carga un diagrama (nodos + transiciones) creado por el admin.
- Avanza el trámite nodo por nodo según el tipo de cada uno (inicio, actividad, decisión, fork, join, fin).
- Notifica, registra trazabilidad y mide tiempos vía los Ports correspondientes.

## Alternativas consideradas

- **Camunda 7 + bpmn-js:** habría reemplazado ~60% del backend y todo el editor visual. Rechazada porque:
  1. Notación BPMN ≠ UML 2.5 (el profesor pidió UML 2.5).
  2. Demostraría integración, no diseño.
  3. El motor es exactamente la pieza evaluada.

- **Flowable:** mismas razones que Camunda; además mayor curva de aprendizaje.

- **JBPM:** descartado por peso del stack Drools.

## Consecuencias

### Positivas
- Demostramos comprensión y control total del flujo de ejecución.
- Sin dependencias pesadas adicionales en el classpath.
- Notación UML 2.5 propia, no obligados a BPMN.
- Modelo de datos en MongoDB simple y a la medida.
- El motor es **el componente que se va a presentar y defender** en el examen.

### Negativas
- No tenemos editor visual gratis; lo construimos como componente propio.
- Algunas características avanzadas (timers complejos, compensaciones, sub-procesos jerárquicos) no están y se postergan.

### Neutras / a observar
- En el futuro, si el sistema crece y se necesitan features de un motor maduro, el motor se podría reemplazar por Camunda **manteniendo `WorkflowEnginePort` igual**; los consumidores no se enterarían. Esa es la promesa de los Ports.

## Referencias

- ADR-002 — Puertos y Adaptadores
- `modules/workflow/internal/WorkflowEngineServiceImpl.java` — la implementación propia
- Enunciado del examen, sección 6 ("la IA es herramienta de apoyo, no el núcleo")
