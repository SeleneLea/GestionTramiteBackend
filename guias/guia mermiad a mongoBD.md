# Guía — Implementar la Base de Datos MongoDB en Spring Boot

**Sistema de Gestión de Trámites · Stack: Spring Boot + MongoDB + Docker**

---

## 1. Instalar MongoDB con Docker

Crear archivo `docker-compose.yml` en la raíz del proyecto:

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:7.0
    container_name: tramites_mongodb
    restart: always
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin123
      MONGO_INITDB_DATABASE: tramites_db
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
      - ./mongo-init:/docker-entrypoint-initdb.d
    networks:
      - tramites_network

  mongo-express:
    image: mongo-express:latest
    container_name: tramites_mongo_express
    restart: always
    ports:
      - "8081:8081"
    environment:
      ME_CONFIG_MONGODB_ADMINUSERNAME: admin
      ME_CONFIG_MONGODB_ADMINPASSWORD: admin123
      ME_CONFIG_MONGODB_URL: mongodb://admin:admin123@mongodb:27017/
      ME_CONFIG_BASICAUTH: false
    depends_on:
      - mongodb
    networks:
      - tramites_network

volumes:
  mongodb_data:

networks:
  tramites_network:
    driver: bridge
```

Levantar los servicios:

```bash
docker-compose up -d
```

- MongoDB quedará en `localhost:27017`
- Mongo Express (interfaz web para ver los datos) en `http://localhost:8081`

---

## 2. Configurar Spring Boot

### 2.1. Agregar dependencia en `pom.xml`

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 2.2. Configurar conexión en `application.yml`

```yaml
spring:
  application:
    name: tramites-backend

  data:
    mongodb:
      uri: mongodb://admin:admin123@localhost:27017/tramites_db?authSource=admin
      auto-index-creation: true

server:
  port: 8080

logging:
  level:
    org.springframework.data.mongodb.core.MongoTemplate: DEBUG
```

---

## 3. Estructura de carpetas recomendada

```
src/main/java/com/tramites/
├── TramitesApplication.java
├── config/
│   └── MongoConfig.java
├── models/          ← Documentos/Colecciones
│   ├── Usuario.java
│   ├── Rol.java
│   ├── Departamento.java
│   ├── Actividad.java
│   ├── PoliticaNegocio.java
│   ├── DiagramaWorkflow.java
│   ├── NodoDiagrama.java
│   ├── Tramite.java
│   ├── ExpedienteDigital.java
│   ├── SeccionExpediente.java
│   ├── Trazabilidad.java
│   └── ... (el resto de colecciones)
├── repositories/    ← Interfaces para acceso a datos
│   ├── UsuarioRepository.java
│   ├── TramiteRepository.java
│   └── ...
├── services/        ← Lógica de negocio
│   ├── TramiteService.java
│   ├── WorkflowEngineService.java
│   └── ...
├── controllers/     ← Endpoints REST
│   ├── TramiteController.java
│   └── ...
└── dto/             ← Data Transfer Objects
    └── ...
```

---

## 4. Ejemplos de código — Traducir el diagrama a Java

### 4.1. Colección USUARIO

```java
package com.tramites.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    private String nombre;
    private String apellido;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    @DBRef
    private Rol rol;

    @DBRef
    private List<Departamento> departamentos;

    private String tipo; // cliente | funcionario | administrador
    private String telefono;
    private boolean activo;

    private LocalDateTime fechaRegistro;
    private LocalDateTime ultimoAcceso;
}
```

### 4.2. Colección TRAMITE

```java
package com.tramites.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;

@Data
@Document(collection = "tramites")
public class Tramite {

    @Id
    private String id;

    @Indexed(unique = true)
    private String codigo; // TR-2025-00001

    @DBRef
    private Usuario cliente;

    @DBRef
    private PoliticaNegocio politica;

    @DBRef
    private ExpedienteDigital expediente;

    private String estadoActual;

    @DBRef
    private NodoDiagrama nodoActual;

    @DBRef
    private Usuario funcionarioActual;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaEstimadaCierre;
    private LocalDateTime fechaCierreReal;

    private int prioridad;
}
```

