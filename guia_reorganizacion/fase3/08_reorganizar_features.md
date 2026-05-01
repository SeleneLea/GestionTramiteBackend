# Fase 3.8 · Reorganizar a features/

> Última subfase de movimiento de archivos: pasar de `screens/` + `services/` planos a `features/<dominio>/{pages,services}/`.

---

## 1. Objetivo

Que cada feature de negocio tenga sus pantallas y servicios juntos, espejando la estructura del Angular (fase 2) y del backend (fase 1).

---

## 2. Mapping completo

| Hoy | Mañana |
|-----|--------|
| `screens/auth/login_screen.dart` | `features/auth/pages/login_screen.dart` |
| `screens/auth/register_screen.dart` | `features/auth/pages/register_screen.dart` |
| `services/auth_service.dart` | `features/auth/services/auth_service.dart` |
| `screens/tramites/*.dart` (10 archivos) | `features/tramites/pages/*.dart` |
| `services/tramites_service.dart` | `features/tramites/services/tramites_service.dart` |
| `services/tramites_envio_service.dart` | `features/tramites/services/tramites_envio_service.dart` |
| `services/tramites_seguimiento_service.dart` | `features/tramites/services/tramites_seguimiento_service.dart` |
| `screens/comunicacion/notificaciones_screen.dart` | `features/comunicacion/pages/notificaciones_screen.dart` |
| `services/comunicacion_service.dart` | `features/comunicacion/services/comunicacion_service.dart` |
| `screens/dashboard/` | `features/dashboard/pages/` |
| `screens/home/` | `features/home/pages/` |
| `screens/profile/` | `features/profile/pages/` |
| `services/http_client_service.dart` | `core/http/http_client_service.dart` |
| `services/storage_service.dart` | `core/storage/storage_service.dart` |
| `config/environment.dart` | `core/env/environment.dart` |

---

## 3. Estructura final

```
mobile/lib/
├── main.dart
├── core/
│   ├── env/environment.dart
│   ├── http/http_client_service.dart
│   ├── storage/storage_service.dart
│   └── utils/
│       ├── estado_utils.dart
│       └── fecha_utils.dart
├── widgets/
│   ├── estado_badge.dart
│   ├── progreso_bar.dart
│   ├── tramite_card.dart
│   ├── tramite_card_data.dart
│   ├── form_dinamico.dart
│   ├── form_dinamico_types.dart
│   ├── timeline_custom.dart
│   ├── timeline_custom_types.dart
│   └── chat_agente_ia.dart
├── features/
│   ├── auth/
│   │   ├── pages/
│   │   │   ├── login_screen.dart
│   │   │   └── register_screen.dart
│   │   └── services/auth_service.dart
│   ├── tramites/
│   │   ├── pages/                     (10 pantallas)
│   │   └── services/                  (3 servicios)
│   ├── comunicacion/
│   │   ├── pages/notificaciones_screen.dart
│   │   └── services/comunicacion_service.dart
│   ├── dashboard/pages/
│   ├── home/pages/
│   └── profile/pages/
├── models/                            (queda igual)
├── routes/app_routes.dart
├── middlewares/
└── mock/
```

---

## 4. Pasos de migración

### Paso A — Crear estructura

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/mobile/lib"
mkdir -p core/env core/http core/storage
mkdir -p features/auth/pages features/auth/services
mkdir -p features/tramites/pages features/tramites/services
mkdir -p features/comunicacion/pages features/comunicacion/services
mkdir -p features/dashboard/pages features/home/pages features/profile/pages
```

### Paso B — Mover los servicios "core" primero

```bash
git mv config/environment.dart core/env/environment.dart
git mv services/http_client_service.dart core/http/http_client_service.dart
git mv services/storage_service.dart core/storage/storage_service.dart
```

Actualizar imports en todo el proyecto. VS Code/Android Studio muestran los imports rotos:
- `import '../config/environment.dart'` → `import '../core/env/environment.dart'`

### Paso C — Mover por feature

#### auth
```bash
git mv screens/auth/login_screen.dart features/auth/pages/login_screen.dart
git mv screens/auth/register_screen.dart features/auth/pages/register_screen.dart
git mv services/auth_service.dart features/auth/services/auth_service.dart
```

Actualizar imports y verificar:
```bash
flutter analyze
```

#### tramites
```bash
git mv screens/tramites/*.dart features/tramites/pages/
git mv services/tramites_service.dart features/tramites/services/
git mv services/tramites_envio_service.dart features/tramites/services/
git mv services/tramites_seguimiento_service.dart features/tramites/services/
```

#### comunicacion
```bash
git mv screens/comunicacion/notificaciones_screen.dart features/comunicacion/pages/
git mv services/comunicacion_service.dart features/comunicacion/services/
```

#### dashboard, home, profile
```bash
git mv screens/dashboard/* features/dashboard/pages/
git mv screens/home/* features/home/pages/
git mv screens/profile/* features/profile/pages/
```

### Paso D — Borrar carpetas vacías

```bash
rmdir screens/auth screens/tramites screens/comunicacion screens/dashboard screens/home screens/profile
rmdir screens/
rmdir config/
rmdir services/  # solo si quedó vacío
```

### Paso E — Actualizar `routes/app_routes.dart`

Cambiar todos los imports de pantallas:
```dart
// Antes:
import '../screens/auth/login_screen.dart';
import '../screens/tramites/mis_tramites_screen.dart';

// Después:
import '../features/auth/pages/login_screen.dart';
import '../features/tramites/pages/mis_tramites_screen.dart';
```

### Paso F — Actualizar `main.dart`

Cambiar imports y registros de servicios en GetX:
```dart
// Antes:
import 'services/auth_service.dart';
import 'services/tramites_seguimiento_service.dart';
import 'services/storage_service.dart';

// Después:
import 'features/auth/services/auth_service.dart';
import 'features/tramites/services/tramites_seguimiento_service.dart';
import 'core/storage/storage_service.dart';
```

### Paso G — `flutter analyze` y `flutter run`

```bash
flutter analyze
```
Debe terminar con 0 errores.

```bash
flutter run
```
La app debe arrancar y todos los flujos seguir funcionando.

---

## 5. Verificación funcional

| Flujo | Esperado |
|-------|----------|
| Login | Funciona |
| Mis trámites carga | Funciona |
| Tap en card → detalle | Funciona |
| Notificaciones | Funciona |
| Logout | Funciona |
| Iniciar trámite nuevo | Funciona |

---

## 6. Commit

Por feature movido (recomendado):
```bash
git add .
git commit -m "refactor(mobile): mover auth a features/auth"
git commit -m "refactor(mobile): mover tramites a features/tramites"
git commit -m "refactor(mobile): mover comunicacion a features/comunicacion"
git commit -m "refactor(mobile): mover servicios infra a core/"
git commit -m "refactor(mobile): mover dashboard/home/profile a features/"
```

---

## Próximo paso

Continuar con **`09_validacion_final.md`**.
