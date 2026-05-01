# Fase 0 · Estrategia general

> Cómo vamos a trabajar para llegar de **monolito por capas** a **arquitectura por componentes** sin romper lo que ya funciona.

---

## 1. Premisa fundamental

> **No se reescribe nada desde cero. Se reorganiza y se le ponen interfaces a lo que ya existe.**

El motor de workflow, el JWT, los CRUDs, las notificaciones, los expedientes — todo eso ya funciona y tiene bugs depurados. Reescribir = perder esa madurez. Reorganizar = mantenerla y ganar arquitectura.

---

## 2. Enfoque: incremental, no *big bang*

| ❌ Big bang | ✅ Incremental (lo que vamos a hacer) |
|---|---|
| Refactorizar todo a la vez | Un componente a la vez |
| App rota durante días | App siempre ejecutable |
| Imposible saber qué rompió qué | Cada paso es verificable |
| Pánico final cerca del examen | Progreso medible diario |

Después de cada paso de refactorización, la app **sigue compilando y corriendo**. Si algo se rompe, sabemos que fue ese paso.

---

## 3. Cómo se trabaja cada componente

Para cada componente que extraigamos seguimos siempre la misma receta:

### Paso A — Inventario
Listar qué clases/archivos pertenecen al componente y qué dependencias tienen hacia afuera.

### Paso B — Identificar la frontera
Definir qué cosas del componente son **públicas** (otros módulos las pueden usar) y cuáles son **internas** (no deben salir del componente).

### Paso C — Extraer la interfaz (puerto)
Crear una interfaz `*Port` que represente lo que el componente ofrece al resto del sistema.

### Paso D — Mover archivos
Reorganizar los archivos en su nuevo paquete bounded context.

### Paso E — Apuntar dependencias a la interfaz
Donde antes se inyectaba la clase concreta, ahora se inyecta la interfaz.

### Paso F — Verificar
Compilar, correr la app, probar el flujo manualmente.

### Paso G — Documentar
Anotar en este directorio qué quedó público y qué quedó interno.

---

## 4. Qué SÍ y qué NO usamos de librerías externas

Ya discutimos que el espíritu del enunciado es que **nuestro código** se vuelva componentes, no que importemos componentes ajenos para reemplazar el dominio.

### ✅ Librerías permitidas (commodity, no compiten con la nota)
- Spring Boot, Spring Security, Spring Data — ya son la base
- Bootstrap / Tailwind — solo CSS
- `timeline_tile` Flutter — ya en uso
- JWT (jjwt) — auth estándar

### ❌ Librerías que NO usaremos
- **Camunda / Flowable / JBPM** — reemplazarían nuestro motor (lo que el profe evalúa)
- **bpmn-js / JointJS / GoJS** — reemplazarían el editor de diagramas
- **PrimeNG / Angular Material** completos — reemplazarían nuestro UI kit propio

### ⚖️ Caso por caso
- **Spring Modulith** — ayuda a documentar componentes pero NO reemplaza nuestra lógica → *evaluar si suma o estorba*
- **ArchUnit** — solo tests de arquitectura → *suma sin reemplazar nada*

---

## 5. Definition of Done (DoD) para cada componente

Un componente se considera **terminado** cuando:

- [ ] Tiene una interfaz pública (`*Port`) bien nombrada
- [ ] Su implementación (`*Adapter` o `*ServiceImpl`) está aislada
- [ ] No exporta clases internas al resto del sistema
- [ ] Otros módulos lo consumen **solo** por su interfaz
- [ ] Tiene un README corto en su carpeta explicando: qué hace, qué expone, qué consume
- [ ] El proyecto compila y la app corre con el componente en su lugar nuevo

---

## 6. Dónde se documenta cada decisión

| Tipo de decisión | Dónde queda registrada |
|---|---|
| Por qué se eligió este corte de componentes | `fase1/decisiones_arquitectonicas.md` |
| Qué interfaces tiene cada componente | README dentro del paquete del componente |
| Cambios que rompen contrato público | `CHANGELOG.md` del componente |
| Diagramas UML del antes/después | `fase4/diagramas/` |

---

## 7. Manejo del riesgo de tiempo

Si en algún punto del cronograma vemos que no llegamos:

1. **Prioridad 1:** Backend en bounded contexts + diagrama de componentes UML (esto es lo que el profe evalúa más fuerte).
2. **Prioridad 2:** Angular `ui-kit` con 3-5 componentes reutilizables clave.
3. **Prioridad 3:** Flutter widgets reutilizables.
4. **Prioridad 4:** ArchUnit tests.

Las prioridades 3 y 4 son "nice to have" — si no llegan, no perdemos lo esencial.

---

## 8. Cómo medimos avance

Cada commit debe responder a UNA de estas tres categorías:

- **`refactor:`** mover/reorganizar sin cambiar comportamiento
- **`feat:`** agregar interfaz/componente nuevo
- **`docs:`** documentación de la guía

Si un commit no encaja en ninguno → es probable que estemos haciendo algo que no toca en esta refactorización.

---

## Próximo paso

Leer **`01_principios.md`** para entender los 6 principios que guían cada decisión técnica que tomemos.
