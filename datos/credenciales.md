# Credenciales de Prueba — Sistema de Gestión de Trámites

## 🔐 Credenciales del Sistema

### Cliente
```json
{
  "email": "cliente@cre.bo",
  "password": "cliente12345",
  "nombre": "Juan",
  "apellido": "Pérez",
  "tipo": "cliente",
  "rol": "Cliente"
}
```

### Funcionario (Técnico)
```json
{
  "email": "funcionario@cre.bo",
  "password": "func12345",
  "nombre": "Carlos",
  "apellido": "Lima",
  "tipo": "funcionario",
  "rol": "Funcionario",
  "departamento": "TEC (Área Técnica)"
}
```

### Administrador
```json
{
  "email": "admin@cre.bo",
  "password": "admin12345",
  "nombre": "Admin",
  "apellido": "Sistema",
  "tipo": "administrador",
  "rol": "Administrador"
}
```

### SuperUser (para tareas críticas)
```json
{
  "email": "superuser@cre.bo",
  "password": "super12345",
  "nombre": "Super",
  "apellido": "Usuario",
  "tipo": "administrador",
  "rol": "SuperUser"
}
```

---

## 🌐 URLs Base

### En Windows (desarrollo)
| Componente | URL |
|-----------|-----|
| API Backend | `http://localhost:8080` |
| Swagger/OpenAPI | `http://localhost:8080/swagger-ui.html` |
| Mongo Express | `http://localhost:8081` |
| MongoDB | `mongodb://admin:12345678@localhost:27017/tramites_db?authSource=admin` |

### En Android Emulator (Flutter)
```
API Backend: http://10.0.2.2:8080
```
> `10.0.2.2` es el alias especial que Android usa para acceder al host desde el emulador

### En Dispositivo Físico (Flutter)
Tu IP Windows:
```
192.168.1.107
```

Usa en Flutter:
```
API Backend: http://192.168.1.107:8080
```

---

## 🔑 Endpoints de Autenticación

### Login
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@cre.bo",
  "password": "admin12345"
}
```

**Respuesta:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tipoToken": "Bearer",
  "email": "admin@cre.bo",
  "nombre": "Admin",
  "tipo": "administrador"
}
```

### Registro de Cliente
```bash
POST /api/auth/register-cliente
Content-Type: application/json

{
  "nombre": "Juan",
  "apellido": "Pérez",
  "email": "juan.perez@example.com",
  "password": "password123",
  "passwordConfirm": "password123"
}
```

---

## 📋 Departamentos del Sistema (CRE)

| ID | Código | Nombre | Abreviatura |
|----|--------|--------|-------------|
| 1 | ATC | Atención al Cliente | ATC |
| 2 | TEC | Área Técnica | TEC |
| 3 | LEG | Área Legal | LEG |
| 4 | OPE | Operaciones | OPE |

---

## 🎯 Política de Negocio Activa

**Nombre:** Nueva conexión residencial  
**Estado:** Activa  
**Versión:** 1.0  
**SLA Máximo:** 15 días hábiles

---

## 🧪 Pruebas Rápidas en Terminal

### 1. Verificar salud del sistema
```bash
curl http://localhost:8080/api/health
```

### 2. Login como admin
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cre.bo","password":"admin12345"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "Token: $TOKEN"
```

### 3. Obtener perfil autenticado
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/usuarios/me
```

### 4. Listar departamentos
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/departamentos
```

### 5. Listar políticas activas
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/politicas?soloActivas=true
```

---

---

## 🐛 Troubleshooting — Error al iniciar sesión en Flutter

### ❌ Error: `Connection refused` en localhost

**Causa:** La app Flutter intenta conectarse a `localhost:8080` desde el emulador/dispositivo, pero localhost apunta al emulador mismo, no a tu máquina.

**Solución:**

1. **En `AuthService.dart` o donde defines la URL base:**

```dart
// ❌ MALO
final String baseUrl = 'http://localhost:8080';

// ✅ BIEN - Emulador
final String baseUrl = 'http://10.0.2.2:8080';

// ✅ BIEN - Dispositivo físico
final String baseUrl = 'http://192.168.1.100:8080';  // Reemplaza con tu IP
```

2. **Obtén tu IP real (si usas dispositivo físico):**
```powershell
ipconfig
```
Busca la línea que dice `IPv4 Address` bajo "Ethernet adapter" o "Wireless LAN adapter".

3. **Verifica que el backend esté corriendo:**
```powershell
curl http://localhost:8080/api/health
```

4. **Reinicia la app Flutter:**
```bash
flutter run
```

---

## 🔒 Notas de Seguridad

- ⚠️ **NUNCA** usar estas credenciales en producción
- 🔐 Las contraseñas se hashean con BCrypt (factor 10) en MongoDB
- 📝 Los tokens JWT expiran en 24 horas
- 🛡️ El secret JWT debe cambiarse en producción (actualmente: `clave-ultra-secreta-para-firmar-tokens-cambiar-en-produccion-minimo-256-bits`)

---

## 📚 Guías Relacionadas

- **G1:** Autenticación y usuarios
- **G2:** Departamentos, actividades, políticas
- **G3:** Swagger y documentación API
- **G4:** Motor de workflow
- **G5:** Diagramador de workflow

---

*Última actualización: 24 de abril de 2026*
