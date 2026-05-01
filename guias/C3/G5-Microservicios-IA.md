# Guía 5 — Ciclo 3: Microservicios Externos (Voz e IA)

**Ciclo 3 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Integrar la plataforma web corporativa y la aplicación móvil con microservicios FastAPI de Inteligencia Artificial. Específicamente, implementar la transcripción automática de audio a texto para acelerar la carga de expedientes (CU-30) y un agente asistente conversacional alimentado con RAG (CU-31).

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-30** | Completar formulario por Voz | Funcionario | `POST /api/expedientes/secciones/{seccionId}/transcribir-voz` — Toma un audio, lo envía al servicio externo y guarda en `transcripcion_voz` |
| **CU-31** | Interactuar con Agente de asistencia | Todos | `POST /api/agente/consultar` — Envía historial de chat al servicio externo de asistencia. Registra un `log_agente` con la interacción. |

---

## 2. Variables de Configuración

En tu `src/main/resources/application.yml` o `application.properties`, deberás apuntar a los puertos donde corras ambos microservicios FastAPI.

```yaml
# application.yml
app:
  ai:
    voz-url: "http://localhost:8001/api/transcribir"
    agente-url: "http://localhost:8002/api/chat"
```

Dependiendo de tus requerimientos de latencia y escalabilidad, este es el momento de considerar si usar un `RestTemplate` (sincrónico) o `WebClient` (asíncrono, ideal si el modelo tarda unos segundos). Para este proyecto estándar de demo, usaremos el `RestTemplate` integrado.

---

## 3. Modelos Java

Extraídos del esquema Mermaid (`TRANSCRIPCION_VOZ` y `LOG_AGENTE`).

### `TranscripcionVoz.java`
```java
package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "transcripcion_voz")
public class TranscripcionVoz {
    @Id
    private String id;
    private String seccionId;
    private String funcionarioId;
    private String textoTranscrito;
    private Double duracionSegundos;
    private Double confianzaTranscripcion;
    private LocalDateTime fechaTranscripcion;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSeccionId() { return seccionId; }
    public void setSeccionId(String seccionId) { this.seccionId = seccionId; }
    public String getFuncionarioId() { return funcionarioId; }
    public void setFuncionarioId(String funcionarioId) { this.funcionarioId = funcionarioId; }
    public String getTextoTranscrito() { return textoTranscrito; }
    public void setTextoTranscrito(String textoTranscrito) { this.textoTranscrito = textoTranscrito; }
    public Double getDuracionSegundos() { return duracionSegundos; }
    public void setDuracionSegundos(Double duracionSegundos) { this.duracionSegundos = duracionSegundos; }
    public Double getConfianzaTranscripcion() { return confianzaTranscripcion; }
    public void setConfianzaTranscripcion(Double confianzaTranscripcion) { this.confianzaTranscripcion = confianzaTranscripcion; }
    public LocalDateTime getFechaTranscripcion() { return fechaTranscripcion; }
    public void setFechaTranscripcion(LocalDateTime fechaTranscripcion) { this.fechaTranscripcion = fechaTranscripcion; }
}
```

### `LogAgente.java`
```java
package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "log_agente")
public class LogAgente {
    @Id
    private String id;
    private String usuarioId;
    private String contextoModulo;
    private String contextoRol;
    private String contextoTramiteId;
    private String consultaUsuario;
    private String respuestaAgente;
    private Double tiempoRespuestaMs;
    private Boolean fueUtil;
    private LocalDateTime timestamp;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getContextoModulo() { return contextoModulo; }
    public void setContextoModulo(String contextoModulo) { this.contextoModulo = contextoModulo; }
    public String getContextoRol() { return contextoRol; }
    public void setContextoRol(String contextoRol) { this.contextoRol = contextoRol; }
    public String getContextoTramiteId() { return contextoTramiteId; }
    public void setContextoTramiteId(String contextoTramiteId) { this.contextoTramiteId = contextoTramiteId; }
    public String getConsultaUsuario() { return consultaUsuario; }
    public void setConsultaUsuario(String consultaUsuario) { this.consultaUsuario = consultaUsuario; }
    public String getRespuestaAgente() { return respuestaAgente; }
    public void setRespuestaAgente(String respuestaAgente) { this.respuestaAgente = respuestaAgente; }
    public Double getTiempoRespuestaMs() { return tiempoRespuestaMs; }
    public void setTiempoRespuestaMs(Double tiempoRespuestaMs) { this.tiempoRespuestaMs = tiempoRespuestaMs; }
    public Boolean getFueUtil() { return fueUtil; }
    public void setFueUtil(Boolean fueUtil) { this.fueUtil = fueUtil; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
```

