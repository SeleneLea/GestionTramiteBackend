# Guía 2 — Ciclo 3: Trazabilidad con integridad, Métricas y Cuellos de botella

**Ciclo 3 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Implementar la capa de auditoría inalterable mediante encadenamiento de hashes SHA-256 (CU-23), la medición automática de los tiempos de atención comparados contra los SLA definidos (CU-24) y la detección periódica de cuellos de botella (CU-25).

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-23** | Registrar trazabilidad del trámite | Sistema | Añade encadenamiento hash SHA-256 a la colección `trazabilidad`. Si el guardado falla, la transacción debe ser revertida. |
| **CU-24** | Medir tiempos de atención | Sistema | Al completar una actividad, calcula `fechaFin - fechaInicio` y lo compara con el SLA de la actividad. Guarda en `metrica_tiempo`. Expone `GET /api/metricas/tramite/{tramiteId}`. |
| **CU-25** | Detectar cuellos de botella | Sistema (Job) | Tarea `@Scheduled` que detecta desviaciones en promedios y sugiere pausas. Guarda en `cuello_botella`. Expone `GET /api/metricas/cuellos-botella`. |

---

## 2. Modelos Java Nuevos (Colecciones MongoDB)

Crear en `com.example.demo.models`. Extraídos estrictamente de `base mermaid.md`.

### `MetricaTiempo.java`
```java
package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "metrica_tiempo")
public class MetricaTiempo {
    @Id
    private String id;
    private String tramiteId;
    private String actividadId;
    private String departamentoId;
    private Integer tiempoSegundos;
    private Boolean superoSla;
    private LocalDateTime fechaInicioActividad;
    private LocalDateTime fechaFinActividad;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTramiteId() { return tramiteId; }
    public void setTramiteId(String tramiteId) { this.tramiteId = tramiteId; }
    public String getActividadId() { return actividadId; }
    public void setActividadId(String actividadId) { this.actividadId = actividadId; }
    public String getDepartamentoId() { return departamentoId; }
    public void setDepartamentoId(String departamentoId) { this.departamentoId = departamentoId; }
    public Integer getTiempoSegundos() { return tiempoSegundos; }
    public void setTiempoSegundos(Integer tiempoSegundos) { this.tiempoSegundos = tiempoSegundos; }
    public Boolean getSuperoSla() { return superoSla; }
    public void setSuperoSla(Boolean superoSla) { this.superoSla = superoSla; }
    public LocalDateTime getFechaInicioActividad() { return fechaInicioActividad; }
    public void setFechaInicioActividad(LocalDateTime fechaInicioActividad) { this.fechaInicioActividad = fechaInicioActividad; }
    public LocalDateTime getFechaFinActividad() { return fechaFinActividad; }
    public void setFechaFinActividad(LocalDateTime fechaFinActividad) { this.fechaFinActividad = fechaFinActividad; }
}
```

### `CuelloBotella.java`
```java
package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "cuello_botella")
public class CuelloBotella {
    @Id
    private String id;
    private String actividadId;
    private String departamentoId;
    private String periodo; // "dia", "semana", "mes"
    private Integer tramitesAcumulados;
    private Double tiempoPromedio;
    private Double tiempoEsperado;
    private Double desviacionPorcentaje;
    private String causaSugerida;
    private LocalDateTime fechaDeteccion;

    // Getters y Setters alineados a camelCase
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActividadId() { return actividadId; }
    public void setActividadId(String actividadId) { this.actividadId = actividadId; }
    public String getDepartamentoId() { return departamentoId; }
    public void setDepartamentoId(String departamentoId) { this.departamentoId = departamentoId; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public Integer getTramitesAcumulados() { return tramitesAcumulados; }
    public void setTramitesAcumulados(Integer tramitesAcumulados) { this.tramitesAcumulados = tramitesAcumulados; }
    public Double getTiempoPromedio() { return tiempoPromedio; }
    public void setTiempoPromedio(Double tiempoPromedio) { this.tiempoPromedio = tiempoPromedio; }
    public Double getTiempoEsperado() { return tiempoEsperado; }
    public void setTiempoEsperado(Double tiempoEsperado) { this.tiempoEsperado = tiempoEsperado; }
    public Double getDesviacionPorcentaje() { return desviacionPorcentaje; }
    public void setDesviacionPorcentaje(Double desviacionPorcentaje) { this.desviacionPorcentaje = desviacionPorcentaje; }
    public String getCausaSugerida() { return causaSugerida; }
    public void setCausaSugerida(String causaSugerida) { this.causaSugerida = causaSugerida; }
    public LocalDateTime getFechaDeteccion() { return fechaDeteccion; }
    public void setFechaDeteccion(LocalDateTime fechaDeteccion) { this.fechaDeteccion = fechaDeteccion; }
}
```