### 4.3. Colección EXPEDIENTE_DIGITAL con secciones embebidas

```java
package com.tramites.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "expedientes")
public class ExpedienteDigital {

    @Id
    private String id;

    private String tramiteId;

    // Las secciones son embebidas, no referenciadas,
    // porque siempre se leen juntas con el expediente
    private List<SeccionExpediente> secciones;

    private LocalDateTime fechaCreacion;
    private LocalDateTime ultimaActualizacion;
}
```

### 4.4. Colección TRAZABILIDAD (con hash encadenado)

```java
package com.tramites.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Document(collection = "trazabilidad")
public class Trazabilidad {

    @Id
    private String id;

    @Indexed
    private String tramiteId;

    private String actorId;
    private String accion;
    private String nodoId;

    private Map<String, Object> datosAntes;
    private Map<String, Object> datosDespues;

    private String hashActual;
    private String hashAnterior;

    @Indexed
    private LocalDateTime timestamp;
}
```

---

## 5. Repositorios — Acceso a datos

```java
package com.tramites.repositories;

import com.tramites.models.Tramite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TramiteRepository extends MongoRepository<Tramite, String> {

    Optional<Tramite> findByCodigo(String codigo);

    List<Tramite> findByEstadoActual(String estado);

    List<Tramite> findByClienteId(String clienteId);

    List<Tramite> findByFuncionarioActualId(String funcionarioId);

    @Query("{ 'estadoActual': { $in: ['En proceso', 'Derivado', 'Observado'] } }")
    List<Tramite> findTramitesActivos();

    long countByEstadoActual(String estado);
}
```

---

## 6. Script de datos iniciales

Crear carpeta `mongo-init/` en la raíz del proyecto con el archivo `01-seed.js`:

```javascript
db = db.getSiblingDB('tramites_db');

// Insertar roles iniciales
db.roles.insertMany([
    {
        _id: ObjectId(),
        nombre: "SuperUser",
        descripcion: "Acceso total al sistema",
        permisos: ["*"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        _id: ObjectId(),
        nombre: "Administrador",
        descripcion: "Crea flujos y gestiona configuración",
        permisos: ["CREAR_FLUJO", "GESTIONAR_USUARIOS", "VER_REPORTES"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        _id: ObjectId(),
        nombre: "Funcionario",
        descripcion: "Ejecuta trámites asignados",
        permisos: ["EJECUTAR_TRAMITE", "VER_HISTORIAL_DEPARTAMENTO"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        _id: ObjectId(),
        nombre: "Cliente",
        descripcion: "Solicita y consulta sus trámites",
        permisos: ["SOLICITAR_TRAMITE", "CONSULTAR_MIS_TRAMITES"],
        esSistema: true,
        fechaCreacion: new Date()
    }
]);

// Insertar departamentos de ejemplo (CRE)
db.departamentos.insertMany([
    { nombre: "Atención al Cliente", codigo: "ATC", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Técnica", codigo: "TEC", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Legal", codigo: "LEG", activo: true, fechaCreacion: new Date() },
    { nombre: "Operaciones", codigo: "OPE", activo: true, fechaCreacion: new Date() }
]);

print("Datos iniciales insertados correctamente");
```

Este script se ejecuta automáticamente cuando se crea el contenedor MongoDB por primera vez gracias al volumen `./mongo-init:/docker-entrypoint-initdb.d` del `docker-compose.yml`.

---

## 7. Índices recomendados para rendimiento

Crear una clase de configuración Java:

