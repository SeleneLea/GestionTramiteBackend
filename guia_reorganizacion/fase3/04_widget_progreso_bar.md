# Fase 3.4 · Widget ProgresoBar

> Barra de progreso con progreso efectivo y color según estado.

---

## 1. Objetivo

Un widget que recibe `progreso` (0-100) y `estado`, y pinta una barra del color correspondiente al estado, aplicando `progresoEfectivo` automáticamente.

---

## 2. Archivo

`lib/widgets/progreso_bar.dart`

---

## 3. Código

```dart
// lib/widgets/progreso_bar.dart
import 'package:flutter/material.dart';
import '../core/utils/estado_utils.dart';

enum ProgresoBarTamano { sm, md, lg }

class ProgresoBar extends StatelessWidget {
  /// Progreso 0-100 que viene del backend.
  final int progreso;

  /// Estado del trámite (para definir color y aplicar progreso efectivo).
  final String estado;

  /// Si se muestra el porcentaje al lado.
  final bool mostrarPorcentaje;

  /// Tamaño de la barra.
  final ProgresoBarTamano tamano;

  const ProgresoBar({
    super.key,
    required this.progreso,
    required this.estado,
    this.mostrarPorcentaje = true,
    this.tamano = ProgresoBarTamano.md,
  });

  @override
  Widget build(BuildContext context) {
    final color = colorEstadoTramite(estado);
    final progresoFinal = progresoEfectivo(progreso, estado);

    final altura = switch (tamano) {
      ProgresoBarTamano.sm => 4.0,
      ProgresoBarTamano.md => 8.0,
      ProgresoBarTamano.lg => 14.0,
    };

    return Row(
      children: [
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              value: progresoFinal / 100,
              minHeight: altura,
              backgroundColor: Colors.grey.shade300,
              valueColor: AlwaysStoppedAnimation(color),
            ),
          ),
        ),
        if (mostrarPorcentaje) ...[
          const SizedBox(width: 8),
          SizedBox(
            width: 40,
            child: Text(
              '$progresoFinal%',
              textAlign: TextAlign.right,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: color,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
          ),
        ],
      ],
    );
  }
}
```

---

## 4. Uso

### Antes:

```dart
Column(
  children: [
    Row(
      children: [
        const Text('Progreso'),
        const Spacer(),
        Text('${tramite.progreso}%'),
      ],
    ),
    const SizedBox(height: 4),
    ClipRRect(
      borderRadius: BorderRadius.circular(4),
      child: LinearProgressIndicator(
        value: tramite.progreso / 100,
        minHeight: 6,
        backgroundColor: Colors.grey.shade300,
        valueColor: AlwaysStoppedAnimation(Colors.blue.shade600),
      ),
    ),
  ],
),
```

### Después:

```dart
import '../../widgets/progreso_bar.dart';

ProgresoBar(progreso: tramite.progreso, estado: tramite.estado),
```

---

## 5. Pasos

### Paso A — Crear el widget

### Paso B — Probar en pantalla piloto: `mis_tramites_screen.dart`

### Paso C — Verificar:
- Trámite "En proceso" 40% → barra azul al 40%
- Trámite "Aprobado" 0% → barra verde al **100%**
- Trámite "Rechazado" 33% → barra roja al 33%

### Paso D — Migrar el resto:
- `tramite_seguimiento_screen.dart`
- `tramites_lista_screen.dart`
- `tramites_observados_screen.dart`
- `detalle_linea_tiempo_screen.dart` (si aplica)

---

## 6. Commit

```bash
git add .
git commit -m "feat(mobile/widgets): ProgresoBar con progreso efectivo y color por estado"
```

---

## Próximo paso

Continuar con **`05_widget_tramite_card.md`**.
