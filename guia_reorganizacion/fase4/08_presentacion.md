# Fase 4.8 · Guion de presentación (20 min)

> El examen se presenta en 20 minutos. Aquí está el guion exacto, minuto por minuto, con qué mostrar y qué decir.

---

## 1. Reglas del juego

- **Duración:** 20 minutos
- **Audiencia:** profesor (técnico, evalúa con criterio de ingeniería)
- **Formato:** demo en vivo + diagramas + diálogo
- **Lo que el profe quiere ver:**
  1. Que la arquitectura es **diseñada**, no accidental.
  2. Que pueden **modificar cualquier parte en vivo**.
  3. Que entienden por qué tomaron cada decisión.

> Frase del enunciado: *"el profesor evaluará que el software es un resultado de ingeniería, no simplemente IA generando código. Deben poder demostrar y modificar cualquier parte en vivo."*

---

## 2. Distribución de tiempo

| Min | Bloque | Qué se muestra |
|-----|--------|----------------|
| 0-2 | Apertura | Problema y alcance |
| 2-5 | Diagrama de actividad UML 2.5 (CRE) | El flujo que el sistema ejecuta |
| 5-8 | Diagrama de componentes | La arquitectura backend |
| 8-11 | Demo: iniciar trámite + completar nodos | El motor en acción |
| 11-14 | Editor de diagramas + prompt IA | Diseño visual + IA |
| 14-16 | UI Kit reutilizable (Angular + Flutter) | Componentes propios |
| 16-18 | ArchUnit pasando | Verificación automática |
| 18-20 | Cierre + Q&A | Síntesis + preguntas |

---

## 3. Guion detallado

### 0-2 min · Apertura

**Slide 1 — Título + 1 línea de sistema**

Decir:
> *"Buenos días. Presentamos un sistema de gestión de trámites de principio a fin, inspirado en CRE. El sistema funciona como una fábrica de trámites: cada producto recorre una línea de ensamblaje diferente según su tipo. Lo que voy a mostrar hoy es **cómo el motor de workflow ejecuta esos flujos** y **cómo el sistema está arquitectado por componentes con interfaces explícitas**."*

---

### 2-5 min · Diagrama de actividad UML 2.5

**Mostrar:** el diagrama UML 2.5 de Enterprise Architect con swimlanes para "Nueva conexión residencial" (CRE).

Decir:
> *"Este es el diagrama de actividad UML 2.5 que sirve de **modelo central**. Tiene 4 calles (atención al cliente, técnica, legal, operaciones) y 4 tipos de flujo: lineal entre ATC y técnica, paralelo dentro de técnica con fork y join, condicional en legal con un decision node, e iterativo si legal devuelve."*

> *"Lo importante: este diagrama lo dibuja el administrador en nuestro editor visual o lo describe por prompt y la IA lo genera. **Y nuestro motor lo ejecuta literalmente** — moviendo el trámite por cada nodo automáticamente."*

---

### 5-8 min · Diagrama de componentes

**Mostrar:** `fase4/diagramas/componentes.png`

Decir:
> *"El backend está dividido en **9 componentes de negocio** más un shared kernel. Cada componente expone una interfaz `Port` y los demás solo lo consumen por esa interfaz."*

> *"El **componente núcleo es workflow**. Aquí pueden ver que consume seis Ports: WorkflowDesign para leer los diagramas, Expediente para gestionar las secciones, Notificación para avisar a clientes y funcionarios, Trazabilidad para auditar con hash chain, Métricas para medir tiempos, y Catálogo para validar políticas y departamentos."*

> *"Esto significa que **cualquier componente se puede sustituir** sin afectar a los demás. Si mañana cambiamos cómo se notifica de web a email, solo cambia un adaptador. El motor no se entera."*

---

### 8-11 min · Demo en vivo: iniciar y completar trámites

**Pantalla:** App Flutter (cliente) + web Angular (funcionario).