```java
package com.tramites.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MongoIndexConfig {

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {
        // Índice único en email
        mongoTemplate.indexOps("usuarios")
            .ensureIndex(new Index().on("email", Sort.Direction.ASC).unique());

        // Índice compuesto en trámites
        mongoTemplate.indexOps("tramites")
            .ensureIndex(new Index()
                .on("estadoActual", Sort.Direction.ASC)
                .on("fechaInicio", Sort.Direction.DESC));

        // Índice en trazabilidad por trámite
        mongoTemplate.indexOps("trazabilidad")
            .ensureIndex(new Index()
                .on("tramiteId", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC));

        // Índice en notificaciones no leídas
        mongoTemplate.indexOps("notificaciones")
            .ensureIndex(new Index()
                .on("destinatarioId", Sort.Direction.ASC)
                .on("leida", Sort.Direction.ASC));
    }
}
```

---

## 8. Ejemplo completo de servicio

```java
package com.tramites.services;

import com.tramites.models.Tramite;
import com.tramites.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TramiteService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private TrazabilidadService trazabilidadService;

    public Tramite crearTramite(Tramite tramite) {
        tramite.setCodigo(generarCodigo());
        tramite.setEstadoActual("Nuevo");
        tramite.setFechaInicio(LocalDateTime.now());

        Tramite guardado = tramiteRepository.save(tramite);

        // Registrar en trazabilidad
        trazabilidadService.registrarAccion(
            guardado.getId(),
            tramite.getCliente().getId(),
            "CREAR_TRAMITE"
        );

        return guardado;
    }

    public List<Tramite> obtenerTramitesActivos() {
        return tramiteRepository.findTramitesActivos();
    }

    private String generarCodigo() {
        int year = LocalDateTime.now().getYear();
        long count = tramiteRepository.count() + 1;
        return String.format("TR-%d-%05d", year, count);
    }
}
```

---

## 9. Flujo de trabajo recomendado

### Paso 1: Levantar MongoDB
```bash
docker-compose up -d mongodb mongo-express
```

### Paso 2: Verificar conexión
- Abrir http://localhost:8081 (Mongo Express)
- Debe aparecer la base `tramites_db` con las colecciones creadas por el seed

### Paso 3: Iniciar Spring Boot
```bash
mvn spring-boot:run
```
Spring Data MongoDB crea automáticamente las colecciones faltantes cuando guardas el primer documento.

### Paso 4: Probar con Postman o curl
```bash
curl -X POST http://localhost:8080/api/tramites \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"...", "politicaId":"..."}'
```

---

## 10. Tips finales para el examen

**Para la presentación del 28 de abril:**

Durante la demo, ten abierto **Mongo Express** en una pestaña del navegador. Así puedes mostrar en vivo cómo se crean los documentos cuando un trámite se ejecuta, que es lo que el profesor quiere ver (software funcionando como producto de ingeniería).

**En la documentación (PDF):**

En la fundamentación teórica, en el capítulo de comparativa de herramientas, debes incluir la comparación **MongoDB vs PostgreSQL**:

- MongoDB es no relacional/documental, ideal para datos con estructuras variables (ej: formularios dinámicos por sección que cambian según la plantilla).
- Usa **Spring Data MongoDB como ODM** (Object Document Mapping), no ORM porque ORM es exclusivo de relacionales.
- Justificar que MongoDB se eligió por la flexibilidad de esquema necesaria para los formularios configurables por nodo.

**En el modelo de datos:**

Presentar el diagrama de Mermaid como modelo de datos, explicando que aunque MongoDB no usa tablas, las colecciones tienen relaciones lógicas entre sí que se manejan con referencias (`DBRef`) o documentos embebidos.

**Regla práctica para decidir entre referencias y embebidos:**

- **Embebido:** cuando los datos siempre se leen juntos y el subdocumento es parte del padre (ej: secciones dentro del expediente)
- **Referencia (DBRef):** cuando el dato se comparte entre múltiples documentos o crece sin límite (ej: un usuario puede estar en muchos trámites)

---

*Guía de implementación · Sistema de Gestión de Trámites · Primer Examen Parcial*