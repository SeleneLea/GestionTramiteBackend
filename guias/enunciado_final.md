# Sistema de Gestión de Trámites — Enunciado Final

**Primer Examen Parcial · Ingeniería de Software · Motor Workflow + UML 2.5**

> ⏰ Entrega PDF Moodle: 29 de abril hasta las 8:00 AM | Presentación: 28 de abril | Duración: 20 minutos

---

## 1. Descripción general del sistema

- Sistema web + móvil para la **gestión de trámites de principio a fin**, inspirado en empresas de servicios como CRE (Comisión de Regulación de Energía).
- Un usuario puede solicitar diferentes tipos de trámites. El sistema los encamina automáticamente por los departamentos correspondientes hasta su resolución.
- El comportamiento es similar a un **workflow (flujo de trabajo)**: cada trámite sigue un proceso definido por políticas de negocio configurables.
- El sistema es **completamente flexible y adaptable**: no tiene flujos predefinidos. Cada organización crea sus propias políticas y flujos desde cero.
- Canales de acceso: **app móvil Flutter (cliente final)** para seguimiento de trámites y notificaciones push, y **plataforma web Angular (administradores y funcionarios)** para gestión, diseño de flujos y ejecución.

> 💡 **Analogía clave para la presentación:** el sistema funciona como una "fábrica de trámites" donde cada producto (trámite) recorre una línea de ensamblaje (workflow) diferente según su tipo.

---

## 2. Motor de Workflow — Núcleo del sistema

El motor de workflow es la pieza central. Interpreta y ejecuta **diagramas de actividad UML 2.5 con swimlanes (calles)**. Cada calle representa un departamento o actor del proceso.

### Elementos del motor

| Elemento | Descripción | Ejemplo en CRE |
|----------|-------------|----------------|
| **Actividad** | Tarea concreta que realiza un actor dentro de una calle | "Revisar documentos del cliente" |
| **Flujo** | Conexión entre actividades que indica el orden de ejecución | Flecha de "Atención al cliente" → "Área técnica" |
| **Estado** | Condición actual del trámite en el proceso | Pendiente / En proceso / Aprobado / Rechazado |
| **Transición** | Cambio de estado disparado por completar una actividad | Al aprobar el área técnica → pasa a legal |
| **Fork / Join** | Separación y unión de flujos para paralelismo | Inspección técnica + papeleo corren al mismo tiempo |
| **Decision node** | Punto de ramificación condicional | ¿Documentación completa? Sí → continúa / No → devuelve |

### Tipos de flujo soportados

- **Lineal / secuencial** → Actividades en orden fijo: A → B → C → D
- **Condicional / alternativo** → Según condición, el flujo toma un camino u otro (decision node)
- **Iterativo (ida y vuelta)** → Permite regresar a pasos anteriores (ej: observaciones que vuelven a revisión)
- **Paralelo** → Fork → actividades simultáneas → Join (se espera que todas terminen)

> ⚠️ **Punto crítico para el examen:** el diagrama de actividad con swimlanes es el modelo central que debes presentar. Debe estar elaborado en herramienta CASE (Enterprise Architect) con notación UML 2.5 correcta.

---

## 3. Expediente digital único por trámite — Secciones por nodo

Cada trámite tiene un **único expediente digital** que se va completando por secciones conforme avanza por los departamentos. Cada nodo/departamento tiene su propia sección dentro de ese expediente.

### Estructura del expediente

| Sección | Departamento | Estado | Quién puede editar |
|---------|-------------|--------|-------------------|
| Sección 1 | Atención al cliente | ✅ Completado | Bloqueada — solo lectura para todos |
| Sección 2 | Área técnica | ✍️ En curso | Solo el funcionario del nodo activo |
| Sección 3 | Área legal | 🔒 Pendiente | Bloqueada hasta que llegue el turno |
| Sección 4 | Aprobación final | 🔒 Pendiente | Bloqueada hasta que llegue el turno |

### Comportamiento clave

