# Requisitos y Configuración para el Frontend (Flutter / GetX)

Ahora que el **Backend de Trámites** (Spring Boot + MongoDB) está desplegado y funcionando correctamente en la instancia EC2 de AWS, el proyecto Frontend en Flutter necesita conectarse a él.

Aquí tienes la lista de cosas que el frontend necesita configurar e implementar para funcionar correctamente con el nuevo backend en la nube:

## 1. URL Base del API (Endpoint)
El frontend debe apuntar a la IP pública de tu servidor de AWS en el puerto `8080`.

*   **URL Base (Producción):** `http://44.213.74.152:8080/api`
*   *(Nota: Asegúrate de que el puerto 8080 esté abierto en los Security Groups de tu instancia de AWS para que la app móvil pueda comunicarse).*

Te sugiero manejar esto mediante un archivo `.env` o una clase de constantes en Flutter:

```dart
// lib/core/config/app_constants.dart
class AppConstants {
  static const String apiBaseUrl = "http://44.213.74.152:8080/api";
  // Si luego agregas un dominio: "https://midominio.com/api"
}
```

## 2. Dependencias de Flutter (`pubspec.yaml`)
Dado que usas **GetX**, estas son las dependencias clave que tu app móvil necesitará para gestionar la comunicación y seguridad:

```yaml
dependencies:
  flutter:
    sdk: flutter
  get: ^4.6.6                 # Para gestión del estado, rutas y dependencias
  dio: ^5.4.0                 # (Opcional, o usar GetConnect) Cliente HTTP robusto
  flutter_secure_storage: ^9.0.0  # Para almacenar el JWT de forma segura
  shared_preferences: ^2.2.2  # Para datos en cache (preferencias de usuario)
```

## 3. Manejo de Autenticación (JWT)
El backend utiliza **Tokens JWT** (JSON Web Tokens) para las sesiones y roles de usuario.

**Flujo requerido en el Front:**
1.  **Login:** Enviar credenciales (usuario/email y contraseña) a la ruta de auth (ej. `/api/auth/login`).
2.  **Guardar el Token:** El backend responderá con un Bearer Token. El frontend debe guardarlo en `flutter_secure_storage`.
3.  **Interceptor / Middleware:** Para cada petición posterior a rutas protegidas (trámites, perfiles, etc.), el frontend debe enviar el encabezado:
    ```http
    Authorization: Bearer <TU_TOKEN_JWT>
    ```

**Ejemplo simple con GetConnect de GetX:**
```dart
class ApiProvider extends GetConnect {
  @override
  void onInit() {
    httpClient.baseUrl = 'http://44.213.74.152:8080/api';
    
    // Interceptor para inyectar el token en cada petición
    httpClient.addRequestModifier((request) async {
      final token = await AuthService.to.getToken(); // tu servicio de storage
      if (token != null) {
        request.headers['Authorization'] = 'Bearer $token';
      }
      return request;
    });
  }
}
```

## 4. Configuración de Seguridad para Tráfico HTTP (Solo si es HTTP y no HTTPS)
Como tu IP no tiene certificado SSL/HTTPS configurado aún (`http://...`), Android e iOS bloquearán las peticiones HTTP claras por defecto. Para que funcione en móviles:

### Para Android
En `android/app/src/main/AndroidManifest.xml`, añade `android:usesCleartextTraffic="true"` en la etiqueta `<application>`:
```xml
<application
    android:usesCleartextTraffic="true"
    android:label="Tramites App"
    android:icon="@mipmap/ic_launcher">
```

### Para iOS
En `ios/Runner/Info.plist`, debes habilitar App Transport Security (ATS):
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

## 5. (Back/Front) Consideración: Permisos CORS
Si en algún momento corres el frontend desde la Web (Flutter Web) en vez del móvil, el Backend (Spring Boot) reportará un error **CORS**. Para aplicaciones móviles nativas (Android/iOS) no hay problema, pero tenlo en cuenta. El Backend debería estar configurado para permitir peticiones desde orígenes cruzados usando la anotación `@CrossOrigin` o un filtro global de CORS en Spring Security.

## 6. Endpoints Mínimos para Validar la Conexión
Puedes utilizar el siguiente endpoint en Flutter para probar si la conexión es exitosa, es una ruta pública que probamos hace unos minutos desde el server:
*   **GET** `http://44.213.74.152:8080/api/health`

Si usas dio o get (HTTP), tu respuesta debería lucir como:
```json
{
  "status": "UP",
  "service": "tramites-backend"
}
```