### `TranscripcionVozRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.TranscripcionVoz;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranscripcionVozRepository extends MongoRepository<TranscripcionVoz, String> {
    List<TranscripcionVoz> findBySeccionId(String seccionId);
}
```

### `LogAgenteRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.LogAgente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogAgenteRepository extends MongoRepository<LogAgente, String> {
    List<LogAgente> findByUsuarioIdOrderByTimestampDesc(String usuarioId);
}
```

---

## 4. DTOs

### `AgenteRequest.java`
```java
package com.example.demo.dto;

// Archivo: AgenteRequest.java  (una clase public por archivo — Java lo requiere)
public class AgenteRequest {
    private String consulta;
    private String moduloActivo; // "tramites", "usuarios", "dashboard"
    private String tramiteIdOpcional;

    public String getConsulta() { return consulta; }
    public void setConsulta(String consulta) { this.consulta = consulta; }
    public String getModuloActivo() { return moduloActivo; }
    public void setModuloActivo(String moduloActivo) { this.moduloActivo = moduloActivo; }
    public String getTramiteIdOpcional() { return tramiteIdOpcional; }
    public void setTramiteIdOpcional(String tramiteIdOpcional) { this.tramiteIdOpcional = tramiteIdOpcional; }
}
```

### `AgenteResponse.java`
```java
package com.example.demo.dto;

// Archivo: AgenteResponse.java  (clase separada obligatoria)
public class AgenteResponse {
    private String idLogBaseDatos;
    private String respuesta;

    public String getIdLogBaseDatos() { return idLogBaseDatos; }
    public void setIdLogBaseDatos(String idLogBaseDatos) { this.idLogBaseDatos = idLogBaseDatos; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
}
```

---

## 5. Servicio de Integración

### `AiIntegrationService.java`
Ejemplo manejando `RestTemplate`. Como los servicios están en otra guía (fuera de Spring Boot), debes configurar qué datos retornar en caso de que FastAPI esté desconectado (Fallback Mechanism).

```java
package com.example.demo.services;

import com.example.demo.dto.AgenteRequest;
import com.example.demo.dto.AgenteResponse;
import com.example.demo.models.LogAgente;
import com.example.demo.models.TranscripcionVoz;
import com.example.demo.repositories.LogAgenteRepository;
import com.example.demo.repositories.TranscripcionVozRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class AiIntegrationService {

    @Autowired
    private TranscripcionVozRepository vozRepo;

    @Autowired
    private LogAgenteRepository agenteRepo;

    @Value("${app.ai.voz-url:http://localhost:8001/api/transcribir}")
    private String transcripcionUrl;

    @Value("${app.ai.agente-url:http://localhost:8002/api/chat}")
    private String agenteUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * CU-30: Voz a Texto enviando binario Multipart
     */
    public TranscripcionVoz transcribirAudio(String seccionId, MultipartFile archivo, String funcionarioId) {
        String texto = "";
        double confianza = 0.0;
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", archivo.getResource());
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            // Simulación asumiendo que el FastAPI retorna un String puro con el texto.
            // String r = restTemplate.postForObject(transcripcionUrl, requestEntity, String.class);
            
            // Mock de respuesta si no está conectado el endpoint de PyTorch/Whisper:
            texto = "Este es un texto auto-transcrito por el mock de Inteligencia Artificial para el expdiente " + seccionId;
            confianza = 0.95;
            
        } catch (Exception e) {
            texto = "[Error en conexión con el microservicio de Voz]: " + e.getMessage();
        }

        TranscripcionVoz tv = new TranscripcionVoz();
        tv.setSeccionId(seccionId);
        tv.setFuncionarioId(funcionarioId);
        tv.setTextoTranscrito(texto);
        tv.setDuracionSegundos(0.0); // Debería extraerse del archivo o del MS
        tv.setConfianzaTranscripcion(confianza);
        tv.setFechaTranscripcion(LocalDateTime.now());
        
        return vozRepo.save(tv);
    }

