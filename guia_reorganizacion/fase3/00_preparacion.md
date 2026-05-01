# Fase 3.0 · Preparación previa

---

## 1. Verificar que la app compila y corre

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/mobile"
flutter pub get
flutter run
```

Smoke tests rápidos en el emulador:
- [ ] Login con `cliente@cre.bo`
- [ ] Lista "Mis trámites" carga
- [ ] Detalle de un trámite muestra estado, progreso, etapa
- [ ] Notificaciones cargan
- [ ] Logout

---

## 2. Control de versiones

```bash
git status
git add . && git commit -m "..."
git checkout -b refactor/componentes-flutter
git tag pre-refactor-fase3
```

---

## 3. Auditoría de duplicación visual

Buscar UI repetida en las screens:

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/mobile/lib/screens"

# Cards de trámite hechos a mano
grep -rln "tramite.codigo\|tramite\.codigo" --include="*.dart"

# Lógica de color por estado
grep -rln "case 'Aprobado'" --include="*.dart" | grep -v services/
grep -rln "Colors\.green\|Colors\.red" --include="*.dart"

# Linear progress indicator inline
grep -rln "LinearProgressIndicator" --include="*.dart"

# DateTime.parse / formato manual
grep -rln "_formatoFecha\|formatoFecha" --include="*.dart"
```

Anotar cuántas veces aparece cada patrón. Cualquiera con > 2 apariciones es candidato a widget reutilizable.

---

## 4. Estructura objetivo (preview)

```
mobile/lib/
├── main.dart
├── core/                              ← NUEVO
│   ├── env/
│   │   └── environment.dart           (movido desde config/)
│   ├── http/
│   │   └── http_client_service.dart   (movido desde services/)
│   ├── storage/
│   │   └── storage_service.dart
│   └── utils/
│       ├── estado_utils.dart          ← lógica de color/icono/texto/progreso
│       └── fecha_utils.dart           ← _formatoFecha consolidado
├── routes/
│   └── app_routes.dart
├── widgets/                           ← UI Kit propio (renombrar a ui_kit/ opcional)
│   ├── estado_badge.dart              ← NUEVO
│   ├── progreso_bar.dart              ← NUEVO
│   ├── tramite_card.dart              ← NUEVO
│   ├── form_dinamico.dart             ← NUEVO
│   ├── timeline_custom.dart           ← NUEVO
│   └── chat_agente_ia.dart            (ya existe)
├── features/                          ← NUEVO
│   ├── auth/
│   │   ├── pages/
│   │   │   ├── login_screen.dart
│   │   │   └── register_screen.dart
│   │   └── services/auth_service.dart
│   ├── tramites/
│   │   ├── pages/
│   │   │   ├── catalogo_tramites_screen.dart
│   │   │   ├── iniciar_tramite_screen.dart
│   │   │   ├── mis_tramites_screen.dart
│   │   │   ├── tramite_detalle_screen.dart
│   │   │   ├── tramite_seguimiento_screen.dart
│   │   │   ├── tramites_lista_screen.dart
│   │   │   ├── tramites_observados_screen.dart
│   │   │   ├── detalle_linea_tiempo_screen.dart
│   │   │   ├── realizar_correccion_screen.dart
│   │   │   └── tramite_nuevo_screen.dart
│   │   └── services/
│   │       ├── tramites_envio_service.dart
│   │       ├── tramites_seguimiento_service.dart
│   │       └── tramites_service.dart
│   ├── comunicacion/
│   │   ├── pages/notificaciones_screen.dart
│   │   └── services/comunicacion_service.dart
│   ├── dashboard/
│   ├── home/
│   └── profile/
└── models/                            (queda igual)
```

---

## 5. Convenciones Flutter

| Tipo | Patrón | Ejemplo |
|------|--------|---------|
| Archivo | `snake_case.dart` | `tramite_card.dart` |
| Clase | `PascalCase` | `TramiteCard` |
| Variable | `camelCase` | `tramiteId` |
| Constante | `lowerCamelCase` `static const` | `static const kPadding = 16.0` |
| Widget reutilizable | `class XWidget extends StatelessWidget` o `StatefulWidget` |

### Stateless por defecto
Todo widget reutilizable empieza siendo `StatelessWidget`. Solo se vuelve `StatefulWidget` si maneja estado interno.

### Const constructors
Usar `const` en constructors siempre que sea posible (mejora performance).

```dart
const EstadoBadge({super.key, required this.estado});
```

### Parameters tipados y nombrados
```dart
const TramiteCard({
  super.key,
  required this.tramite,
  this.onTap,
  this.compacto = false,
});
```

---

## 6. Confirmación

- [ ] App levanta y smoke tests pasan
- [ ] Rama y tag creados
- [ ] Auditoría hecha (cantidades anotadas)

```bash
git add .
git commit -m "chore: punto cero de fase 3 (auditoria de duplicacion mobile)"
```

---

## Próximo paso

Continuar con **`01_estructura_objetivo.md`**.
