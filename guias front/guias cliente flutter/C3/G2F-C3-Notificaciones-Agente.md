# Guía 2F-C3 — Notificaciones y Asistente Inteligente (CU-28, CU-31)

**Ciclo 3 · Sistema de Gestión de Trámites - Frontend Mobile (Flutter)**

> 🎯 **Objetivo:** Informar al Cliente proactivamente mediante una bandeja de notificaciones sobre el estado de sus trámites (CU-28), e integrar un agente conversacional interactivo para responder a sus dudas en lenguaje natural (CU-31).

---

## 0. Requisitos

✅ La Guía 1F-C3 debe estar implementada.
✅ Backend corriendo en `http://localhost:8080`.
✅ (Opcional) Firebase Cloud Messaging configurado en Flutter si se desea que las notificaciones lleguen aunque la app esté cerrada (Push Notifications). En este caso construiremos la **bandeja interna de la app**.

---

## 1. Actualización de Servicios (API)

Integramos las llamadas a los endpoints del sistema de notificaciones y del agente de IA.

En `lib/services/tramite_service.dart` (o crear un `comunicacion_service.dart`):

```dart
// ... importaciones ...

  // CU-28: Obtener la lista de notificaciones (alertas de avance, finalización, devolución)
  Future<List<dynamic>> getMisNotificaciones() async {
    final response = await http.get(
      Uri.parse('${Constants.BASE_URL}/notificaciones/mis-notificaciones'),
      headers: {
        'Authorization': 'Bearer $token',
      },
    );

    if (response.statusCode == 200) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('No se pudieron cargar las notificaciones.');
    }
  }

  // CU-31: Enviar pregunta al Agente Conversacional
  // Endpoint real: POST /api/agente/consultar  (ver G5-C3 backend)
  // Body: AgenteRequest { consulta, moduloActivo, tramiteIdOpcional? }
  Future<Map<String, dynamic>> consultarAgenteIA(String pregunta, String contexto) async {
    final response = await http.post(
      Uri.parse('${Constants.BASE_URL}/agente/consultar'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body: json.encode({
        'consulta': pregunta,        // campo real de AgenteRequest (no 'pregunta')
        'moduloActivo': contexto,    // campo real de AgenteRequest (no 'pantallaActual')
        // 'tramiteIdOpcional': tramiteId  // pasar si se tiene contexto de trámite específico
      }),
    );

    if (response.statusCode == 200) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      // Retornar mensaje de fallback para que el chat no se rompa
      return {'respuesta': 'Lo siento, el asistente virtual no está disponible en este momento.'};
    }
  }
```

---

## 2. Pantalla 1: Bandeja de Notificaciones (CU-28)

En el menú lateral o en la barra inferior (BottomNavigationBar), añadiremos un icono de "Campana" que dirija a esta pantalla.

Crea `lib/screens/comunicacion/notificaciones_screen.dart`:

```dart
import 'package:flutter/material.dart';
import '../../services/tramite_service.dart'; // O comunicacion_service

class NotificacionesScreen extends StatefulWidget {
  final String token;
  NotificacionesScreen({required this.token});

  @override
  _NotificacionesScreenState createState() => _NotificacionesScreenState();
}

class _NotificacionesScreenState extends State<NotificacionesScreen> {
  late Future<List<dynamic>> _notificacionesFuture;

  @override
  void initState() {
    super.initState();
    _cargarNotificaciones();
  }

  void _cargarNotificaciones() {
    setState(() {
      _notificacionesFuture = TramiteService(widget.token).getMisNotificaciones();
    });
  }

  // Tipos reales del backend (ver G0-C3 tabla de valores):
  // cambio_estado | asignacion | sla_vencido | observacion
  IconData _getIconoPorTipo(String tipo) {
    switch (tipo) {
      case 'cambio_estado': return Icons.trending_flat;
      case 'asignacion':    return Icons.verified;
      case 'observacion':   return Icons.history;
      case 'sla_vencido':   return Icons.warning_amber;
      default:              return Icons.notifications_active;
    }
  }

  Color _getColorPorTipo(String tipo) {
    if (tipo == 'asignacion')  return Colors.green;
    if (tipo == 'observacion') return Colors.orange;
    if (tipo == 'sla_vencido') return Colors.red;
    return Colors.blue;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Mis Notificaciones')),
      body: FutureBuilder<List<dynamic>>(
        future: _notificacionesFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) return Center(child: CircularProgressIndicator());
          if (snapshot.hasError) return Center(child: Text('Error al obtener notificaciones.'));
          if (!snapshot.hasData || snapshot.data!.isEmpty) return Center(child: Text('📭 No tienes notificaciones recientes.'));

          final notifs = snapshot.data!;
          return RefreshIndicator(
            onRefresh: () async => _cargarNotificaciones(),
            child: ListView.separated(
              itemCount: notifs.length,
              separatorBuilder: (context, index) => Divider(height: 1),
              itemBuilder: (context, index) {
                final n = notifs[index];
                bool leida = n['leida'] ?? false;

                return Container(
                  color: leida ? Colors.transparent : Colors.blue.withOpacity(0.05),
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: _getColorPorTipo(n['tipo']),
                      child: Icon(_getIconoPorTipo(n['tipo']), color: Colors.white),
                    ),
                    title: Text(n['titulo'] ?? 'Aviso', style: TextStyle(fontWeight: leida ? FontWeight.normal : FontWeight.bold)),
                    subtitle: Text(n['mensaje'] ?? ''),
                    trailing: Text(
                      _formatearFecha(n['fechaCreacion']),
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    onTap: () {
                      // Opcional: Llamar endpoint para marcar como leída y navegar al detalle del trámite.
                    },
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }

  String _formatearFecha(String? isoDate) {
    if (isoDate == null) return '';
    final dt = DateTime.parse(isoDate).toLocal();
    return '${dt.day}/${dt.month}/${dt.year} ${dt.hour}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
```

