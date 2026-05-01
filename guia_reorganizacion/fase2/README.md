# Fase 2 · Frontend Angular a componentes reutilizables

> Reorganizar el frontend Angular de organización **por rol** (`admin/`, `funcionario/`) a organización **por feature de negocio**, y consolidar un `ui-kit` propio reutilizable.

---

## Estado actual del proyecto Angular

Estructura existente:

```
src/app/
├── admin/                     ← features mezclados por rol
│   ├── actividades/
│   ├── dashboard/
│   ├── departamentos/
│   ├── diagramas/
│   ├── historial/
│   ├── metricas/
│   ├── politicas/
│   └── usuarios/
├── auth/
│   └── login/
├── core/
│   ├── guards/
│   ├── interceptors/
│   ├── models/                ← 8 modelos
│   └── services/              ← 13 servicios HTTP
├── funcionario/               ← features mezclados por rol
│   ├── bandeja-entrada/
│   ├── expediente-digital/
│   ├── tramite-detalle/
│   └── tramites-lista/
└── shared/
    ├── navbar/
    ├── sidebar/
    ├── skeleton/
    ├── toast/
    ├── pages/
    └── ui/                    ← ya hay algunos componentes
        ├── card/
        ├── empty-state/
        ├── input/
        ├── modal/
        ├── status-badge/
        ├── table/
        └── index.ts
```

**Diagnóstico:**
- ✅ `core/` está bien estructurado
- ✅ Hay un `shared/ui/` con base reutilizable
- ⚠️ Los features están mezclados por rol (admin/funcionario), no por dominio
- ⚠️ Lógica de "color por estado" duplicada en plantillas (verificado en sesiones previas)
- ⚠️ Componentes existentes pueden estar infrautilizados (varias pantallas hacen su propio badge/card)
- ❌ Falta `progress-bar`, `timeline`, `form-dinamico` reutilizables

---

## Plan de la fase 2

| # | Archivo | Tarea | Tiempo |
|---|---------|-------|--------|
| 0 | `00_preparacion.md` | Auditoría + rama | 30 min |
| 1 | `01_estructura_objetivo.md` | Target: features/ por dominio | 30 min |
| 2 | `02_auditoria_ui_kit.md` | Inventario de lo que ya existe en shared/ui | 1 h |
| 3 | `03_estado_y_badges.md` | Consolidar `status-badge` + `EstadoService` | 1.5 h |
| 4 | `04_progress_bar.md` | Nuevo `<app-progress-bar>` | 1 h |
| 5 | `05_card_tramite.md` | Nuevo `<app-card-tramite>` | 2 h |
| 6 | `06_data_table.md` | Mejorar `<app-table>` genérica | 2 h |
| 7 | `07_form_dinamico.md` | Nuevo `<app-form-dinamico>` para secciones | 3 h |
| 8 | `08_timeline.md` | Nuevo `<app-timeline>` | 2 h |
| 9 | `09_reorganizar_features.md` | admin/+funcionario/ → features/ por dominio | 3 h |
| 10 | `10_validacion_final.md` | E2E + cierre | 1.5 h |

**Total estimado: ~17 horas (~2-2.5 días).**

---

## Diferencia clave con fase 1

En fase 1 movimos **archivos del backend**. En fase 2 hacemos dos cosas distintas:

1. **Crear / mejorar componentes UI reutilizables** (es código nuevo o consolidado)
2. **Reorganizar features** del frontend (es como fase 1, mover archivos)

---

## Filosofía: no romper, mejorar

El frontend Angular ya **funciona y se ve bien**. La fase 2 no debería romper visualmente nada. Cada componente reutilizable que creamos:

1. Se crea **al lado** del código viejo
2. Se prueba en **una** pantalla primero
3. Si pasa, se reemplaza progresivamente en las demás
4. Se borra el código duplicado

---

## Próximo paso

Empezar por **`00_preparacion.md`**.
