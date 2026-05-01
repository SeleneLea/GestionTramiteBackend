# Datos de seed — `Backend/seed/`

Snapshot **declarativo** de los datos que `MasterSeeder` siembra al arrancar
la aplicación. Sirven para:

1. **Auditar** qué carga exactamente cada seeder sin tener que abrir el código Java.
2. **Restaurar** la base manualmente (`mongoimport`) si por algún motivo el bootstrap
   automático no corre o quieres re-poblar sin reiniciar.
3. **Documentar** los IDs lógicos (códigos, emails) que los tests de integración
   asumen — los `_id` reales son ObjectId generados por Mongo en tiempo de inserción.

## Estructura

| Archivo | Colección Mongo | Origen |
|---------|-----------------|--------|
| `permisos.json` | `permisos` | `PermisoSeeder.java` |
| `roles.json` | `roles` | `RolSeeder.java` |
| `departamentos.json` | `departamentos` | `DepartamentoSeeder.java` |
| `documentos.json` | `documentos` | `DocumentoSeeder.java` |
| `actividades.json` | `actividades` | `ActividadSeeder.java` |
| `usuarios.json` | `usuarios` | `UsuarioSeeder.java` |
| `politicas.json` | `politicas` | `PoliticaSeeder.java` |
| `tramites.json` | `tramites` | `TramiteSeeder.java` |

Los archivos de seed faltantes (diagramas, formularios, expedientes,
notificaciones, métricas, etc.) se generan a partir de los anteriores y son
voluminosos — se mantienen sólo en código (`*Seeder.java`).

## Credenciales útiles (de `UsuarioSeeder`)

| Email | Password | Rol |
|-------|----------|-----|
| `admin@cre.bo` | `admin12345` | Administrador |
| `superuser@cre.bo` | `super12345` | SuperUser |
| `funcionario@cre.bo` | `func12345` | Funcionario (TEC) |
| `func_atc@cre.bo` | `func12345` | Funcionario (ATC) |
| `func_leg@cre.bo` | `func12345` | Funcionario (LEG) |
| `func_ope@cre.bo` | `func12345` | Funcionario (OPE) |
| `cliente@cre.bo` | `cliente12345` | Cliente |
| `cliente2@cre.bo` | `cliente12345` | Cliente |
| `cliente3@cre.bo` | `cliente12345` | Cliente |

## Verificación

El seed automático corre vía `DataSeeder` con `@PostConstruct`. Para verificar
que la base está poblada correctamente, los tests de integración HTTP en
[src/test/java/com/example/demo/integration/](../src/test/java/com/example/demo/integration/)
golpean el backend ya corriendo y chequean cuentas + flujos CRUD reales:

```bash
# 1. Levantar Mongo (Docker o servicio del sistema)
docker compose up -d        # opcional, si usas Docker

# 2. Levantar el backend (queda seedeando en el primer arranque)
./gradlew bootRun

# 3. En otra terminal, correr la suite de integración
./gradlew test --tests "com.example.demo.integration.*"
```

## ⚠️ Gotcha: dos Mongo escuchando en 27017

Si tienes a la vez:
- El **Docker** del proyecto (`docker compose up`) → bindea `0.0.0.0:27017`
- Un **mongod del sistema** (servicio Windows / mongod.exe) → bindea `127.0.0.1:27017`

…el cliente Java de Spring resuelve `localhost` a `127.0.0.1` y entra al **mongod
del sistema**, no al de Docker. Confirma con:

```bash
netstat -ano | grep :27017
```

Si ves dos PIDs distintos, los datos del seed se irán al mongod del sistema y
mongo-express (apuntando al Docker) verá colecciones vacías. Soluciones:

- **Detener** el servicio mongod del sistema (`net stop MongoDB`), o
- **Cambiar el puerto** del Docker (`27018:27017` en docker-compose) y actualizar
  `application.yml` para usar `localhost:27018`.

## Bug detectado y corregido durante el bootstrap

`Backend/application.yml` (el de la raíz, no el de `src/main/resources/`) tenía
`spring.mongodb.uri` cuando la key correcta es `spring.data.mongodb.uri`. Como
Spring 4.x ignora la mal-nombrada, la URI con credenciales nunca se aplicaba y
el cliente Mongo arrancaba con `credential=null` → `AuthenticationFailed` contra
el Docker (que sí exige auth). Ya corregido en este commit.
