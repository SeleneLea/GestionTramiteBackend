# Fase 3.3 · Widget EstadoBadge

> Primer widget reutilizable. Renderiza un badge consistente recibiendo solo el `estado`.

---

## 1. Objetivo

Reemplazar las múltiples implementaciones inline de `Container` con `decoration` que pintan un badge en cada pantalla por un único widget `EstadoBadge`.

---

## 2. Archivo

`lib/widgets/estado_badge.dart`

---

## 3. Código

```dart
// lib/widgets/estado_badge.dart
import 'package:flutter/material.dart';
import '../core/utils/estado_utils.dart';

enum EstadoBadgeTamano { sm, md, lg }

class EstadoBadge extends StatelessWidget {
  final String estado;
  final EstadoBadgeTamano tamano;
  final bool mostrarIcono;
  final bool mostrarTexto;

  const EstadoBadge({
    super.key,
    required this.estado,
    this.tamano = EstadoBadgeTamano.md,
    this.mostrarIcono = true,
    this.mostrarTexto = true,
  });

  @override
  Widget build(BuildContext context) {
    final color = colorEstadoTramite(estado);
    final icono = iconoEstadoTramite(estado);
    final texto = textoEstadoTramite(estado);

    final padding = switch (tamano) {
      EstadoBadgeTamano.sm => const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      EstadoBadgeTamano.md => const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      EstadoBadgeTamano.lg => const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
    };

    final fontSize = switch (tamano) {
      EstadoBadgeTamano.sm => 11.0,
      EstadoBadgeTamano.md => 13.0,
      EstadoBadgeTamano.lg => 15.0,
    };

    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (mostrarIcono)
            Text(icono, style: TextStyle(fontSize: fontSize)),
          if (mostrarIcono && mostrarTexto)
            const SizedBox(width: 4),
          if (mostrarTexto)
            Text(
              texto,
              style: TextStyle(
                fontSize: fontSize,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
        ],
      ),
    );
  }
}
```

---

## 4. Uso

### Antes (en una screen):

```dart
Container(
  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
  decoration: BoxDecoration(
    color: tramitesSeguimientoService
        .getColorEstadoFlutter(tramite.estado).withOpacity(0.1),
    border: Border.all(
      color: tramitesSeguimientoService.getColorEstadoFlutter(tramite.estado)),
    borderRadius: BorderRadius.circular(16),
  ),
  child: Text(
    '${tramitesSeguimientoService.getIconoEstado(tramite.estado)} '
    '${tramitesSeguimientoService.getTextoEstado(tramite.estado)}',
    style: TextStyle(
      fontSize: 11,
      fontWeight: FontWeight.bold,
      color: tramitesSeguimientoService.getColorEstadoFlutter(tramite.estado),
    ),
  ),
),
```

### Después:

```dart
import '../../widgets/estado_badge.dart';

EstadoBadge(estado: tramite.estado, tamano: EstadoBadgeTamano.sm),
```

**De ~20 líneas a 1 línea.**

---

## 5. Pantallas a migrar

Buscar en `screens/`:

```bash
grep -rln "getColorEstadoFlutter\|getIconoEstado.*getTextoEstado" --include="*.dart" lib/screens
```

Lista esperada (según sesiones previas):
- [ ] `tramite_seguimiento_screen.dart` — badge en card de estado general
- [ ] `mis_tramites_screen.dart` — badge en cada card de la lista
- [ ] `tramites_lista_screen.dart` — similar
- [ ] `tramites_observados_screen.dart` — similar
- [ ] `detalle_linea_tiempo_screen.dart` — si tiene badge

---

## 6. Pasos

### Paso A — Crear el widget
Crear `lib/widgets/estado_badge.dart` con el código del punto 3.

### Paso B — Probar en pantalla piloto
`mis_tramites_screen.dart`: reemplazar el badge inline.

### Paso C — Hot reload y verificar visual

### Paso D — Migrar las demás pantallas

### Paso E — Verificar no-duplicación
```bash
grep -rn "Border.all.*colorEstado\|Border.all.*getColorEstado" --include="*.dart" lib/screens
```
Esperado: cero coincidencias.

---

## 7. Commit

```bash
git add .
git commit -m "feat(mobile/widgets): EstadoBadge reutilizable con tamanos configurables"
```

---

## Próximo paso

Continuar con **`04_widget_progreso_bar.md`**.
