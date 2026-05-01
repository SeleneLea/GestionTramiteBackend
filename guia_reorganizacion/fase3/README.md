# Fase 3 · Mobile Flutter a widgets reutilizables

> Reorganizar la app Flutter siguiendo el mismo principio aplicado al backend (fase 1) y al Angular (fase 2): **componentes propios reutilizables**.

---

## Estado actual del Flutter

```
mobile/lib/
├── main.dart
├── config/
│   └── environment.dart
├── middlewares/
├── mock/
├── models/                            ← 9 modelos
│   ├── actividad_model.dart
│   ├── auth_model.dart
│   ├── departamento_model.dart
│   ├── expediente_model.dart
│   ├── formulario_model.dart
│   ├── politica_model.dart
│   ├── tramite_estado_model.dart
│   ├── tramite_resumen_model.dart
│   └── usuario_model.dart
├── routes/
│   └── app_routes.dart
├── screens/
│   ├── auth/                          ← 2 pantallas
│   ├── comunicacion/                  ← 1 pantalla
│   ├── dashboard/
│   ├── home/
│   ├── profile/
│   └── tramites/                      ← 10 pantallas (lo más denso)
├── services/                          ← 7 servicios
└── widgets/
    └── chat_agente_ia.dart            ← solo 1 widget reutilizable hoy
```

**Diagnóstico:**
- ✅ `services/` está bien estructurado (uno por dominio)
- ✅ `models/` está separado por dominio
- ✅ Ya consolidamos lógica de estado/color en `tramites_seguimiento_service.dart` (sesión previa)
- ❌ `widgets/` casi vacío — la mayoría de UI está duplicada en cada screen
- ❌ `screens/` mezcla todo, sin features bien definidas
- ❌ Lógica de UI (color, formato fecha) viajando dentro de servicios HTTP

---

## Plan de la fase 3

| # | Archivo | Tarea | Tiempo |
|---|---------|-------|--------|
| 0 | `00_preparacion.md` | Auditoría + branch | 30 min |
| 1 | `01_estructura_objetivo.md` | Target: features/ + widgets/ kit + core/utils | 30 min |
| 2 | `02_consolidar_estado_utils.md` | Mover lógica estado a `core/utils/estado_utils.dart` | 1 h |
| 3 | `03_widget_estado_badge.md` | Widget `EstadoBadge` reutilizable | 1 h |
| 4 | `04_widget_progreso_bar.md` | Widget `ProgresoBar` con progreso efectivo | 1 h |
| 5 | `05_widget_tramite_card.md` | Widget `TramiteCard` reutilizable | 1.5 h |
| 6 | `06_widget_form_dinamico.md` | Widget `FormDinamico` para secciones | 2 h |
| 7 | `07_widget_timeline_custom.md` | Widget `TimelineCustom` para historial | 1.5 h |
| 8 | `08_reorganizar_features.md` | screens/ → features/ por dominio | 2.5 h |
| 9 | `09_validacion_final.md` | E2E + cierre | 1 h |

**Total estimado: ~11 horas (~1.5-2 días).**

---

## Filosofía: extraer, no reescribir

Igual que fases 1 y 2, no se reescribe. Lo que se hace:

1. Identificar UI **repetida** en distintas pantallas
2. Extraer a un widget en `widgets/` (luego `widgets/ui_kit/`)
3. Reemplazar el código inline en cada pantalla por el widget extraído
4. Borrar el código duplicado

---

## Próximo paso

Empezar por **`00_preparacion.md`**.