Modificar `Trazabilidad.java` existente para asegurar que contenga los campos `hashActual` y `hashAnterior` definidos en la estructura del DB Mermaid.

---

## 3. Repositorios

Crear en `com.example.demo.repositories`:

### `MetricaTiempoRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.MetricaTiempo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetricaTiempoRepository extends MongoRepository<MetricaTiempo, String> {
    List<MetricaTiempo> findByTramiteId(String tramiteId);
    List<MetricaTiempo> findByActividadIdOrderByFechaFinActividadDesc(String actividadId);
}
```

### `CuelloBotellaRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.CuelloBotella;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuelloBotellaRepository extends MongoRepository<CuelloBotella, String> {
    List<CuelloBotella> findAllByOrderByFechaDeteccionDesc();
}
```

También, en `TrazabilidadRepository.java` agregar:
```java
// campo del modelo: timestamp  (no "fechaAccion")
Trazabilidad findTopByTramiteIdOrderByTimestampDesc(String tramiteId);
```

---

## 4. Servicios

### 4.1. Refactorización para Integridad: `TrazabilidadService.java`
Servicio dedicado que encapsule el genrado SHA-256.

```java
package com.example.demo.services;

import com.example.demo.models.Trazabilidad;
import com.example.demo.repositories.TrazabilidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class TrazabilidadService {

    @Autowired
    private TrazabilidadRepository trazabilidadRepository;

    /**
     * CU-23: Registrar trazabilidad con hash inalterable.
     * Campos del modelo Trazabilidad: tramiteId, actorId, accion, nodoId,
     * datosAntes, datosDespues, hashActual, hashAnterior, timestamp
     */
    public Trazabilidad registrar(String tramiteId, String actorId, String accion,
                                  String nodoId, Map<String, Object> datosDespues) {
        // findTopBy... — campo es "timestamp" (no "fechaAccion")
        Trazabilidad previa = trazabilidadRepository.findTopByTramiteIdOrderByTimestampDesc(tramiteId);
        String hashAnterior = previa != null ? previa.getHashActual()
                : "0000000000000000000000000000000000000000000000000000000000000000";

        Trazabilidad nueva = new Trazabilidad();
        nueva.setTramiteId(tramiteId);
        nueva.setActorId(actorId);
        nueva.setAccion(accion);
        nueva.setNodoId(nodoId);
        nueva.setDatosDespues(datosDespues);
        nueva.setTimestamp(LocalDateTime.now());    // campo: timestamp (LocalDateTime)
        nueva.setHashAnterior(hashAnterior);

        // Hash encadena: tramiteId + accion + timestamp + hashAnterior
        String inputToHash = tramiteId + accion + nueva.getTimestamp().toString() + hashAnterior;
        nueva.setHashActual(generarHash(inputToHash));

        // Si esto lanza excepción de BD, debe bloquear la transacción en el Controller
        return trazabilidadRepository.save(nueva);
    }

    private String generarHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error de integridad: algoritmo SHA-256 no disponible.");
        }
    }
}
```

### 4.2. `MetricaYCuelloService.java` (CU-24 y CU-25)
**(Nota IMPORTANTE: Asegúrate de tener `@EnableScheduling` en `DemoApplication.java`)**

```java
package com.example.demo.services;

