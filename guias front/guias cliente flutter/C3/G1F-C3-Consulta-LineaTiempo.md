# Guía 1F-C3 — Consulta Interactiva y Cancelación de Trámite (CU-21, CU-19)

**Ciclo 3 · Sistema de Gestión de Trámites - Frontend Mobile (Flutter)**

> 🎯 **Objetivo:** Proporcionar al Cliente una línea de tiempo (timeline) detallada en tiempo real de su trámite atravesando los departamentos (CU-21) y habilitar la opción de desistir o cancelar un trámite activo (CU-19).

---

## 0. Requisitos

✅ Autenticación y flujos del Ciclo 2 en Flutter completados.
✅ Paquete sugerido para la línea de tiempo: `timeline_tile` (añadir en `pubspec.yaml`).
✅ Backend Ciclo 3 expuesto y procesando historiales con el Motor de Workflow.

---

## 1. Actualización de Servicios (API)

Agregaremos al proveedor de servicios las peticiones hacia el controlador del **Ciclo de Vida (Ciclo 3)**.

En `lib/services/tramite_service.dart`:

```dart
// ... importaciones genéricas ...

  // CU-21: Consultar estado del trámite (Línea de tiempo / Historial completo)
  // Endpoint real: GET /api/tramites/{id}/linea-tiempo  (ver G1-C3 backend)
  Future<Map<String, dynamic>> getLineaTiempoTramite(String tramiteId) async {
    final response = await http.get(
      Uri.parse('${Constants.BASE_URL}/tramites/$tramiteId/linea-tiempo'),
      headers: {
        'Authorization': 'Bearer $token',
      },
    );

    if (response.statusCode == 200) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('No se pudo cargar la línea de tiempo del trámite.');
    }
  }

  // CU-19: Cancelar trámite (desistimiento voluntario por parte del cliente)
  Future<void> cancelarTramite(String tramiteId, String motivoCancelacion) async {
    final response = await http.post(
      Uri.parse('${Constants.BASE_URL}/tramites/$tramiteId/cancelar'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body: json.encode({
        'motivo': motivoCancelacion
      }),
    );

    if (response.statusCode != 200) {
      throw Exception('El trámite ya no puede ser cancelado en este momento.');
    }
  }
```

---

## 2. Pantalla: Detalle del Trámite y Línea de Tiempo (CU-21)

Esta pantalla mostrará el progreso visual del trámite desde que se inició.

Primero, añade `timeline_tile: ^2.0.0` a tu `pubspec.yaml` e instala.

