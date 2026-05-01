# Fase 1.0 · Preparación previa

> Checklist obligatorio **antes** de mover una sola línea de código. Si te saltas esto, vas a tener problemas a mitad del refactor.

---

## 1. Backup y control de versiones

### 1.1 Asegurar que todo está commiteado
```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/Backend"
git status
```
Si hay cambios sin commitear, hacer commit primero.

### 1.2 Crear rama dedicada al refactor
```bash
git checkout -b refactor/componentes-backend
```

> **Nota:** todo el refactor se hace en esta rama. Solo se mergea a `main` cuando la fase 1 esté completa y verificada.

### 1.3 Tag del estado actual (por si necesitas volver atrás)
```bash
git tag pre-refactor-fase1
```

---

## 2. Verificar que el proyecto compila y corre HOY

Antes de cualquier cambio, hay que tener un **punto de partida verde**.

### 2.1 Compilar
```bash
./mvnw clean compile
```
Debe terminar en `BUILD SUCCESS`.

### 2.2 Levantar la app
```bash
./mvnw spring-boot:run
```
Debe arrancar sin errores. Verificar que:
- MongoDB está corriendo (`docker ps` o servicio local)
- El puerto 8080 queda escuchando
- `http://localhost:8080/api/health` responde 200

### 2.3 Smoke test manual rápido
- Login con un usuario admin → obtiene JWT
- GET `/api/departamentos` → 200 con lista
- GET `/api/diagramas` → 200 con lista

Si algo de esto falla **ahora**, no es momento de empezar el refactor. Arreglar primero.

---

## 3. Inventario de pruebas manuales clave

Lista de flujos que verificaremos después de cada subfase. Anotar tiempos para que sea rápido.

| # | Flujo | Endpoint clave | Esperado |
|---|-------|----------------|----------|
| 1 | Login admin | POST `/api/auth/login` | 200 + token |
| 2 | Listar departamentos | GET `/api/departamentos` | 200 + array |
| 3 | Crear departamento | POST `/api/departamentos` | 201 |
| 4 | Listar diagramas | GET `/api/diagramas` | 200 |
| 5 | Iniciar trámite | POST `/api/tramites/iniciar` | 201 + Tramite |
| 6 | Estado de trámite | GET `/api/tramites/{id}/estado` | 200 + estado completo |
| 7 | Listar mis trámites | GET `/api/tramites/mis-tramites` | 200 |
| 8 | Listar pendientes funcionario | GET `/api/tramites/mis-pendientes` | 200 |
| 9 | Generar diagrama por prompt | POST `/api/workflow-design/from-prompt` | 201 |
| 10 | Mis notificaciones | GET `/api/notificaciones/mis-notificaciones` | 200 |

> **Recomendación:** crear una collection en Postman/Bruno con estas 10 requests. Después de cada subfase, ejecutar la collection completa.

---

## 4. Crear la nueva estructura raíz vacía

Aún sin mover nada, vamos a crear los **paquetes destino** vacíos para que IntelliJ tenga adónde mover:

```
src/main/java/com/example/demo/
├── (lo de hoy se mantiene intacto)
└── modules/                        ← NUEVO, contendrá todos los componentes
    ├── shared/                     ← se llenará en 1.1
    │   ├── api/
    │   └── internal/
    ├── auth/                       ← se llenará en 1.2
    ├── notificaciones/             ← se llenará en 1.3
    ├── trazabilidad/               ← se llenará en 1.4
    ├── metricas/                   ← se llenará en 1.5
    ├── expediente/                 ← se llenará en 1.6
    ├── workflowdesign/             ← se llenará en 1.7
    ├── workflow/                   ← se llenará en 1.8
    ├── catalogo/                   ← se llenará en 1.9
    ├── aiintegration/              ← se llenará en 1.10
    └── reportes/                   ← se llenará en 1.10
```

> **Decisión de naming:** evitamos guiones medios en nombres de paquete Java (`workflow-design` no es válido). Usamos `workflowdesign`, `aiintegration`. Y mantenemos `com.example.demo` como raíz para no obligar a renombrar todo el classpath ahora — eso se podrá hacer al final si queda tiempo.

### Crear los paquetes vacíos

En IntelliJ: click derecho en `com.example.demo` → New → Package → repetir por cada uno.
O por terminal:

```bash
cd "src/main/java/com/example/demo"
mkdir -p modules/shared/api modules/shared/internal
mkdir -p modules/auth/api modules/auth/internal modules/auth/domain
mkdir -p modules/notificaciones/api modules/notificaciones/internal modules/notificaciones/domain
mkdir -p modules/trazabilidad/api modules/trazabilidad/internal modules/trazabilidad/domain
mkdir -p modules/metricas/api modules/metricas/internal modules/metricas/domain
mkdir -p modules/expediente/api modules/expediente/internal modules/expediente/domain
mkdir -p modules/workflowdesign/api modules/workflowdesign/internal modules/workflowdesign/domain
mkdir -p modules/workflow/api modules/workflow/internal modules/workflow/domain
mkdir -p modules/catalogo/api modules/catalogo/internal modules/catalogo/domain
mkdir -p modules/aiintegration/api modules/aiintegration/internal modules/aiintegration/domain
mkdir -p modules/reportes/api modules/reportes/internal modules/reportes/domain
```

---

## 5. Plantilla de `package-info.java`

Cada componente tendrá un `package-info.java` que documenta su propósito. Plantilla:

```java
/**
 * Componente: <NOMBRE>
 *
 * Propósito:
 *   <una frase explicando qué hace>
 *
 * Puerto público:
 *   - <Nombre>Port — <qué expone>
 *
 * Consume:
 *   - <Otro>Port (vía interfaz)
 *
 * Es consumido por:
 *   - <Otro componente>
 *
 * Colecciones MongoDB:
 *   - <colección 1>
 *   - <colección 2>
 */
package com.example.demo.modules.<nombre>;
```

---

## 6. Plantilla de README por componente

Crear como `src/main/java/com/example/demo/modules/<nombre>/README.md`:

```markdown
# Componente: <nombre>

## Propósito
<descripción de 1-2 líneas>

## Puerto público
`<Nombre>Port` — operaciones expuestas al resto del sistema:
- `metodo1(params)` — qué hace
- `metodo2(params)` — qué hace

## Consume
- `<OtroPort>` — para qué

## Es consumido por
- `<otro-componente>`

## Colecciones Mongo
- `nombre_coleccion`

## Notas
<si hay decisiones técnicas que aclarar>
```

---

## 7. Confirmación antes de seguir

Antes de pasar a `01_shared_kernel.md`, verificar:

- [ ] Rama `refactor/componentes-backend` creada
- [ ] Tag `pre-refactor-fase1` puesto
- [ ] App compila y arranca
- [ ] Los 10 smoke tests pasan
- [ ] Estructura `modules/` creada y vacía
- [ ] Plantillas listas para copiar

Si todos los checks están ✅, **commitear el "punto cero"**:

```bash
git add .
git commit -m "chore: estructura modules/ vacía como base para refactor a componentes"
```

---

## Próximo paso

Continuar con **`01_shared_kernel.md`**.