import com.example.demo.models.Actividad;
import com.example.demo.models.CuelloBotella;
import com.example.demo.models.MetricaTiempo;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.CuelloBotellaRepository;
import com.example.demo.repositories.MetricaTiempoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MetricaYCuelloService {

    @Autowired
    private MetricaTiempoRepository metricaRepo;

    @Autowired
    private CuelloBotellaRepository cuelloRepo;

    @Autowired
    private ActividadRepository actividadRepository;

    /**
     * CU-24: Medir tiempos al completar el nodo. Debe ser llamado por WorkflowEngineService
     */
    public void registrarMetricaActividad(String tramiteId, String actividadId, String departamentoId, LocalDateTime inicio, LocalDateTime fin) {
        Actividad act = actividadRepository.findById(actividadId).orElse(null);
        if (act == null || inicio == null || fin == null) return;

        long segundos = Duration.between(inicio, fin).getSeconds();
        int slaSegundos = act.getSlaHoras() * 3600;

        MetricaTiempo m = new MetricaTiempo();
        m.setTramiteId(tramiteId);
        m.setActividadId(actividadId);
        m.setDepartamentoId(departamentoId);
        m.setFechaInicioActividad(inicio);
        m.setFechaFinActividad(fin);
        m.setTiempoSegundos((int) segundos);
        m.setSuperoSla(segundos > slaSegundos);
        
        metricaRepo.save(m);
    }

    /**
     * CU-25: Job automático cada 24 horas para detectar anomalías de cuellos de botella
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2:00 AM todos los días
    public void analizarCuellosDeBotella() {
        List<Actividad> actividades = actividadRepository.findAll();
        
        for (Actividad act : actividades) {
            List<MetricaTiempo> metricas = metricaRepo.findByActividadIdOrderByFechaFinActividadDesc(act.getId());
            if (metricas.isEmpty() || metricas.size() < 5) continue; // Necesita historial mínimo

            double promedio = metricas.stream().mapToInt(MetricaTiempo::getTiempoSegundos).average().orElse(0.0);
            double slaSegundos = act.getSlaHoras() * 3600;

            if (promedio > slaSegundos) {
                CuelloBotella cb = new CuelloBotella();
                cb.setActividadId(act.getId());
                cb.setDepartamentoId(act.getDepartamentoId());
                cb.setPeriodo("dia");
                cb.setTramitesAcumulados(metricas.size());
                cb.setTiempoPromedio(promedio);
                cb.setTiempoEsperado(slaSegundos);
                cb.setDesviacionPorcentaje(((promedio - slaSegundos) / slaSegundos) * 100);
                cb.setCausaSugerida("El promedio supera el SLA. Posible falta de personal o procesos ineficientes.");
                cb.setFechaDeteccion(LocalDateTime.now());
                
                cuelloRepo.save(cb);
            }
        }
    }
}
```

---

## 5. Controladores REST

### `MetricaController.java`
Expondrá los endpoints de CU-24 y CU-25.

```java
package com.example.demo.controllers;

import com.example.demo.models.CuelloBotella;
import com.example.demo.models.MetricaTiempo;
import com.example.demo.repositories.CuelloBotellaRepository;
import com.example.demo.repositories.MetricaTiempoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metricas")
public class MetricaController {

    @Autowired
    private MetricaTiempoRepository metricaRepository;

    @Autowired
    private CuelloBotellaRepository cuelloBotellaRepository;

    @GetMapping("/tramite/{tramiteId}")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    public ResponseEntity<List<MetricaTiempo>> getMetricasPorTramite(@PathVariable String tramiteId) {
        return ResponseEntity.ok(metricaRepository.findByTramiteId(tramiteId));
    }

    @GetMapping("/cuellos-botella")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<CuelloBotella>> getCuellosBotella() {
        return ResponseEntity.ok(cuelloBotellaRepository.findAllByOrderByFechaDeteccionDesc());
    }
}
```

---

## Siguientes Pasos
Con esta guía completada, el motor ya no solo avanza, sino que audita rigurosamente (hash), monitorea velocidad (métricas SLA) e incluso tiene un job en background detectando estancamientos para el dashboard del admin. 

El siguiente paso es la Guía **G4-C3** que creará la infraestructura para enviar notificaciones por app móvil/web/email cada que un trámite cambie de estado o un SLA se exceda.