Crea `lib/screens/tramites/detalle_linea_tiempo_screen.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:timeline_tile/timeline_tile.dart';
import '../../services/tramite_service.dart';

class DetalleLineaTiempoScreen extends StatefulWidget {
  final String token;
  final String tramiteId;
  final String codigo;

  DetalleLineaTiempoScreen({
    required this.token, 
    required this.tramiteId, 
    required this.codigo
  });

  @override
  _DetalleLineaTiempoScreenState createState() => _DetalleLineaTiempoScreenState();
}

class _DetalleLineaTiempoScreenState extends State<DetalleLineaTiempoScreen> {
  late Future<Map<String, dynamic>> _lineaTiempoFuture;

  @override
  void initState() {
    super.initState();
    _lineaTiempoFuture = TramiteService(widget.token).getLineaTiempoTramite(widget.tramiteId);
  }

  // Lógica del CU-19 para mostrar un diálogo de cancelación
  void _mostrarDialogoCancelar() {
    final _motivoController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('⚠️ Cancelar Trámite', style: TextStyle(color: Colors.red)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('¿Estás seguro de que deseas desistir de este trámite? El proceso se detendrá inmediatamente en el departamento actual.'),
            SizedBox(height: 15),
            TextField(
              controller: _motivoController,
              decoration: InputDecoration(
                labelText: 'Motivo de cancelación (Opcional)',
                border: OutlineInputBorder()
              ),
            )
          ],
        ),
        actions: [
          TextButton(
            child: Text('Cerrar'),
            onPressed: () => Navigator.pop(ctx),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
            child: Text('Confirmar Cancelación'),
            onPressed: () async {
              try {
                await TramiteService(widget.token).cancelarTramite(widget.tramiteId, _motivoController.text);
                Navigator.pop(ctx); // Cierra diálogo
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Trámite cancelado.')));
                setState(() { _lineaTiempoFuture = TramiteService(widget.token).getLineaTiempoTramite(widget.tramiteId); }); // Recargamos la línea para ver estado cancelado
              } catch (e) {
                Navigator.pop(ctx);
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString()), backgroundColor: Colors.red));
              }
            },
          )
        ],
      )
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Seguimiento: ${widget.codigo}'),
        actions: [
          // Botón de Cancelación disponible si el trámite está activo (CU-19)
          IconButton(
            icon: Icon(Icons.cancel, color: Colors.white),
            tooltip: 'Cancelar Trámite',
            onPressed: _mostrarDialogoCancelar,
          )
        ],
      ),
      body: FutureBuilder<Map<String, dynamic>>(
        future: _lineaTiempoFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return Center(child: CircularProgressIndicator());
          } else if (snapshot.hasError) {
            return Center(child: Text('Error al cargar la línea de tiempo.'));
          }

          // Respuesta del backend: LineaTiempoResponse { tramiteId, estadoActual, hitos: [HitoDTO] }
          // HitoDTO campos reales: fecha, estado, departamento, actor, esActual
          final data = snapshot.data!;
          final estadoGlobal = data['estadoActual'] as String? ?? 'Desconocido';
          final hitos = data['hitos'] as List<dynamic>? ?? [];

          return Column(
            children: [
              // Encabezado
              Container(
                padding: EdgeInsets.all(16),
                color: Colors.blue.shade50,
                child: Row(
                  children: [
                    Icon(Icons.info_outline, color: Colors.blue),
                    SizedBox(width: 8),
                    Text('Estado Global:', style: TextStyle(fontWeight: FontWeight.bold)),
                    Spacer(),
                    Chip(
                      label: Text(estadoGlobal, style: TextStyle(color: Colors.white)),
                      // 'Cancelado por el usuario' es el estado real de TramiteCicloVidaService
                      backgroundColor: estadoGlobal == 'Cancelado por el usuario' || estadoGlobal == 'Rechazado'
                          ? Colors.red
                          : estadoGlobal == 'Aprobado'
                              ? Colors.green
                              : Colors.orange,
                    )
                  ],
                ),
              ),
              Expanded(
                // Despliegue de Timeline_Tile (CU-21)
                child: ListView.builder(
                  itemCount: hitos.length,
                  padding: EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                  itemBuilder: (context, index) {
                    final hito = hitos[index];
                    final bool esActual = hito['esActual'] == true;

                    return TimelineTile(
                      isFirst: index == 0,
                      isLast: index == hitos.length - 1,
                      indicatorStyle: IndicatorStyle(
                        width: 25,
                        color: esActual ? Colors.orange : Colors.green,
                        iconStyle: IconStyle(
                          iconData: esActual ? Icons.hourglass_top : Icons.check,
                          color: Colors.white,
                        ),
                      ),
                      beforeLineStyle: LineStyle(
                        color: esActual ? Colors.orange : Colors.green,
                      ),
                      endChild: Container(
                        constraints: BoxConstraints(minHeight: 100),
                        padding: EdgeInsets.all(12),
                        child: Card(
                          elevation: 2,
                          child: Padding(
                            padding: EdgeInsets.all(12),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                // "estado" es el campo real de HitoDTO (no "actividad")
                                Text(
                                  '${hito['estado'] ?? 'Sin estado'}',
                                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                                ),
                                SizedBox(height: 5),
                                if (hito['departamento'] != null)
                                  Text('🏢 ${hito['departamento']}'),
                                // "actor" es el campo real de HitoDTO (no "funcionario")
                                if (hito['actor'] != null)
                                  Text(
                                    '👤 Actor: ${hito['actor']}',
                                    style: TextStyle(color: Colors.grey.shade700, fontSize: 12),
                                  ),
                                SizedBox(height: 5),
                                // "fecha" es el campo real de HitoDTO (no "fechaFin")
                                if (hito['fecha'] != null)
                                  Text(
                                    '🗓️ Fecha: ${hito['fecha']}',
                                    style: TextStyle(fontSize: 12, color: Colors.green),
                                  ),
                                if (esActual)
                                  Text(
                                    '⏳ En proceso actualmente',
                                    style: TextStyle(fontSize: 12, color: Colors.orange.shade700),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              )
            ],
          );
        },
      ),
    );
  }
}
```

---

## 3. Revisión del Flujo

1. **(CU-21) Línea de tiempo visual:** El Cliente ya no tiene un texto estático, sino un árbol visual con tarjetas (cards) que muestran exactamente qué departamentos y qué personas tienen el documento, así como tiempos transcurridos.
2. **(CU-19) Cancelar trámite:** Si el usuario presiona el botón X en la barra (ActionBar), detendrá inmediatamente el procesamiento del workflow en el backend y la línea de tiempo se actualizará a rojo con el estado "Cancelado por el usuario".