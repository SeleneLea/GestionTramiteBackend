# Fase 3.5 · Widget TramiteCard

> Tarjeta resumen de un trámite. Reutilizable en `mis_tramites`, `tramites_lista`, `tramites_observados`, `bandeja`, etc.

---

## 1. Objetivo

Un único widget que recibe un DTO `TramiteCardData` y renderiza una card consistente con badge de estado, progreso, etapa actual, fechas y `onTap`.

---

## 2. Archivos

- `lib/widgets/tramite_card_data.dart` — DTO interno
- `lib/widgets/tramite_card.dart` — widget

---

## 3. Código

### `lib/widgets/tramite_card_data.dart`

```dart
class TramiteCardData {
  final String id;
  final String codigo;
  final String politicaNombre;
  final String estado;
  final int progreso;
  final String? nodoActualNombre;
  final String? fechaInicio;
  final String? fechaCierreReal;
  final int? prioridad;

  const TramiteCardData({
    required this.id,
    required this.codigo,
    required this.politicaNombre,
    required this.estado,
    required this.progreso,
    this.nodoActualNombre,
    this.fechaInicio,
    this.fechaCierreReal,
    this.prioridad,
  });
}
```

### `lib/widgets/tramite_card.dart`

```dart
import 'package:flutter/material.dart';
import '../core/utils/estado_utils.dart';
import '../core/utils/fecha_utils.dart';
import 'estado_badge.dart';
import 'progreso_bar.dart';
import 'tramite_card_data.dart';

class TramiteCard extends StatelessWidget {
  final TramiteCardData tramite;
  final VoidCallback? onTap;
  final bool compacto;

  const TramiteCard({
    super.key,
    required this.tramite,
    this.onTap,
    this.compacto = false,
  });

  @override
  Widget build(BuildContext context) {
    final esCerrado = esEstadoTerminal(tramite.estado);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Encabezado: código + estado
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          tramite.codigo,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          tramite.politicaNombre,
                          style: const TextStyle(
                            color: Colors.grey,
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  EstadoBadge(estado: tramite.estado, tamano: EstadoBadgeTamano.sm),
                ],
              ),
              const SizedBox(height: 12),

              // Etapa actual (solo si no está cerrado)
              if (!esCerrado && (tramite.nodoActualNombre?.isNotEmpty ?? false))
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Row(
                    children: [
                      Icon(Icons.info_outline, size: 16, color: Colors.grey[600]),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          'Etapa: ${tramite.nodoActualNombre}',
                          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),

              // Progreso
              if (!compacto) ...[
                Row(
                  children: [
                    Text('Progreso',
                        style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                  ],
                ),
                const SizedBox(height: 4),
              ],
              ProgresoBar(progreso: tramite.progreso, estado: tramite.estado),

              if (!compacto) ...[
                const SizedBox(height: 12),
                if (tramite.fechaInicio != null)
                  Text(
                    '📅 Creado: ${formatoFechaCorto(tramite.fechaInicio)}',
                    style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                  ),
                if (tramite.fechaCierreReal != null)
                  Text(
                    '✓ Cerrado: ${formatoFechaCorto(tramite.fechaCierreReal)}',
                    style: TextStyle(fontSize: 11, color: Colors.green[600]),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
```

---

## 4. Uso

### En `mis_tramites_screen.dart`:

```dart
import '../../widgets/tramite_card.dart';
import '../../widgets/tramite_card_data.dart';

// Mapper privado de modelo de backend a DTO del widget
TramiteCardData _toCardData(TramiteResumen t) => TramiteCardData(
  id: t.id,
  codigo: t.codigo,
  politicaNombre: t.politicaNombre,
  estado: t.estado,
  progreso: t.progreso,
  nodoActualNombre: t.nodoActualNombre,
  fechaInicio: t.fechaInicio,
  fechaCierreReal: t.fechaCierreReal,
);

// En el ListView:
ListView.builder(
  itemCount: tramites.length,
  itemBuilder: (context, index) {
    final t = tramites[index];
    return TramiteCard(
      tramite: _toCardData(t),
      onTap: () {
        Get.toNamed('/tramite-seguimiento', arguments: t.id);
      },
    );
  },
);
```

---

## 5. Pantallas a migrar

| Pantalla | Acción |
|----------|--------|
| `mis_tramites_screen.dart` | Reemplazar las cards por `TramiteCard` |
| `tramites_lista_screen.dart` | Reemplazar |
| `tramites_observados_screen.dart` | Reemplazar |
| `tramite_detalle_screen.dart` | (si tiene listado interno, evaluar) |

---

## 6. Pasos

### Paso A — Crear el DTO (`tramite_card_data.dart`)

### Paso B — Crear el widget (`tramite_card.dart`)

### Paso C — Pantalla piloto: `mis_tramites_screen.dart`
- Importar el widget
- Crear el mapper `_toCardData`
- Reemplazar el `_buildTramiteCard` original por la llamada a `TramiteCard`
- **Borrar** el método `_buildTramiteCard` viejo y el helper `_formatoFecha` (usar `formatoFechaCorto`)

### Paso D — Migrar las demás pantallas

### Paso E — Verificar visual y funcional
- Cards consistentes en tres listas distintas
- Tap navega correctamente
- Estados pintan colores correctos
- Trámites cerrados muestran 100% y badge verde/rojo

---

## 7. Commit

```bash
git add .
git commit -m "feat(mobile/widgets): TramiteCard reutilizable con DTO desacoplado"
```

---

## Próximo paso

Continuar con **`06_widget_form_dinamico.md`**.