- El administrador diseña el formulario de **cada sección** al construir el flujo: define qué campos tendrá cada departamento en el expediente.
- Cuando el trámite llega a un nodo, el funcionario ve el expediente completo pero **solo puede editar su sección activa**. Las secciones anteriores quedan visibles como referencia (contexto) pero bloqueadas.
- Funcionalidad de **voz a sección** (IA): el funcionario dicta por voz y la IA transcribe rellenando los campos de **su sección activa** únicamente. Luego puede editar manualmente.
- Los campos pueden ser: texto libre, selección, fecha, adjuntos (documentos, imágenes) y campos calculados.
- Al completar y guardar su sección, el motor avanza el trámite automáticamente al siguiente nodo.

### Ventajas del expediente único

- El funcionario de área técnica puede ver lo que registró atención al cliente antes, dándole **contexto completo** sin abrir otros módulos.
- El administrador ve un **solo documento cohesionado** por trámite, no múltiples informes sueltos.
- La trazabilidad es natural: un expediente = un trámite, con todas las secciones, sus timestamps y sus autores.

> 💡 **Ejemplo CRE:** Sección 1 (Atención al cliente): datos del solicitante y documentos. Sección 2 (Técnica): resultado de inspección + fotos. Sección 3 (Legal): validación del contrato. Sección 4 (Operaciones): fecha de ejecución y cierre.

---

## 4. Roles del sistema

### Administrador
- Registra departamentos y actividades.
- Diseña los flujos de trabajo (swimlane diagrams) mediante interfaz visual o prompts con IA.
- Define políticas de negocio: cada política es un flujo completo diferente.
- Configura el formulario de cada sección del expediente por nodo.
- Configura reglas de notificación y tiempos límite por actividad.
- Accede a dashboards de monitoreo y reportes de cuellos de botella.

### Funcionario
- Recibe una solicitud y selecciona la política de negocio aplicable.
- El sistema dirige el trámite automáticamente por el flujo definido.
- Solo ejecuta las actividades que le corresponden según su calle (departamento).
- Completa su sección activa del expediente (por voz o manualmente) desde la **plataforma web**.
- No puede crear actividades ni modificar flujos.
- Recibe notificaciones en la plataforma web cuando un trámite llega a su área.

### Cliente
- Inicia trámites desde la **app móvil Flutter**.
- Consulta el estado y línea de tiempo de sus trámites en tiempo real.
- Recibe notificaciones push cuando su trámite cambia de estado.
- Puede cancelar trámites activos.

---

## 5. Automatización de estados y ciclo de vida

### Estados del trámite

`Nuevo` → `En proceso` → `Derivado` → `Observado` → `Rechazado` / `Aprobado / Cerrado`

- Los estados se actualizan **automáticamente** al completar cada actividad: no requiere intervención manual del administrador.
- Al completar la sección de un nodo, el motor evalúa el siguiente paso según el flujo definido y deriva el trámite automáticamente.
- Si hay un decision node, el motor evalúa la condición y toma el camino correcto.
- Si el flujo es paralelo, el motor espera que todos los nodos del join estén completos antes de continuar.
- Si hay un flujo iterativo (observación), el motor retrocede el trámite al nodo indicado.

---

## 6. Módulo de IA (FastAPI + Python)

| Funcionalidad | Descripción | Quién la usa |
|---------------|-------------|--------------|
| **Creación de flujos por prompt** | El administrador describe el proceso en texto y la IA genera el diagrama de actividad automáticamente. | Administrador |
| **Voz a sección del expediente** | El funcionario dicta por voz; la IA transcribe y rellena los campos de **su sección activa** en el expediente. No toca las secciones de otros departamentos. El funcionario puede editar antes de guardar. | Funcionario |
| **Análisis de cuellos de botella** | Analiza tiempos históricos por actividad y departamento. Identifica automáticamente dónde se acumulan los trámites. | Administrador |
| **Consulta del cliente (app móvil)** | El cliente consulta el estado y la línea de tiempo de su trámite desde la app Flutter. Ve en qué departamento está, cuánto lleva y qué falta. | Cliente (Flutter) |
| **Agente de asistencia (CU-31)** | Agente conversacional contextual que detecta en tiempo real en qué módulo está el usuario y responde preguntas, guía acciones y sugiere pasos. Implementado con n8n + RAG sobre la documentación real del sistema. Panel flotante en todas las pantallas. (Parte 3 del documento) | Todos los usuarios |

