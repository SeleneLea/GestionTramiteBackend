# Fase 1 · Backend a bounded contexts

> Reorganización del backend Spring Boot de paquetes-por-capa a paquetes-por-componente, con interfaces explícitas (Ports).

---

## Orden de ejecución

Cada componente se extrae en este orden, **del menos acoplado al más acoplado**. Así reducimos el riesgo de romper dependencias críticas en los pasos iniciales.

| # | Archivo | Componente | Tiempo | Riesgo |
|---|---------|------------|--------|--------|
| 0 | `00_preparacion.md` | (preparación previa) | 30 min | — |
| 1 | `01_shared_kernel.md` | shared | 1 h | Bajo |
| 2 | `02_componente_auth.md` | auth | 2 h | Bajo |
| 3 | `03_componente_notificaciones.md` | notificaciones | 2 h | Medio |
| 4 | `04_componente_trazabilidad.md` | trazabilidad | 1 h | Bajo |
| 5 | `05_componente_metricas.md` | metricas | 1 h | Bajo |
| 6 | `06_componente_expediente.md` | expediente | 3 h | Medio |
| 7 | `07_componente_workflow_design.md` | workflow-design | 3 h | Medio |
| 8 | `08_componente_workflow.md` | workflow ⚠️ núcleo | 4 h | Alto |
| 9 | `09_componente_catalogo.md` | catalogo-configuracion | 2 h | Bajo |
| 10 | `10_componente_ai_y_reportes.md` | ai-integration + reportes | 2 h | Bajo |
| 11 | `11_validacion_final.md` | validación end-to-end | 2 h | — |

**Total: ~22 horas (~3 días de trabajo concentrado).**

---

## Por qué este orden

1. **Shared primero** — porque todos los demás dependen de `ErrorResponse` y el `GlobalExceptionHandler`.
2. **Auth segundo** — es independiente y nos asegura que JWT siga funcionando antes de tocar otros componentes.
3. **Notificaciones, trazabilidad, metricas** — son componentes "hoja" (no consumen otros componentes de negocio), así que extraer sus Ports es directo y no rompe nadie.
4. **Expediente y workflow-design** — tienen lógica significativa pero son consumibles desde workflow, no consumen demasiado de otros.
5. **Workflow al final** — es el componente más acoplado: depende de notificaciones, métricas, trazabilidad, expediente, workflow-design, catalogo. Si lo movemos al final, sus Ports ya están listos.
6. **Catalogo, ai-integration, reportes** — periferia, se acomodan al final.

---

## Patrón común a todas las subfases

Cada archivo `0X_componente_*.md` sigue la misma estructura:

```
1. Objetivo
2. Archivos involucrados (lista exacta)
3. Estructura final del paquete
4. Definición del Port (interfaz)
5. Pasos de migración (A → G de la receta)
6. Verificación manual
7. Commit sugerido
```

---

## Regla durante toda la fase 1

> **Después de cada subfase, el proyecto compila y la app corre.**

Si al terminar un commit la app está rota, no se sigue al siguiente componente. Se arregla primero.

---

## Próximo paso

Empezar por **`00_preparacion.md`**.
