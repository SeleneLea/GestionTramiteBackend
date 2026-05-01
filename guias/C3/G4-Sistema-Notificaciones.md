# Guía 4 — Ciclo 3: Sistema de Notificaciones

**Ciclo 3 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Implementar un motor de notificaciones multicanal (Push, Web y Email) para alertar a clientes y funcionarios de eventos clave como cambio de estado, asignación de tareas, vencimiento de SLA u observaciones (CU-27 y CU-28).

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-27** | Enviar notificaciones del sistema | Sistema | Lógica interna asíncrona (`@Scheduled`) o en tiempo real que inyecta datos en plantillas, y los despacha mediante `firebase-admin` o `JavaMailSender` cambiando su `estadoEnvio` a "enviada" o "fallida". |
| **CU-28** | Recibir notificaciones | Cliente, Func. | Endpoints `GET /api/notificaciones/mis-notificaciones` y `PUT /api/notificaciones/{id}/marcar-leida` para la bandeja de entrada in-app. |

---

## 2. Dependencias (`build.gradle`)

Agregar la compatibilidad de Mail (SMTP) y Firebase Cloud Messaging (Push Notifications Flutter):

```groovy
dependencies {
    // Spring Boot Mail (SMTP)
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    
    // Firebase Admin (Para notificaciones Push en Flutter)
    implementation 'com.google.firebase:firebase-admin:9.3.0'
}
```

> **Configuración requerida en `application.yml`:** Es necesario agregar luego las propiedades `spring.mail.host`, `spring.mail.port`, etc. y colocar el JSON de servicio de Firebase.

---

## 3. Modelos Java

Extraídos del esquema Mermaid (`NOTIFICACION` y `CANAL_ENVIO`), se crearán en `com.example.demo.models`.

### `CanalEnvio.java`
```java
package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document(collection = "canal_envio")
public class CanalEnvio {
    @Id
    private String id;
    private String tipo; // "push_flutter", "web_internal", "email"
    private Map<String, Object> configuracion;
    private Boolean activo;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Map<String, Object> getConfiguracion() { return configuracion; }
    public void setConfiguracion(Map<String, Object> configuracion) { this.configuracion = configuracion; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
```

*(Nota: Deberás actualizar `Notificacion.java` creado marginalmente en C2 con los atributos completos).*
### `Notificacion.java`
```java
package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notificacion")
public class Notificacion {
    @Id
    private String id;
    private String destinatarioId;
    private String tramiteId;
    private String canal; // "push", "web", "email"
    private String tipo; // "cambio_estado", "asignacion", "sla_vencido", "observacion"
    private String titulo;
    private String mensaje;
    private Boolean leida;
    private String estadoEnvio; // "pendiente", "enviada", "fallida"
    private Integer intentosEnvio;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLeida;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDestinatarioId() { return destinatarioId; }
    public void setDestinatarioId(String destinatarioId) { this.destinatarioId = destinatarioId; }
    public String getTramiteId() { return tramiteId; }
    public void setTramiteId(String tramiteId) { this.tramiteId = tramiteId; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public Boolean getLeida() { return leida; }
    public void setLeida(Boolean leida) { this.leida = leida; }
    public String getEstadoEnvio() { return estadoEnvio; }
    public void setEstadoEnvio(String estadoEnvio) { this.estadoEnvio = estadoEnvio; }
    public Integer getIntentosEnvio() { return intentosEnvio; }
    public void setIntentosEnvio(Integer intentosEnvio) { this.intentosEnvio = intentosEnvio; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaLeida() { return fechaLeida; }
    public void setFechaLeida(LocalDateTime fechaLeida) { this.fechaLeida = fechaLeida; }
}
```

---

## 4. Repositorios

### `NotificacionRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    List<Notificacion> findByDestinatarioIdOrderByFechaCreacionDesc(String destinatarioId);
    List<Notificacion> findByEstadoEnvioAndIntentosEnvioLessThan(String estadoEnvio, int maxIntentos);
}
```

---

## 5. Servicio Core (`CU-27`)

### `NotificacionService.java`
Gestiona la creación y el despacho asíncrono.

```java
package com.example.demo.services;