> ℹ️ **Importante:** La IA es una herramienta de apoyo, no el núcleo. El profesor evaluará que el software es un resultado de ingeniería, no simplemente IA generando código. Deben poder demostrar y modificar cualquier parte en vivo.

---

## 7. Monitoreo, notificaciones y colaboración

### Monitoreo
- Dashboard con métricas en tiempo real: trámites activos, pausados, completados por período.
- Tiempo promedio de atención por actividad, por departamento y por política de negocio.
- Detección automática de cuellos de botella: actividades que superan el tiempo límite configurado.
- Identificación de los departamentos con mayor carga de trabajo en un período.

### Notificaciones push (app móvil Flutter — solo cliente final)
- Notificación push al **cliente** cuando su trámite cambia de estado (Aprobado, Rechazado, Observado, etc.).
- Alerta push cuando el sistema requiere una acción del cliente (ej. subsanar documentos).
- Notificaciones internas en la **plataforma web** para funcionarios cuando un trámite llega a su área.
- Alerta web al funcionario si un trámite asignado supera el tiempo límite definido en la política.

### Diseño colaborativo de diagramas (online)
- El **administrador es quien crea el diagrama** y tiene control total sobre el diseño del workflow.
- El administrador puede **enviar una solicitud de colaboración** a otros administradores o funcionarios para que participen en el diseño del diagrama.
- Los colaboradores invitados pueden editar, sugerir cambios o agregar nodos en tiempo real sobre el mismo lienzo, similar a un editor online colaborativo (tipo Figma o Google Docs).
- Historial de versiones del diagrama: quién modificó qué nodo y cuándo.

---

## 8. Ejemplo de flujo real — Solicitud de nueva conexión eléctrica (CRE)

**Política de negocio: "Nueva conexión residencial"**

```
Cliente → Solicita conexión → Atención al cliente → Verifica documentos
                                                           ↓
                                                    Área técnica
                                                    ├── Inspección en campo (paralelo)
                                                    └── Presupuesto (paralelo)
                                                           ↓ Join: ambas OK
                                                    Área legal → Revisar contrato
                                                    ├── Sí → Cierre y conexión
                                                    └── No → Vuelve a técnica
```

| Calle (swimlane) | Actor / Departamento | Sección en el expediente | Tipo de flujo |
|-----------------|---------------------|--------------------------|---------------|
| Calle 1 | Atención al cliente | Sección 1: datos del solicitante, documentos adjuntos, verificación | Lineal |
| Calle 2 | Área técnica | Sección 2a: resultado inspección + fotos / Sección 2b: presupuesto (paralelas) | Paralelo |
| Calle 3 | Área legal | Sección 3: validación de contrato; puede devolver con observaciones | Condicional + Iterativo |
| Calle 4 | Operaciones | Sección 4: fecha de ejecución, cierre y firma | Lineal |

> 💡 **Para el examen:** mostrar el diagrama de actividad UML 2.5 de este flujo en Enterprise Architect y el sistema ejecutándolo en vivo (el trámite moviéndose entre calles automáticamente).

---

## 9. Gestión de configuración

- El administrador registra: **Departamentos**, **Actividades**, **Usuarios**, **Políticas de negocio**.
- Una organización puede tener **múltiples políticas de negocio**, cada una con su propio flujo completamente diferente.
- No hay flujos predefinidos: todo se crea desde cero por el administrador, lo que hace el sistema **100% flexible**.
- Un flujo puede reutilizar actividades ya definidas en otros flujos (principio de componentes reutilizables).
- Los flujos se diseñan mediante: (a) interfaz visual drag-and-drop de swimlanes colaborativa (online), o (b) descripción en texto procesada por IA.

