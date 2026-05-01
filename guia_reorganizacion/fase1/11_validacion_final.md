# Fase 1.11 · Validación final de la fase 1

> Cerrar la fase 1 verificando que todo está coherente, que la app sigue funcionando end-to-end, y que la arquitectura por componentes es real (no solo cosmética).

---

## 1. Objetivo

Antes de pasar a fase 2 (Angular), confirmar que el backend ya está completamente componentizado y que todo funciona como antes desde el punto de vista del cliente.

---

## 2. Checklist de estructura

### 2.1 Carpetas existentes

Confirmar que en `src/main/java/com/example/demo/modules/` están **todas** estas carpetas, cada una con sus 3 subcarpetas (`api/`, `internal/`, `domain/` cuando aplique) y sus archivos `package-info.java` + `README.md`:

- [ ] `shared/`
- [ ] `auth/`
- [ ] `notificaciones/`
- [ ] `trazabilidad/`
- [ ] `metricas/`
- [ ] `expediente/`
- [ ] `workflowdesign/`
- [ ] `workflow/`
- [ ] `catalogo/`
- [ ] `aiintegration/`
- [ ] `reportes/`

### 2.2 Carpetas legacy vacías

Confirmar que estas carpetas **viejas** ya están **vacías** (o eliminadas):

- [ ] `controllers/` — vacía
- [ ] `services/` — vacía
- [ ] `repositories/` — vacía
- [ ] `models/` — vacía
- [ ] `dto/` — vacía
- [ ] `security/` — vacía
- [ ] `config/` — vacía (todo movido a shared/auth)

Si alguna carpeta legacy tiene archivos, identificar a qué componente pertenecen y moverlos.

---

## 3. Verificación de Ports

Confirmar que cada componente expone **al menos** estos Ports:

| Componente | Ports esperados |
|------------|-----------------|
| auth | `AuthPort`, `JwtPort` |
| notificaciones | `NotificacionPort` |
| trazabilidad | `TrazabilidadPort` |
| metricas | `MetricasPort` |
| expediente | `ExpedientePort` |
| workflowdesign | `WorkflowDesignPort` |
| workflow | `WorkflowEnginePort`, `TramiteQueryPort` |
| catalogo | `CatalogoPort` |
| aiintegration | `VozPort`, `AgentePort` |
| reportes | `ReportesPort` |

---

## 4. Verificación de no-acoplamiento

Buscar en todo el proyecto patrones que indican acoplamiento incorrecto:

### 4.1 Búsqueda de acceso a repositorios ajenos

```bash
cd "src/main/java/com/example/demo/modules"
```

Para cada componente, buscar imports que apunten a `internal/` de otro componente:

```bash
grep -r "modules.workflow.internal" --include="*.java" . | grep -v "modules/workflow/"
grep -r "modules.expediente.internal" --include="*.java" . | grep -v "modules/expediente/"
# repetir por cada componente
```

**Esperado:** ningún resultado. Cada componente solo importa `api/` de los demás.

### 4.2 Búsqueda de inyecciones de implementaciones concretas

```bash
grep -r "@Autowired" --include="*.java" . | grep "ServiceImpl"
```

**Esperado:** ningún resultado. Las inyecciones deben ser por interfaz (`*Port`).

### 4.3 Verificación visual de package-private

Abrir cada `*ServiceImpl.java` en `internal/` y confirmar que la clase **no** es `public`:

```java
@Service
class WorkflowEngineServiceImpl implements WorkflowEnginePort {  // ✅ sin public
```

---

## 5. Verificación funcional end-to-end

### 5.1 Compilar y arrancar
```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### 5.2 Ejecutar la collection completa de Postman/Bruno

Correr los 10 flujos del punto 3 de `00_preparacion.md`. **Todos deben pasar.**

### 5.3 Test integral del flujo completo

1. Login admin
2. Crear departamento "TEC" (ATC ya existe)
3. Crear actividad "Inspección" en TEC con SLA=24h
4. Generar diagrama por prompt: *"Atención al Cliente recibe la solicitud, luego TEC hace inspección, finalmente Operaciones cierra"*
5. Crear política asociada al diagrama, estado=activa
6. Login cliente
7. Iniciar trámite con esa política → debe responder 201 con Tramite
8. Login funcionario de ATC → ver `/mis-pendientes` → debe aparecer el trámite
9. Completar nodo de ATC → trámite avanza a TEC
10. Login funcionario de TEC → completar nodo → trámite avanza a Operaciones
11. Login funcionario Operaciones → completar nodo → trámite queda Aprobado
12. Verificar:
    - Cliente recibe notificaciones en cada cambio
    - Trazabilidad tiene 4-5 eventos con hash chain íntegro
    - Métricas registradas para cada actividad
    - Expediente con 3 secciones todas en `completada`
    - GET `/api/tramites/{id}/estado` muestra progreso 100%, estado "Aprobado"

Si todo lo anterior pasa, la fase 1 está **completa y validada**.

---

## 6. Documentación final de fase 1

Crear `fase1/RESUMEN_EJECUTIVO.md` con:

```markdown
# Fase 1 · Resumen ejecutivo

## Componentes extraídos (10 + shared)

[lista de componentes con su Port principal y propósito]

## Métricas

- Líneas de código modificadas: ~XXX
- Archivos movidos: ~YYY
- Ports creados: 13
- Tests manuales pasados: 10/10
- Tiempo real invertido: X días

## Decisiones arquitectónicas registradas

- Shared kernel para utilidades transversales
- CQRS-light en workflow (Engine + Query Port)
- Catalogo como facade unificado de configuración
- (...)

## Deudas pendientes

- (lista corta de cosas que se podrían mejorar pero quedan para después)
```

Este documento es **lo que el profesor lee primero** para saber qué se hizo en fase 1.

---

## 7. Tag final de fase 1

Una vez todos los checks anteriores en verde:

```bash
git tag fin-fase1
git push origin refactor/componentes-backend
git push origin fin-fase1
```

---

## 8. Decisión: ¿mergear a main?

Dos opciones:

**Opción A (recomendada):** mergear `refactor/componentes-backend` a `main` ahora. La rama queda como historial.

**Opción B:** mantener la rama y seguir directamente con fase 2 (Angular) en una rama nueva `refactor/componentes-frontend` desde la base ya refactorizada.

Si el equipo tiene confianza en la verificación, **Opción A** es preferible — consolida el avance.

---

## 9. Siguientes pasos

- ✅ Fase 1 backend completa
- ⏭️ Continuar con **fase 2** (Angular ui-kit) — se documentará en `guia_reorganizacion/fase2/`
- ⏭️ Luego **fase 3** (Flutter widgets)
- ⏭️ Cierre con **fase 4** (validación arquitectónica: UML, ArchUnit, despliegue)
