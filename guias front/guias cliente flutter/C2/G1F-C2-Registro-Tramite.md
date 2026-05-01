# Guía 1F-C2 — Registro de Solicitud de Trámite (CU-07)

**Ciclo 2 · Sistema de Gestión de Trámites - Frontend Mobile (Flutter)**

> 🎯 **Objetivo:** Permitir al Cliente Final iniciar un trámite de forma digital de manera real, seleccionando una política de negocio específica para que el motor de workflow del backend (CU-07) asigne el proceso al área correspondiente ("Iniciado").

---

## 0. Requisitos

✅ Backend Ciclo 2 ejecutándose en `http://localhost:8080`.
✅ App Flutter del Ciclo 1 base configurada (Autenticación JWT del Cliente funcionando).
✅ Dependencias sugeridas en Flutter: `http`, `provider` (o el gestor de estado que uses).

---

## 1. Actualización de Servicios (API)

En el Ciclo 1 utilizábamos endpoints básicos. Ahora debemos conectarnos con el motor de workflow del Ciclo 2.

Crea o actualiza el archivo `lib/services/tramite_service.dart`:

```dart
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../utils/constants.dart'; // Aquí defines tu BASE_URL = 'http://10.0.2.2:8080/api' o similar

class TramiteService {
  final String token;
  TramiteService(this.token);

  // Obtener el catálogo de políticas de negocio disponibles para iniciar
  Future<List<dynamic>> getPoliticasDisponibles() async {
    final response = await http.get(
      Uri.parse('${Constants.BASE_URL}/politicas'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
    );

    if (response.statusCode == 200) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('Error al cargar políticas de negocio');
    }
  }

  // Iniciar un nuevo trámite a partir de una política (CU-07)
  Future<Map<String, dynamic>> iniciarTramite(String politicaId, String clienteId) async {
    final response = await http.post(
      Uri.parse('${Constants.BASE_URL}/tramites/iniciar'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body: json.encode({
        'politicaId': politicaId,
        'clienteId': clienteId, // campo correcto según IniciarTramiteRequest del backend
      }),
    );

    if (response.statusCode == 200 || response.statusCode == 201) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('Error al iniciar el trámite. Verifique la conexión.');
    }
  }
}
```

---

## 2. Pantalla 1: Catálogo de Tipos de Trámite (Políticas)

El Cliente primero debe seleccionar qué tipo de trámite desea iniciar.

Crea `lib/screens/tramites/catalogo_tramites_screen.dart`:

```dart
import 'package:flutter/material.dart';
import '../../services/tramite_service.dart';
import 'iniciar_tramite_screen.dart';

class CatalogoTramitesScreen extends StatefulWidget {
  final String token;
  final String clienteId; // ID del usuario autenticado, extraído del JWT en el Login y pasado aquí
  CatalogoTramitesScreen({required this.token, required this.clienteId});

  @override
  _CatalogoTramitesScreenState createState() => _CatalogoTramitesScreenState();
}

class _CatalogoTramitesScreenState extends State<CatalogoTramitesScreen> {
  late Future<List<dynamic>> _politicasFuture;

  @override
  void initState() {
    super.initState();
    _politicasFuture = TramiteService(widget.token).getPoliticasDisponibles();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Seleccionar Tipo de Trámite')),
      body: FutureBuilder<List<dynamic>>(
        future: _politicasFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return Center(child: CircularProgressIndicator());
          } else if (snapshot.hasError) {
            return Center(child: Text('Excepción: No hay políticas disponibles o error de red'));
          } else if (!snapshot.hasData || snapshot.data!.isEmpty) {
            return Center(child: Text('No hay políticas de negocio configuradas.'));
          }

          final politicas = snapshot.data!;

          return ListView.builder(
            itemCount: politicas.length,
            itemBuilder: (context, index) {
              final pol = politicas[index];
              return Card(
                margin: EdgeInsets.all(8.0),
                child: ListTile(
                  leading: Icon(Icons.description, color: Colors.blue),
                  title: Text(pol['nombre'] ?? 'Sin nombre'),
                  subtitle: Text(pol['descripcion'] ?? 'Trámite disponible'),
                  trailing: Icon(Icons.arrow_forward_ios),
                  onTap: () {
                    // Navegar a la pantalla de confirmación/inicio
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => IniciarTramiteScreen(
                          token: widget.token,
                          clienteId: widget.clienteId, // se propaga desde el login
                          politicaId: pol['id'],
                          politicaNombre: pol['nombre'],
                        ),
                      ),
                    );
                  },
                ),
              );
            },
          );
        },
      ),
    );
  }
}
```

---

## 3. Pantalla 2: Vista de Confirmación e Inicio de Trámite

Una vez seleccionada la política, permitimos al usuario confirmar y disparar el inicio en el Backend.

Crea `lib/screens/tramites/iniciar_tramite_screen.dart`:

```dart
import 'package:flutter/material.dart';
import '../../services/tramite_service.dart';
// Importa tu gestor de autenticación para obtener el clienteId

class IniciarTramiteScreen extends StatefulWidget {
  final String token;
  final String clienteId;
  final String politicaId;
  final String politicaNombre;

  IniciarTramiteScreen({
    required this.token,
    required this.clienteId,
    required this.politicaId,
    required this.politicaNombre,
  });

  @override
  _IniciarTramiteScreenState createState() => _IniciarTramiteScreenState();
}

class _IniciarTramiteScreenState extends State<IniciarTramiteScreen> {
  bool _isLoading = false;

  void _iniciar() async {
    setState(() => _isLoading = true);
    try {
      final result = await TramiteService(widget.token)
          .iniciarTramite(widget.politicaId, widget.clienteId);
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Trámite iniciado con éxito: ${result["codigo"]}')),
      );
      
      // Volver al Dashboard (Home)
      Navigator.popUntil(context, (route) => route.isFirst);

    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString()), backgroundColor: Colors.red),
      );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Confirmar Trámite')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Está a punto de iniciar:', style: TextStyle(fontSize: 18)),
            SizedBox(height: 10),
            Text(widget.politicaNombre, style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
            SizedBox(height: 20),
            Text('Una vez iniciado, este trámite será derivado automáticamente al área de Atención al Cliente para su revisión.'),
            Spacer(),
            ElevatedButton(
              onPressed: _isLoading ? null : _iniciar,
              style: ElevatedButton.styleFrom(
                padding: EdgeInsets.symmetric(vertical: 16),
              ),
              child: _isLoading 
                ? CircularProgressIndicator(color: Colors.white)
                : Text('Confirmar e Iniciar Trámite', style: TextStyle(fontSize: 18)),
            )
          ],
        ),
      ),
    );
  }
}
```

---

## 4. Post-Condición y Verificación

1. Ingresa a la app Flutter como Cliente.
2. Ve al "Catálogo de Trámites". Deberías ver las Políticas de Negocio que están registradas en la Base de Datos provista por el endpoint `GET /api/politicas`.
3. Selecciona una e inicia el trámite.
4. El backend devolverá el código del Trámite (ej: "TRM-2026-008") y se derivará de forma automática según la primera Actividad de la Política en el motor de Workflow (CU-07 cumplido en el frontend).