Decir mientras se demuestra:
> *"Como cliente, inicio un trámite seleccionando una política. Pueden ver que aparece la card con su código, badge de estado 'En proceso' y barra de progreso."*

(cambiar al admin/funcionario en web)

> *"Aquí el funcionario de Atención al Cliente recibe el trámite en su bandeja. Completa la sección y lo libera."*

(volver al móvil)

> *"El cliente recibe la notificación de que su trámite avanzó de etapa. El motor automáticamente movió el trámite al siguiente nodo según el diagrama."*

> *"Cada paso queda registrado en trazabilidad con hash chain SHA-256 — ningún cambio puede ser borrado o adulterado sin romper la cadena."*

---

### 11-14 min · Editor de diagramas + prompt IA

**Pantalla:** `/diagramas` en Angular admin.

Decir:
> *"El administrador diseña los flujos en este editor visual. Puede crear nodos, transiciones, swimlanes, decisiones, paralelos."*

(abrir `/workflow-design/from-prompt`)

> *"Pero también puede **describir el flujo en lenguaje natural** y la IA lo genera. Por ejemplo:"*

(escribir): *"Atención al Cliente recibe la solicitud, luego TEC hace inspección en paralelo con LEG que revisa deuda, finalmente OPE cierra"*

> *"En segundos genera un diagrama con 4 actividades, fork-join paralelo y los swimlanes correctos. **La IA es herramienta de apoyo**, no el motor — el motor lo construimos nosotros, y eso es lo importante."*

---

### 14-16 min · UI Kit reutilizable

**Pantalla:** estructura del Angular y Flutter.

Decir:
> *"En frontend aplicamos el mismo principio de componentes. Tenemos un **UI Kit propio** que se reutiliza entre features."*

(mostrar `<app-card-tramite>` usado en lista, bandeja y detalle)

> *"Esta misma card aparece en tres pantallas distintas. Si cambio el badge de estado, cambia en todas. Lógica de color y progreso efectivo viven en `core/utils` como funciones puras."*

(mostrar `widgets/` en Flutter)

> *"En Flutter aplicamos exactamente el mismo patrón: `EstadoBadge`, `ProgresoBar`, `TramiteCard`, `TimelineCustom`. Y la lógica está en `core/utils/estado_utils.dart` — funciones puras testeables."*

---

### 16-18 min · ArchUnit pasando

**Pantalla:** IntelliJ con tests verdes.

Decir:
> *"Ahora la prueba de que la arquitectura es real, no solo un diagrama. Tenemos **9 reglas arquitectónicas verificadas con ArchUnit** que se ejecutan en cada build:"*

(leer 2-3 reglas):
> *"Por ejemplo: 'ningún componente puede tener ciclos con otros'. 'Lo que vive en `internal/` no puede ser accedido desde otro componente'. 'El dominio no puede depender de Spring'."*

(ejecutar `mvn test -Dtest=ArchitectureTest`):
> *"Si rompo cualquier regla, el build falla. La arquitectura no solo está documentada — está **enforced**."*

---

### 18-20 min · Cierre y Q&A

Decir:
> *"En síntesis: el sistema implementa un motor de workflow propio que ejecuta diagramas UML 2.5, expone su funcionalidad como Ports, está organizado en 9 componentes con interfaces explícitas, y la arquitectura se verifica automáticamente en cada build. La IA es herramienta de apoyo (generación de flujos por prompt, voz a sección, agente de asistencia) pero el núcleo de ingeniería es nuestro."*

> *"¿Tienen preguntas?"*

---

## 4. Preguntas previsibles del profesor (y respuestas preparadas)

**P1: ¿Por qué no usaron Camunda u otro motor existente?**
> *"Porque el motor es exactamente lo que el examen evalúa. Importarlo equivaldría a no haberlo implementado. Además, BPMN no es UML 2.5 — habríamos tenido que justificar la diferencia. Diseñamos uno propio que ejecuta nuestra notación."*
(Apuntar a ADR-004.)

