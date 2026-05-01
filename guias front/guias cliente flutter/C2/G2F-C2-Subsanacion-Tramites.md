# Guía 2F-C2 — Subsanación de Trámites Devueltos (CU-17 para el Cliente)

**Ciclo 2 · Sistema de Gestión de Trámites - Frontend Mobile (Flutter)**

> 🎯 **Objetivo:** Permitir al Cliente visualizar los trámites que han sido "devueltos o con observaciones" por un Funcionario (resultado del CU-17), leer el motivo de la devolución y realizar la corrección completando de nuevo su sección en el expediente.

---

## 0. Requisitos

✅ La Guía 1F-C2 (Registro de Solicitud) debe estar implementada.
✅ Backend devolviendo trámites en estado "Observado" cuando un funcionario aplica CU-17.
✅ El backend G2-C2 implementado: `POST /api/expedientes/seccion/{seccionId}/completar`.

> **Nota sobre la lista de trámites:** El backend no expone un endpoint exclusivo para que el cliente liste sus trámites. Esta pantalla recibe la lista de trámites desde la pantalla de seguimiento (G4F-C1), que ya los tiene cargados. Se filtra localmente por `estadoActual == 'Observado'`.

---

## 1. Actualización de Servicios (API)

Reemplaza los métodos de subsanación en `lib/services/tramite_service.dart`:

```dart
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../utils/constants.dart';

class TramiteService {
  final String token;
  TramiteService(this.token);

  // Obtener el expediente completo de un trámite (para encontrar la sección activa)
  Future<Map<String, dynamic>> getExpediente(String tramiteId) async {
    final response = await http.get(
      Uri.parse('${Constants.BASE_URL}/expedientes/tramite/$tramiteId'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
    );

    if (response.statusCode == 200) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('Error al cargar el expediente del trámite');
    }
  }

  // Completar la sección activa con las correcciones del cliente (CU-17 — subsanación)
  //
  // ⚠️ LIMITACIÓN DEL BACKEND: El endpoint POST /api/expedientes/seccion/{id}/completar
  // tiene @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMINISTRADOR')").
  // Un usuario con rol CLIENTE recibirá HTTP 403 al llamar este endpoint.
  // Para habilitar la subsanación desde la app del cliente, el backend debe agregar
  // 'CLIENTE' a la lista de roles permitidos en ese @PreAuthorize.
  // Mientras tanto, este flujo solo funcionará si el usuario autenticado es FUNCIONARIO.
  Future<void> enviarCorreccion(String tramiteId, String notas) async {
    // Paso 1: Obtener el expediente para encontrar la sección activa
    final expediente = await getExpediente(tramiteId);
    final secciones = expediente['secciones'] as List<dynamic>;

    final seccionActiva = secciones.firstWhere(
      (s) => s['infoSeccion']['estado'] == 'en_curso',
      orElse: () => null,
    );

    if (seccionActiva == null) {
      throw Exception('No se encontró una sección activa para corregir en este trámite.');
    }

    final seccionId = seccionActiva['infoSeccion']['id'];

    // Paso 2: Completar la sección con las notas de corrección
    // Endpoint: POST /api/expedientes/seccion/{seccionId}/completar
    // Body: CompletarSeccionRequest { notasOperativas }
    final response = await http.post(
      Uri.parse('${Constants.BASE_URL}/expedientes/seccion/$seccionId/completar'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body: json.encode({
        'notasOperativas': notas,
      }),
    );

    if (response.statusCode != 200 && response.statusCode != 201) {
      throw Exception('Error al enviar la corrección al sistema.');
    }
  }
}
```

---

## 2. Pantalla 1: Lista de Trámites por Corregir

Esta pantalla recibe la lista de trámites del cliente (ya cargada en la pantalla de seguimiento G4F-C1) y filtra los que están en estado "Observado".

Crea `lib/screens/tramites/tramites_observados_screen.dart`:

```dart
import 'package:flutter/material.dart';
import '../../services/tramite_service.dart';
import 'realizar_correccion_screen.dart';

class TramitesObservadosScreen extends StatefulWidget {
  final String token;
  // Lista completa de trámites del cliente, obtenida desde la pantalla de seguimiento (G4F-C1)
  final List<dynamic> tramites;

  TramitesObservadosScreen({required this.token, required this.tramites});

  @override
  _TramitesObservadosScreenState createState() => _TramitesObservadosScreenState();
}

class _TramitesObservadosScreenState extends State<TramitesObservadosScreen> {
  late List<dynamic> _observados;

  @override
  void initState() {
    super.initState();
    // Filtrar localmente los trámites en estado "Observado"
    _observados = widget.tramites
        .where((t) => t['estadoActual'] == 'Observado')
        .toList();
  }

  void _recargar() {
    // Reaplica el filtro sobre la lista original (no re-fetcha)
    setState(() {
      _observados = widget.tramites
          .where((t) => t['estadoActual'] == 'Observado')
          .toList();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Trámites Devueltos / Observados')),
      body: _observados.isEmpty
          ? Center(child: Text('✅ No tienes trámites con observaciones.'))
          : ListView.builder(
              itemCount: _observados.length,
              itemBuilder: (context, index) {
                final t = _observados[index];
                return Card(
                  color: Colors.orange.shade50,
                  margin: EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  child: ListTile(
                    leading: Icon(Icons.warning_amber_rounded, color: Colors.orange),
                    title: Text(t['codigo'] ?? 'Sin código',
                        style: TextStyle(fontWeight: FontWeight.bold)),
                    subtitle: Text('Estado: ${t['estadoActual']}'),
                    trailing: ElevatedButton(
                      child: Text('Corregir'),
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => RealizarCorreccionScreen(
                              token: widget.token,
                              tramite: t,
                            ),
                          ),
                        ).then((_) => _recargar());
                      },
                    ),
                  ),
                );
              },
            ),
    );
  }
}
```

---

## 3. Pantalla 2: Formulario de Corrección

El cliente lee el estado del trámite y envía su corrección completando la sección activa del expediente.

Crea `lib/screens/tramites/realizar_correccion_screen.dart`:

```dart
import 'package:flutter/material.dart';
import '../../services/tramite_service.dart';

class RealizarCorreccionScreen extends StatefulWidget {
  final String token;
  final Map<String, dynamic> tramite;

  RealizarCorreccionScreen({required this.token, required this.tramite});

  @override
  _RealizarCorreccionScreenState createState() => _RealizarCorreccionScreenState();
}

class _RealizarCorreccionScreenState extends State<RealizarCorreccionScreen> {
  final _respuestaController = TextEditingController();
  bool _isSubmitting = false;

  void _enviar() async {
    if (_respuestaController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Debe ingresar un comentario o respuesta.')),
      );
      return;
    }

    setState(() => _isSubmitting = true);
    try {
      // Obtiene el expediente, localiza la sección "en_curso" y la completa
      await TramiteService(widget.token).enviarCorreccion(
        widget.tramite['id'],
        _respuestaController.text.trim(),
      );

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Corrección enviada correctamente.')),
      );
      Navigator.pop(context);
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString()), backgroundColor: Colors.red),
      );
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Subsanar Trámite')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Trámite: ${widget.tramite["codigo"]}',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            SizedBox(height: 10),
            Container(
              padding: EdgeInsets.all(12),
              color: Colors.red.shade50,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Motivo de devolución:',
                      style: TextStyle(fontWeight: FontWeight.bold, color: Colors.red)),
                  SizedBox(height: 5),
                  Text(
                    'Revise el historial del trámite para ver las observaciones del funcionario.',
                  ),
                ],
              ),
            ),
            SizedBox(height: 20),
            TextField(
              controller: _respuestaController,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: 'Respuesta / Subsanación',
                hintText: 'Ingrese sus observaciones o describa la corrección realizada...',
                border: OutlineInputBorder(), // corregido: era OutlineOutlineInputBorder (no existe)
              ),
            ),
            SizedBox(height: 20),
            Spacer(),
            ElevatedButton(
              onPressed: _isSubmitting ? null : _enviar,
              style: ElevatedButton.styleFrom(padding: EdgeInsets.symmetric(vertical: 16)),
              child: _isSubmitting
                  ? CircularProgressIndicator(color: Colors.white)
                  : Text('Reenviar Trámite', style: TextStyle(fontSize: 18)),
            )
          ],
        ),
      ),
    );
  }
}
```

---

## 4. Cierre del Cliente en Ciclo 2

Para el Cliente en su App Mobile de Flutter, el Ciclo 2 se resume en:
1. **Iniciar trámites bajo el amparo de Políticas de Negocio dinámicas** (Guía 1).
2. **Reaccionar al CU-17 ("Devolver trámite a corregir")** completando la sección activa del expediente con la corrección solicitada por el funcionario (Guía 2).

*Fin de las guías de Ciclo 2 para el Cliente (Flutter).*