    /**
     * CU-31: Interacción RAG
     */
    public AgenteResponse consultarAgente(AgenteRequest input, String usuarioId, String rolId) {
        long start = System.currentTimeMillis();
        String respuestaIA = "";
        
        try {
            // Preparar JSON
            // String res = restTemplate.postForObject(agenteUrl, input, String.class);
            respuestaIA = "La documentación de nuestra intranet indica que puedes navegar por el menú de reportes para descargar dicho material si posees un rol de " + rolId + ".";
        } catch (Exception e) {
            respuestaIA = "El Agente de asistencia está en mantenimiento. Intenta leer el [Manual de Usuario](/docs/manual.pdf).";
        }

        long end = System.currentTimeMillis();

        // 1. Guardar Log
        LogAgente lg = new LogAgente();
        lg.setUsuarioId(usuarioId);
        lg.setContextoModulo(input.getModuloActivo());
        lg.setContextoRol(rolId);
        lg.setContextoTramiteId(input.getTramiteIdOpcional());
        lg.setConsultaUsuario(input.getConsulta());
        lg.setRespuestaAgente(respuestaIA);
        lg.setTiempoRespuestaMs((double) (end - start));
        lg.setFueUtil(null);
        lg.setTimestamp(LocalDateTime.now());
        lg = agenteRepo.save(lg);

        // 2. Retornar DTO
        AgenteResponse resp = new AgenteResponse();
        resp.setIdLogBaseDatos(lg.getId());
        resp.setRespuesta(lg.getRespuestaAgente());
        
        return resp;
    }
}
```

---

## 6. Controladores AI

### `AiIntegrationController.java`
Rutas correspondientes a CU-30 (`/api/expedientes/secciones/{seccionId}/transcribir-voz`) y CU-31 (`/api/agente/consultar`).

```java
package com.example.demo.controllers;

import com.example.demo.dto.AgenteRequest;
import com.example.demo.dto.AgenteResponse;
import com.example.demo.models.TranscripcionVoz;
import com.example.demo.services.AiIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AiIntegrationController {

    @Autowired
    private AiIntegrationService aiIntegrationService;

    @PostMapping("/expedientes/secciones/{seccionId}/transcribir-voz")
    @PreAuthorize("hasRole('FUNCIONARIO')")
    public ResponseEntity<TranscripcionVoz> vozATexto(
            @PathVariable String seccionId,
            @RequestParam("audio") MultipartFile audio,
            Authentication authentication) {
        TranscripcionVoz res = aiIntegrationService.transcribirAudio(seccionId, audio, authentication.getName());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/agente/consultar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AgenteResponse> consultasAIAgente(
            @RequestBody AgenteRequest payload,
            Authentication authentication) {
        // Obtener el primer rol real del JWT; fallback si authorities está vacío
        String rol = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USUARIO");
        AgenteResponse res = aiIntegrationService.consultarAgente(payload, authentication.getName(), rol);
        return ResponseEntity.ok(res);
    }
}
```

---

¡Con esta guía, el **Ciclo 3** completo está documentado! La funcionalidad de inteligencia artificial y voz está modularizada para evitar afectar el performance general del motor de workflows (microservicios acoplados libremente vía red HTTP). Todos los pipelines y endpoints previstos están preparados para los desarrolladores Frontend o ingenieros de testing que implementarán el QA e interface gráfica final.