---

## 10. CU-31 — Interactuar con el agente de asistencia (Parte 3)

Mecanismo principal para que cualquier usuario aprenda a usar el sistema con el menor esfuerzo posible. El agente detecta automáticamente el contexto del usuario y responde de forma específica a lo que está haciendo en ese momento.

### Ficha del caso de uso

| Campo | Detalle |
|-------|---------|
| **Ciclo** | Ciclo 3 |
| **Actores** | Cliente (app Flutter) · Funcionario (web) · Administrador (web) |
| **Propósito** | Proporcionar un agente conversacional inteligente que detecte en tiempo real qué está haciendo el usuario dentro de la plataforma y responda preguntas, guíe acciones y sugiera los siguientes pasos de forma contextual, reduciendo al mínimo el tiempo de aprendizaje sin necesidad de consultar manuales. |
| **Implementación** | n8n + RAG (Retrieval Augmented Generation) sobre la documentación real del sistema. El agente no usa conocimiento genérico de IA, sino los flujos configurados, políticas activas y CU definidos del propio proyecto. |
| **Disponibilidad** | Panel flotante accesible desde cualquier pantalla del sistema (web y móvil). |

### Precondiciones
- El usuario debe estar autenticado en el sistema.
- El microservicio del agente (FastAPI) debe estar activo y conectado.
- El agente debe tener acceso al contexto de navegación actual: módulo activo, rol del usuario, trámite en curso si aplica, y acciones ejecutadas recientemente.

### Flujo principal

| Paso | Acción |
|------|--------|
| 1 | El usuario abre el panel del agente (ícono flotante disponible en todas las pantallas). |
| 2 | El agente detecta automáticamente el contexto: módulo activo, rol, trámite en curso si aplica. |
| 3 | El agente muestra un saludo contextual. Ej: *"Estás en diseño de flujos. ¿Necesitas ayuda para agregar un nodo de decisión?"* |
| 4 | El usuario escribe o dicta su consulta en lenguaje natural. |
| 5 | El agente envía la consulta + contexto de navegación al microservicio FastAPI. |
| 6 | El microservicio consulta la base de conocimiento (documentación, políticas activas, estado del trámite) y genera la respuesta. |
| 7 | El agente muestra la respuesta y, si aplica, un botón de acción directa. Ej: *"Ir a ese módulo"*, *"Completar este campo"*. |
| 8 | El usuario puede continuar la conversación con preguntas de seguimiento. |
| 9 | El sistema registra la interacción en el log para mejorar respuestas futuras. |

### Excepciones
- **Consulta fuera de alcance:** Si la pregunta no tiene relación con el sistema, el agente responde que solo asiste con funcionalidades de la plataforma.
- **Microservicio caído:** Muestra "Asistente no disponible temporalmente" y ofrece el enlace al manual de usuario como alternativa.
- **Contexto no disponible:** Si no puede determinar la pantalla actual del usuario, responde de forma genérica pero funcional.
- **Rol incorrecto:** Si un funcionario pregunta por funcionalidades de administrador, el agente informa que esa función es exclusiva del rol administrador.

### Post condiciones
- El usuario recibe orientación suficiente para completar su tarea sin abandonar la plataforma.
- No se modifica ningún dato del sistema — es operación de solo lectura sobre el contexto.
- La interacción queda registrada en el log del agente.

> 💡 **Lo que diferencia este agente de un chatbot genérico:** (1) detecta el contexto automáticamente sin que el usuario explique dónde está, (2) responde basado en la documentación real del sistema, no en conocimiento general de IA, y (3) ofrece botones de acción directa para que el usuario no tenga que navegar solo.

---

*Primer Examen Parcial — Sistema de Gestión de Trámites · Luis David Guzmán Rojas · Ingeniería de Software*
