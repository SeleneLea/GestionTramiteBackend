# Fase 3.2 · Consolidar lógica de estado en core/utils

> Mover la lógica de color/icono/texto/progreso desde `tramites_seguimiento_service.dart` a un archivo de utilidades puras. Es base para los widgets siguientes.

---

## 1. Objetivo

1. Crear `lib/core/utils/estado_utils.dart` con funciones puras.
2. Crear `lib/core/utils/fecha_utils.dart` con `formatoFecha` consolidado.
3. Limpiar el servicio HTTP: `tramites_seguimiento_service.dart` ya no debe contener lógica de UI.

---

## 2. Archivos involucrados

### Crear nuevos
- `lib/core/utils/estado_utils.dart`
- `lib/core/utils/fecha_utils.dart`

### Modificar
- `lib/services/tramites_seguimiento_service.dart` — quitar `getColorEstadoFlutter`, `progresoEfectivo`, `esEstadoTerminal`, `getIconoEstado`, `getTextoEstado`. Esos delegan a las funciones nuevas o se eliminan.
- Cada pantalla que usaba esos métodos → ahora importa las funciones de `core/utils`.

---

## 3. Código

### `lib/core/utils/estado_utils.dart`

```dart
import 'package:flutter/material.dart';

/// Estados terminales (el trámite ya no avanza).
const _estadosTerminales = {
  'Aprobado', 'Rechazado', 'Cancelado',
  'completado', 'archivado', 'rechazado',
};

bool esEstadoTerminal(String estado) => _estadosTerminales.contains(estado);

/// Color semántico según el estado.
Color colorEstadoTramite(String estado) {
  switch (estado) {
    case 'Aprobado':
    case 'completado':
      return Colors.green;
    case 'Rechazado':
    case 'rechazado':
      return Colors.red;
    case 'Cancelado':
    case 'archivado':
      return Colors.grey;
    case 'Observado':
    case 'Devuelto':
      return Colors.orange;
    case 'En proceso':
    case 'Derivado':
    case 'activo':
    case 'en_progreso':
      return Colors.blue;
    default:
      return Colors.blueGrey;
  }
}

/// Ícono / emoji según el estado.
String iconoEstadoTramite(String estado) {
  const iconos = {
    'Aprobado': '✅',
    'Rechazado': '❌',
    'Cancelado': '🚫',
    'En proceso': '⏳',
    'Derivado': '↗️',
    'Observado': '⚠️',
    'Devuelto': '↩️',
    'Nuevo': '🆕',
    'borrador': '📝',
    'en_progreso': '⏳',
    'completado': '✅',
    'archivado': '📦',
    'rechazado': '❌',
  };
  return iconos[estado] ?? '•';
}

/// Texto legible.
String textoEstadoTramite(String estado) {
  const textos = {
    'Aprobado': 'Aprobado',
    'Rechazado': 'Rechazado',
    'Cancelado': 'Cancelado',
    'En proceso': 'En Proceso',
    'Derivado': 'Derivado',
    'Observado': 'Observado',
    'Devuelto': 'Devuelto',
    'Nuevo': 'Nuevo',
    'borrador': 'Borrador',
    'activo': 'En Proceso',
    'en_progreso': 'En Proceso',
    'completado': 'Completado',
    'archivado': 'Archivado',
    'rechazado': 'Rechazado',
  };
  return textos[estado] ?? estado;
}

/// Progreso efectivo: 100% para terminales aprobados, real para el resto.
int progresoEfectivo(int progresoBackend, String estado) {
  if (estado == 'Aprobado' || estado == 'completado') return 100;
  if (esEstadoTerminal(estado) && progresoBackend == 0) return 100;
  return progresoBackend;
}

/// Ícono según tipo de evento del historial.
String iconoEvento(String tipo) {
  const iconos = {
    'creacion': '📝',
    'cambio_estado': '→',
    'aprobacion': '✅',
    'rechazo': '❌',
    'completacion': '✓',
    'archivo': '📎',
  };
  return iconos[tipo] ?? '•';
}
```

### `lib/core/utils/fecha_utils.dart`

