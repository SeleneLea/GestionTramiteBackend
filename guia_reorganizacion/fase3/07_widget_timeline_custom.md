# Fase 3.7 · Widget TimelineCustom

> Línea de tiempo vertical para mostrar historial del trámite. Reemplaza la implementación inline en `tramite_seguimiento_screen.dart` y `detalle_linea_tiempo_screen.dart`.

---

## 1. Objetivo

Un widget que recibe una lista de `HitoTimeline` (eventos) y renderiza una columna con marca circular + línea conectora + contenido. Acepta override de ícono y color.

---

## 2. Archivos

- `lib/widgets/timeline_custom_types.dart`
- `lib/widgets/timeline_custom.dart`

---

## 3. Tipos

### `lib/widgets/timeline_custom_types.dart`

```dart
import 'package:flutter/material.dart';

enum EstadoHito { completado, enCurso, pendiente, rechazado }

class HitoTimeline {
  final String id;
  final String titulo;
  final String? descripcion;
  final String? fecha;
  final String? autor;
  final String? departamento;
  final EstadoHito estado;
  final IconData? iconoOverride;

  const HitoTimeline({
    required this.id,
    required this.titulo,
    this.descripcion,
    this.fecha,
    this.autor,
    this.departamento,
    required this.estado,
    this.iconoOverride,
  });
}
```

---

## 4. Widget

### `lib/widgets/timeline_custom.dart`

```dart
import 'package:flutter/material.dart';
import '../core/utils/fecha_utils.dart';
import 'timeline_custom_types.dart';

class TimelineCustom extends StatelessWidget {
  final List<HitoTimeline> hitos;
  final bool compacto;

  const TimelineCustom({
    super.key,
    required this.hitos,
    this.compacto = false,
  });

  Color _colorEstado(EstadoHito e) => switch (e) {
    EstadoHito.completado => Colors.green,
    EstadoHito.enCurso => Colors.blue,
    EstadoHito.pendiente => Colors.grey,
    EstadoHito.rechazado => Colors.red,
  };

  IconData _iconoEstado(EstadoHito e) => switch (e) {
    EstadoHito.completado => Icons.check_circle,
    EstadoHito.enCurso => Icons.hourglass_top,
    EstadoHito.pendiente => Icons.radio_button_unchecked,
    EstadoHito.rechazado => Icons.cancel,
  };

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (int i = 0; i < hitos.length; i++)
          _HitoTile(
            hito: hitos[i],
            esUltimo: i == hitos.length - 1,
            color: _colorEstado(hitos[i].estado),
            icono: hitos[i].iconoOverride ?? _iconoEstado(hitos[i].estado),
            compacto: compacto,
          ),
      ],
    );
  }
}

class _HitoTile extends StatelessWidget {
  final HitoTimeline hito;
  final bool esUltimo;
  final Color color;
  final IconData icono;
  final bool compacto;

  const _HitoTile({
    required this.hito,
    required this.esUltimo,
    required this.color,
    required this.icono,
    required this.compacto,
  });

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Columna de marca + línea
          SizedBox(
            width: 32,
            child: Column(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(icono, color: Colors.white, size: 18),
                ),
                if (!esUltimo)
                  Expanded(
                    child: Container(
                      width: 2,
                      color: color.withOpacity(0.4),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 12),

          // Contenido
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: compacto ? 12 : 20),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: color.withOpacity(0.05),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: color.withOpacity(0.2)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      hito.titulo,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    if (hito.descripcion != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        hito.descripcion!,
                        style: TextStyle(fontSize: 13, color: Colors.grey[700]),
                      ),
                    ],
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 12,
                      runSpacing: 4,
                      children: [
                        if (hito.fecha != null)
                          _MetaItem(
                              icono: Icons.access_time,
                              texto: formatoFechaConHora(hito.fecha)),
                        if (hito.autor != null)
                          _MetaItem(icono: Icons.person, texto: hito.autor!),
                        if (hito.departamento != null)
                          _MetaItem(
                              icono: Icons.business, texto: hito.departamento!),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MetaItem extends StatelessWidget {
  final IconData icono;
  final String texto;

  const _MetaItem({required this.icono, required this.texto});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icono, size: 12, color: Colors.grey[600]),
        const SizedBox(width: 4),
        Text(
          texto,
          style: TextStyle(fontSize: 11, color: Colors.grey[600]),
        ),
      ],
    );
  }
}
```

---

## 5. Uso

### En `tramite_seguimiento_screen.dart` (pestaña Historial):

```dart
import '../../widgets/timeline_custom.dart';
import '../../widgets/timeline_custom_types.dart';

// Mapper privado
EstadoHito _mapearEstado(String tipo) => switch (tipo) {
  'aprobacion' || 'completacion' => EstadoHito.completado,
  'rechazo' => EstadoHito.rechazado,
  'cambio_estado' => EstadoHito.enCurso,
  _ => EstadoHito.pendiente,
};

List<HitoTimeline> _mapearHitos(List<EventoHistorico> eventos) {
  return eventos.map((e) => HitoTimeline(
    id: e.id,
    titulo: e.descripcion,
    fecha: e.fecha,
    autor: e.usuario,
    departamento: e.departamento,
    estado: _mapearEstado(e.tipo),
  )).toList();
}

// En el build de la pestaña historial:
Widget _buildPestanaHistorial() {
  if (estado!.historial.isEmpty) {
    return const Center(child: Text('No hay eventos en el historial'));
  }
  return TimelineCustom(hitos: _mapearHitos(estado!.historial));
}
```

---

## 6. Pasos

### Paso A — Crear los dos archivos

### Paso B — Reemplazar el `_buildPestanaHistorial` inline

Borrar el `Column` con `for evento in historial` que arma manualmente el timeline. Reemplazar por la llamada a `TimelineCustom`.

### Paso C — Migrar `detalle_linea_tiempo_screen.dart`

Esta pantalla actualmente puede estar usando la librería `timeline_tile`. Si es así, puede mantenerse (es un componente externo aceptable). Si tiene timeline a mano, migrar al `TimelineCustom` propio.

### Paso D — Verificar

- Pestaña Historial muestra eventos en orden con marcas de color correcto
- Línea conectora visible entre eventos
- Mete información (fecha, autor, departamento) bien formateada

---

## 7. Commit

```bash
git add .
git commit -m "feat(mobile/widgets): TimelineCustom propio para historial de tramites"
```

---

## Próximo paso

Continuar con **`08_reorganizar_features.md`**.