import com.example.demo.models.Notificacion;
import com.example.demo.repositories.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;

    // Para activar email real: añadir spring-boot-starter-mail en build.gradle y descomentar:
    // @Autowired private JavaMailSender mailSender;
    // @Autowired private UsuarioRepository usuarioRepository; // para obtener email del destinatario

    /**
     * Api interna para crear Notificaciones (Llamar desde WorkflowMotor, SLA, etc)
     */
    public Notificacion crearNotificacion(String destinatarioId, String tramiteId, String tipo, String titulo, String mensaje, String canal) {
        Notificacion n = new Notificacion();
        n.setDestinatarioId(destinatarioId);
        n.setTramiteId(tramiteId);
        n.setCanal(canal);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        n.setLeida(false);
        n.setEstadoEnvio("web".equals(canal) ? "enviada" : "pendiente"); 
        n.setIntentosEnvio(0);
        n.setFechaCreacion(LocalDateTime.now());
        
        return notificacionRepository.save(n);
    }

    /**
     * CU-27: Procesar la cola de envíos ("pendiente") cada minuto
     */
    @Scheduled(fixedRate = 60000)
    public void procesarNotificacionesPendientes() {
        List<Notificacion> pendientes = notificacionRepository.findByEstadoEnvioAndIntentosEnvioLessThan("pendiente", 3);
        
        for (Notificacion notif : pendientes) {
            boolean exito = false;
            try {
                if ("email".equals(notif.getCanal())) {
                    exito = enviarEmailSimulado(notif);
                } else if ("push".equals(notif.getCanal())) {
                    exito = enviarPushSimulado(notif);
                }
            } catch (Exception e) {
                exito = false;
            }

            notif.setIntentosEnvio(notif.getIntentosEnvio() + 1);
            if (exito) {
                notif.setEstadoEnvio("enviada");
            } else if (notif.getIntentosEnvio() >= 3) {
                notif.setEstadoEnvio("fallida");
            }
            
            notificacionRepository.save(notif);
        }
    }

    private boolean enviarEmailSimulado(Notificacion notif) {
        // Lógica real:
        // Usuario u = usuarioRepository.findById(notif.getDestinatarioId()).orElseThrow();
        // SimpleMailMessage msg = new SimpleMailMessage();
        // msg.setTo(u.getEmail()); ...
        // mailSender.send(msg);
        
        System.out.println("Enviando EMAIL a " + notif.getDestinatarioId() + ": " + notif.getTitulo());
        return true; 
    }

    private boolean enviarPushSimulado(Notificacion notif) {
        // Lógica real usando Firebase Admin SDK
        // Message message = Message.builder().setNotification(Notification.builder().setTitle...
        // FirebaseMessaging.getInstance().send(message);
        
        System.out.println("Enviando PUSH FCM a " + notif.getDestinatarioId() + ": " + notif.getTitulo());
        return true;
    }

    public Notificacion marcarComoLeida(String id, String usuarioId) {
        Notificacion n = notificacionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No encontrada"));
        if (!n.getDestinatarioId().equals(usuarioId)) throw new IllegalArgumentException("No autorizado");
        
        n.setLeida(true);
        n.setFechaLeida(LocalDateTime.now());
        return notificacionRepository.save(n);
    }
}
```

---

## 6. Controlador REST (`CU-28`)

### `NotificacionController.java`
Endpoints para que el usuario recupere las notificaciones dirigidas al canal "web" de su bandeja.

```java
package com.example.demo.controllers;

import com.example.demo.models.Notificacion;
import com.example.demo.repositories.NotificacionRepository;
import com.example.demo.services.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private NotificacionService notificacionService;

    @GetMapping("/mis-notificaciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Notificacion>> getMisNotificaciones(Authentication authentication) {
        List<Notificacion> notificaciones = notificacionRepository
                .findByDestinatarioIdOrderByFechaCreacionDesc(authentication.getName());
        return ResponseEntity.ok(notificaciones);
    }

    @PutMapping("/{id}/marcar-leida")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Notificacion> marcarLeida(@PathVariable String id, Authentication authentication) {
        Notificacion n = notificacionService.marcarComoLeida(id, authentication.getName());
        return ResponseEntity.ok(n);
    }
}
```

---

## Próximos pasos
El corazón de mensajería (colas y retries limitados) está ahora preparado. Simplemente inyectando la url SMTP de tu proveedor y la clave maestra de FCM Firebase (`.json`), podrá despachar al exterior. 

Faltaría únicamente el enlace de estas alertas a nuestro motor del Ciclo 2 (ej: Llamar a `crearNotificacion(tramite.getClienteId(), ...)` al finalizar CU-18 "Aprobar trámite").

Quedaría documentar la **G5-C3** sobre integraciones de Agente de IA y Transcripción de Voz (Microservicios FastAPI).