**P2: ¿Qué pasa si quiero agregar un nuevo tipo de notificación, por ejemplo SMS?**
> *"Creo un nuevo `NotificacionSmsAdapter` que implementa `NotificacionPort`. No cambio nada del motor ni de los demás componentes. La inyección de dependencias se encarga del resto."*

**P3: ¿Cómo prueban que la arquitectura no se degrada con el tiempo?**
> *"Con ArchUnit. Cada regla está codificada como test JUnit. Si un compañero por error inyecta un `NotificacionService` concreto en lugar del Port, el build falla."*

**P4: ¿Por qué CQRS-light en workflow?**
> *"Porque las operaciones de mando (iniciar, completar, derivar) y las de consulta (estado, mis trámites) son responsabilidades distintas. Tener dos Ports — `WorkflowEnginePort` y `TramiteQueryPort` — permite que la app móvil dependa solo de las consultas, sin tener acceso a las operaciones de mando."*
(Apuntar a ADR-005.)

**P5: ¿Por qué no microservicios?**
> *"Porque sería sobrediseño para el tamaño actual. Pero la arquitectura por componentes con Ports es **un paso intermedio**: si mañana el sistema crece y necesitamos escalar el motor, podemos extraerlo como microservicio sin tocar a los consumidores."*

---

## 5. Checklist pre-presentación

Día antes:
- [ ] PDF del documento de arquitectura subido a Moodle
- [ ] Backend levantado en Docker o local (verificar)
- [ ] BD MongoDB con datos de seed
- [ ] Web Angular compilado y corriendo
- [ ] App Flutter en emulador/dispositivo listo
- [ ] Postman/Bruno con collection de smoke tests
- [ ] Diagramas exportados a PNG en alta resolución
- [ ] Slides preparadas (5-7 slides máximo)

Día de presentación:
- [ ] Cargador de laptop
- [ ] Adaptador HDMI / VGA
- [ ] Cable USB para el celular si la app no está en emulador
- [ ] El proyecto **abierto** en IntelliJ con los tests ArchUnit cargados
- [ ] La rama de trabajo en una sin cambios pendientes (limpia)
- [ ] Logueado como admin **y** como cliente listos en distintas ventanas

---

## 6. Lo que **NO** decir

- **No** disculparse por features incompletas; mostrar lo que sí está y mencionarlo como "siguiente iteración".
- **No** decir "lo hizo Claude/IA" — el profe ya sabe que se usa IA, lo que evalúa es que tú entiendas y puedas modificar.
- **No** entrar en detalles de configuración (CORS, ports, env vars) salvo que pregunte. El tiempo es escaso.

---

## 7. Lo que **SÍ** decir

- "El motor consume `<Port>`..." — siempre nombrar los Ports cuando se demuestre algo.
- "Esto está documentado en ADR-N" — referenciar las decisiones cuando aplique.
- "Si el profesor quiere, puedo modificar X en vivo" — ofrecer cuando se pueda.

---

## 8. Commit final

```bash
git add fase4/
git commit -m "docs(arquitectura): guion de presentacion + cierre fase 4"
git tag fin-fase4
```

---

## 9. Estado del proyecto al cerrar fase 4

- ✅ Backend componentizado (fase 1)
- ✅ Frontend Angular con UI Kit (fase 2)
- ✅ Mobile Flutter con Widget Kit (fase 3)
- ✅ Diagramas UML 2.5 de componentes, despliegue, capas, secuencias
- ✅ ArchUnit tests verificando arquitectura
- ✅ 8 ADRs documentando decisiones clave
- ✅ Documento PDF final
- ✅ Guion de presentación

**El proyecto está listo para defender.**

---

## Próximo paso

Ejecutar las fases 1-4 en código real y entregar.