```dart
/// Formato corto: dd/MM/yyyy
String formatoFechaCorto(String? fecha) {
  if (fecha == null || fecha.isEmpty) return '';
  try {
    final dt = DateTime.parse(fecha);
    return '${dt.day.toString().padLeft(2, '0')}/'
        '${dt.month.toString().padLeft(2, '0')}/${dt.year}';
  } catch (_) {
    return fecha;
  }
}

/// Formato con hora: dd/MM/yyyy HH:mm
String formatoFechaConHora(String? fecha) {
  if (fecha == null || fecha.isEmpty) return '';
  try {
    final dt = DateTime.parse(fecha);
    return '${dt.day}/${dt.month}/${dt.year} '
        '${dt.hour}:${dt.minute.toString().padLeft(2, '0')}';
  } catch (_) {
    return fecha;
  }
}
```

---

## 4. Pasos de migración

### Paso A — Crear estructura
```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/mobile/lib"
mkdir -p core/utils
```

### Paso B — Crear los dos archivos `*_utils.dart`

### Paso C — Limpiar `tramites_seguimiento_service.dart`

Antes:
```dart
class TramitesSeguimientoService extends GetxService {
  Color getColorEstadoFlutter(String estado) { ... }   // ← QUITAR
  bool esEstadoTerminal(String estado) { ... }         // ← QUITAR
  int progresoEfectivo(int p, String e) { ... }        // ← QUITAR
  String getIconoEstado(String e) { ... }              // ← QUITAR
  String getTextoEstado(String e) { ... }              // ← QUITAR
  String getIconoEvento(String t) { ... }              // ← QUITAR

  // Los métodos HTTP se quedan
  Future<List<TramiteResumen>> obtenerMisTramites() { ... }
  Future<EstadoTramite> obtenerEstadoTramite(String id) { ... }
  Future<Map<String, dynamic>> getExpediente(String id) { ... }
  // etc.
}
```

Después:
```dart
class TramitesSeguimientoService extends GetxService {
  // Solo HTTP. La lógica de UI vive en core/utils/estado_utils.dart
  Future<List<TramiteResumen>> obtenerMisTramites() { ... }
  Future<EstadoTramite> obtenerEstadoTramite(String id) { ... }
  // ...
}
```

### Paso D — Adaptar las pantallas que llamaban al servicio para esto

Antes:
```dart
final color = tramitesSeguimientoService.getColorEstadoFlutter(estado!.estado);
final progreso = tramitesSeguimientoService.progresoEfectivo(estado!.progreso, estado!.estado);
final esCerrado = tramitesSeguimientoService.esEstadoTerminal(estado!.estado);
```

Después:
```dart
import '../../core/utils/estado_utils.dart';

final color = colorEstadoTramite(estado!.estado);
final progreso = progresoEfectivo(estado!.progreso, estado!.estado);
final esCerrado = esEstadoTerminal(estado!.estado);
```

Pantallas a actualizar (basado en lo que ya consolidamos en sesiones previas):
- `screens/tramites/tramite_seguimiento_screen.dart`
- `screens/tramites/mis_tramites_screen.dart`
- `screens/tramites/tramites_lista_screen.dart`
- `screens/tramites/tramites_observados_screen.dart`
- `screens/tramites/detalle_linea_tiempo_screen.dart`
- Cualquier otra que use el servicio para color/icono/texto

### Paso E — Verificar que no quedan llamadas a los métodos viejos

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/mobile/lib"
grep -rn "getColorEstadoFlutter\|getIconoEstado\|getTextoEstado\|esEstadoTerminal" --include="*.dart"
```

Resultado esperado: solo coincidencias en `core/utils/estado_utils.dart` y en imports de pantallas (las nuevas funciones libres).

---

## 5. Verificación

| Pantalla | Esperado |
|----------|----------|
| Lista mis-tramites | Cards con colores correctos por estado |
| Detalle trámite "Aprobado" 0% | Muestra 100% (progreso efectivo) |
| Detalle trámite "Rechazado" 0% | Muestra 100% (terminal con override) |
| Notificaciones | Sin cambios visuales (no usa estos utils) |

---

## 6. Commit

```bash
git add .
git commit -m "refactor(mobile): mover logica estado a core/utils, limpiar servicio HTTP"
```

---

## Próximo paso

Continuar con **`03_widget_estado_badge.md`**.
