# Requisitos y Configuración Específica para la App Móvil (Flutter / GetX)

Al tratarse de una aplicación móvil para **gestión de trámites**, hay configuraciones nativas y de arquitectura necesarias para que la app funcione fluidamente en los dispositivos de los usuarios.

Aquí tienes la lista de requisitos enfocada 100% en el entorno móvil (Android / iOS):

## 1. Permisos Nativos (Dispositivo)
Los trámites usualmente requieren subir documentos o fotos. Debes solicitar los permisos adecuados en ambos sistemas operativos.

### Android (`android/app/src/main/AndroidManifest.xml`)
Dependiendo de si necesitas tomar fotos o subir Pdfs:
```xml
<!-- Permiso de Internet (Obligatorio para consumir el API) -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Para adjuntar fotos de trámites -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<!-- Si usas Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_DOCUMENT" />
```

### iOS (`ios/Runner/Info.plist`)
Apple exige una justificación escrita para cada permiso que solicites:
```xml
<key>NSCameraUsageDescription</key>
<string>Necesitamos acceso a la cámara para tomar fotos de los documentos del trámite.</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>Necesitamos acceso a la galería para que puedas adjuntar archivos a tu trámite.</string>
```

## 2. Manejo de Conectividad (Offline / Online)
Los usuarios móviles pierden señal frecuentemente. La app debe manejar estos cortes sin "crashear".
**Dependencia recomendada:** `connectivity_plus` y `dio` (para manejar interceptores de timeout).

**Sugerencia GetX:**
Crea un `NetworkController` global que escuche los cambios de conexión y muestre un `Get.snackbar` si el usuario se queda sin internet mientras llena un formulario de trámite.

```yaml
dependencies:
  connectivity_plus: ^5.0.2
```

## 3. Arquitectura Limpia con GetX
Para mantener el proyecto móvil escalable con tu nuevo backend, organiza así tu código:

*   `/lib`
    *   `/data`: Proveedores de API (conexiones a `http://44.213.74.152:8080`), modelos y repositorios.
    *   `/modules`: Módulos principales (Login, Dashboard, TramiteDetalle). Cada módulo debe tener:
        *   `xxxx_view.dart` (La interfaz UI)
        *   `xxxx_controller.dart` (Lógica de negocio y llamadas al API)
        *   `xxxx_binding.dart` (Inyección de dependencias para memoria eficiente)
    *   `/routes`: Definición de `GetPage` para navegar.
    *   `/core`: Constantes, temas, interceptores HTTP, helpers de seguridad.

## 4. Timeout de Peticiones HTTP
El servidor EC2 puede tener picos de latencia. Configura un `connectTimeout` y `receiveTimeout` explícito en tus llamadas.

Si usas `GetConnect`:
```dart
class Api extends GetConnect {
  @override
  void onInit() {
    httpClient.baseUrl = 'http://44.213.74.152:8080/api';
    httpClient.timeout = const Duration(seconds: 15); // Evitar que la app se quede cargando eternamente
  }
}
```

## 5. Notificaciones Push (Firebase Cloud Messaging)
En un sistema de trámites, es vital notificar al usuario cuando:
- "Tu trámite ha sido aprobado"
- "Necesitas corregir un documento"

**Pasos requeridos:**
1. Crear proyecto en [Firebase Console](https://console.firebase.google.com/).
2. Añadir dependencias: `firebase_core` y `firebase_messaging`.
3. Guardar el **FCM Token** (Device Token) del celular y enviarlo al Backend de Spring Boot (probablemente necesites crear un endpoint para esto: `POST /api/usuarios/device-token`).
4. Cuando el estado de un trámite cambia en Spring Boot, el backend envía la señal a Firebase y Firebase hace sonar el teléfono del usuario.

## 6. Tráfico HTTP (Recordatorio crítico móvil)
Si no configuras esto, las pantallas que traigan datos del API se quedarán en blanco en producción, ya que Apple y Google bloquean llamadas `http://` por seguridad.

*   **Android:** Agregar `android:usesCleartextTraffic="true"` en `AndroidManifest.xml`.
*   **iOS:** Agregar configuración de `NSAppTransportSecurity` en el `Info.plist`.

*(Cuando configures un dominio web con SSL/HTTPS en el futuro, podrás retirar estas excepciones)*.