# Fase 3.1 · Estructura objetivo y decisiones de diseño

---

## 1. Estructura final completa

```
mobile/lib/
├── main.dart
│
├── core/                              ← infraestructura común
│   ├── env/
│   │   └── environment.dart
│   ├── http/
│   │   └── http_client_service.dart
│   ├── storage/
│   │   └── storage_service.dart
│   └── utils/
│       ├── estado_utils.dart
│       └── fecha_utils.dart
│
├── widgets/                           ← UI Kit reutilizable propio
│   ├── estado_badge.dart
│   ├── progreso_bar.dart
│   ├── tramite_card.dart
│   ├── tramite_card_data.dart         ← DTO interno del widget
│   ├── form_dinamico.dart
│   ├── form_dinamico_types.dart
│   ├── timeline_custom.dart
│   ├── timeline_custom_types.dart
│   ├── chat_agente_ia.dart
│   └── ui_kit.dart                    ← barrel
│
├── features/                          ← features por dominio
│   ├── auth/
│   │   ├── pages/
│   │   └── services/
│   ├── tramites/
│   │   ├── pages/
│   │   └── services/
│   ├── comunicacion/
│   │   ├── pages/
│   │   └── services/
│   ├── dashboard/
│   │   └── pages/
│   ├── home/
│   │   └── pages/
│   └── profile/
│       └── pages/
│
├── models/                            (queda igual, modelos compartidos)
│
└── routes/
    └── app_routes.dart
```

---

## 2. Decisiones de diseño

### 2.1 ¿Por qué `widgets/` y no `ui_kit/`?

Flutter usa `widgets` como término universal — toda la UI son widgets. Llamar a la carpeta `widgets/` está alineado con la convención Dart/Flutter. Si quieres ser más explícito, puedes renombrarla a `ui_kit/`, pero no es obligatorio.

> **Decisión:** mantener `widgets/` como carpeta del UI kit propio.

### 2.2 ¿`features/` o sigo con `screens/`?

`screens/` agrupa por **tipo técnico** (todas las pantallas juntas). `features/` agrupa por **dominio**, con sus pages **y** sus services adentro.

Ventajas de `features/`:
- Los servicios de un feature están al lado de las pantallas que los usan
- Si quieres mover el feature `tramites` a otro proyecto, te llevas una carpeta y listo
- Espeja la estructura del backend (`workflow/`, `expediente/`, etc.) y de Angular (`features/`)

> **Decisión:** migrar a `features/`. Servicios específicos de un feature se mueven dentro.

### 2.3 ¿Por qué `core/utils/estado_utils.dart`?

Hoy `tramites_seguimiento_service.dart` tiene métodos como `getColorEstadoFlutter`, `progresoEfectivo`, `esEstadoTerminal`. Eso es lógica de **vista**, no del servicio HTTP.

Mover a `core/utils/estado_utils.dart` permite:
- Que cualquier widget la use sin inyectar el servicio
- Que sean **funciones puras** (más fáciles de testear)
- Que el servicio HTTP solo haga HTTP

### 2.4 ¿Qué se queda en `models/`?

Los modelos de **dominio** (lo que el backend devuelve) se mantienen como hoy:
- `tramite_estado_model.dart`
- `expediente_model.dart`
- etc.

Estos son distintos de los **DTOs internos del widget** (ej. `TramiteCardData`), que viven al lado del widget en `widgets/`.

---

## 3. Política de migración

Misma que en fase 2:

1. Crear todos los widgets nuevos en `widgets/` (subfases 3.3 a 3.7)
2. Reemplazarlos pantalla por pantalla
3. Cuando todas las pantallas usan el widget nuevo, borrar el código duplicado
4. Recién al final reorganizar `screens/` → `features/` (subfase 3.8)

---

## 4. Mapa de dependencias entre widgets

```
widgets/
├── estado_badge        ← usa core/utils/estado_utils
├── progreso_bar        ← usa core/utils/estado_utils
├── tramite_card        ← usa estado_badge + progreso_bar
├── form_dinamico       ← independiente
├── timeline_custom     ← usa core/utils/estado_utils para colores
└── chat_agente_ia      ← independiente (ya existe)
```

**Regla:** un widget del kit puede usar a otro widget del kit. No al revés (`estado_badge` no debe importar `tramite_card`).

---

## 5. Próximo paso

Continuar con **`02_consolidar_estado_utils.md`**.