---

## 3. Pantalla 2: Botón Flotante del Agente de IA (CU-31)

El asistente no será una pantalla completa, sino un **Modal Bottom Sheet** (Panel que sube desde abajo) que se puede abrir desde cualquier lugar de la app.

Crea `lib/widgets/chat_agente_ia.dart`:

```dart
import 'package:flutter/material.dart';
import '../services/tramite_service.dart';

class ChatAgenteIA extends StatefulWidget {
  final String token;
  final String pantallaActual; // Para darle contexto a la IA (ej. "Catálogo", "Expediente")

  ChatAgenteIA({required this.token, required this.pantallaActual});

  @override
  _ChatAgenteIAState createState() => _ChatAgenteIAState();
}

class _ChatAgenteIAState extends State<ChatAgenteIA> {
  final List<Map<String, dynamic>> _mensajes = [];
  final TextEditingController _msgController = TextEditingController();
  bool _esperandoRespuesta = false;

  @override
  void initState() {
    super.initState();
    // Mensaje inicial del agente
    _mensajes.add({
      'texto': '¡Hola! Soy tu asistente virtual. Veo que estás en ${widget.pantallaActual}. ¿En qué te puedo ayudar hoy?',
      'esCliente': false
    });
  }

  void _enviarPregunta() async {
    if (_msgController.text.trim().isEmpty) return;

    String pregunta = _msgController.text.trim();
    setState(() {
      _mensajes.add({'texto': pregunta, 'esCliente': true});
      _msgController.clear();
      _esperandoRespuesta = true;
    });

    try {
      final res = await TramiteService(widget.token).consultarAgenteIA(pregunta, widget.pantallaActual);
      
      setState(() {
        _mensajes.add({'texto': res['respuesta'] ?? 'Sin respuesta', 'esCliente': false});
        _esperandoRespuesta = false;
      });
    } catch (e) {
      setState(() {
        _mensajes.add({'texto': 'Error de conexión con el asistente.', 'esCliente': false});
        _esperandoRespuesta = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.75, // Ocupa 3/4 de la pantalla
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20))
      ),
      child: Column(
        children: [
          // Cabecera
          Container(
            padding: EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.indigo,
              borderRadius: BorderRadius.vertical(top: Radius.circular(20))
            ),
            child: Row(
              children: [
                Icon(Icons.smart_toy, color: Colors.white),
                SizedBox(width: 10),
                Text('Asistente de Soporte', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
                Spacer(),
                IconButton(
                  icon: Icon(Icons.close, color: Colors.white),
                  onPressed: () => Navigator.pop(context),
                )
              ],
            ),
          ),
          
          // Lista de Mensajes
          Expanded(
            child: ListView.builder(
              padding: EdgeInsets.all(16),
              itemCount: _mensajes.length,
              itemBuilder: (context, index) {
                final m = _mensajes[index];
                bool isMe = m['esCliente'];
                return Align(
                  alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    margin: EdgeInsets.only(bottom: 10),
                    padding: EdgeInsets.all(12),
                    constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.7),
                    decoration: BoxDecoration(
                      color: isMe ? Colors.blue.shade100 : Colors.grey.shade200,
                      borderRadius: BorderRadius.circular(15)
                    ),
                    child: Text(m['texto']),
                  ),
                );
              },
            ),
          ),

          // Indicador "Escribiendo..."
          if (_esperandoRespuesta)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text('El asistente está escribiendo...', style: TextStyle(color: Colors.grey, fontStyle: FontStyle.italic)),
              ),
            ),

          // Input de Texto
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _msgController,
                    decoration: InputDecoration(
                      hintText: 'Escribe tu consulta...',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(20)),
                      contentPadding: EdgeInsets.symmetric(horizontal: 15)
                    ),
                    onSubmitted: (_) => _enviarPregunta(),
                  ),
                ),
                SizedBox(width: 8),
                CircleAvatar(
                  backgroundColor: Colors.indigo,
                  child: IconButton(
                    icon: Icon(Icons.send, color: Colors.white),
                    onPressed: _esperandoRespuesta ? null : _enviarPregunta,
                  ),
                )
              ],
            ),
          )
        ],
      ),
    );
  }
}
```

---

## 4. Invocación del Agente

Para lanzar el Asistente Inteligente en cualquier pantalla de la app (ej. el Dashboard), añade un `FloatingActionButton`:

```dart
  floatingActionButton: FloatingActionButton(
    backgroundColor: Colors.indigo,
    child: Icon(Icons.support_agent),
    onPressed: () {
      showModalBottomSheet(
        context: context,
        isScrollControlled: true, // Importante para que el bottom sheet suba casi toda la pantalla
        backgroundColor: Colors.transparent,
        builder: (ctx) => ChatAgenteIA(
          token: widget.token, 
          pantallaActual: "Pantalla Principal (Mis Trámites)"
        ),
      );
    },
  ),
```

---

## 5. Fin del Ciclo 3 para Flutter

* Los usuarios ven los eventos que el backend les dispara según plantillas procesadas en el CU-27.
* Reciben notificaciones de alertas en la bandeja (CU-28).
* Tienen el bot integrado conversando en lenguaje natural consultando a FastAPI.