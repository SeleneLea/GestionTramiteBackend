# Guía 3 — Ciclo 3: Reportes e Historial de Trámites

**Ciclo 3 · Sistema de Gestión de Trámites · Spring Boot + MongoDB**

> 🎯 **Objetivo:** Implementar la visualización filtrada del historial global de trámites para administradores (CU-29) y permitir la exportación de dicho historial o métricas en formatos físicos como CSV, PDF o Excel (CU-26).

---

## 1. Casos de uso que cubre esta guía

| CU | Nombre | Actor | Qué hace en el backend |
|----|--------|-------|------------------------|
| **CU-29** | Ver historial de trámites | Administrador | `GET /api/tramites/historial` — Consulta paginada y filtrable (por estado, departamento, rango de fechas). |
| **CU-26** | Generar reportes de proceso | Administrador | `POST /api/reportes/generar` — Procesa datos y crea documento. <br>`GET /api/reportes/{id}/descargar` — Retorna el archivo generado. |

---

## 2. Dependencias

Para soportar los formatos de exportación, añadir a `build.gradle` si se van a implementar Excel o PDF (para CSV usaremos Java puro):

```groovy
dependencies {
    // Generación de Excel (Opcional, requerido si el frontend pide formato EXCEL)
    implementation 'org.apache.poi:poi-ooxml:5.2.5'
    
    // Generación de PDF (Opcional, requerido si el frontend pide formato PDF)
    implementation 'com.itextpdf:itext7-core:7.2.5'
}
```

---

## 3. Modelo Java y Repositorio

Asegurar el modelo basado estrictamente en el diagrama de la base de datos (sección `REPORTE`).

### `Reporte.java`
```java
package com.example.demo.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "reporte")
public class Reporte {
    @Id
    private String id;
    private String generadoPorId;
    private String tipo; // "productividad", "cuellos", "trazabilidad", "politica"
    private Map<String, Object> filtros; // "rango_fechas", "departamento", "estado", etc.
    private String formato; // "PDF", "EXCEL", "CSV"
    private String urlArchivo; // Ruta local / S3
    private LocalDateTime fechaGeneracion;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGeneradoPorId() { return generadoPorId; }
    public void setGeneradoPorId(String generadoPorId) { this.generadoPorId = generadoPorId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Map<String, Object> getFiltros() { return filtros; }
    public void setFiltros(Map<String, Object> filtros) { this.filtros = filtros; }
    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }
    public String getUrlArchivo() { return urlArchivo; }
    public void setUrlArchivo(String urlArchivo) { this.urlArchivo = urlArchivo; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public void setFechaGeneracion(LocalDateTime fechaGeneracion) { this.fechaGeneracion = fechaGeneracion; }
}
```

### `ReporteRepository.java`
```java
package com.example.demo.repositories;

import com.example.demo.models.Reporte;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReporteRepository extends MongoRepository<Reporte, String> {
    List<Reporte> findByGeneradoPorIdOrderByFechaGeneracionDesc(String generadoPorId);
}
```

---

## 4. DTOs (Data Transfer Objects)

### `ReporteRequest.java`
```java
package com.example.demo.dto;

import java.util.Map;

public class ReporteRequest {
    private String tipo;
    private String formato;
    private Map<String, Object> filtros;

    // Getters y Setters
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }
    public Map<String, Object> getFiltros() { return filtros; }
    public void setFiltros(Map<String, Object> filtros) { this.filtros = filtros; }
}
```

---

## 5. Servicios

### `ReporteService.java`
Gestión de la lógica de acopio de información y exportación (versión inicial funcional orientada a CSV nativo).

```java
package com.example.demo.services;

import com.example.demo.dto.ReporteRequest;
import com.example.demo.models.Reporte;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReporteService {

    @Autowired
    private ReporteRepository reporteRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    private static final String STORAGE_DIR = "./uploads/reportes/";

    /**
     * CU-26: Generador principal
     */
    public Reporte generarReporte(ReporteRequest request, String adminId) throws Exception {
        // Preparar directorio
        Path dirPath = Paths.get(STORAGE_DIR);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        String fileName = UUID.randomUUID().toString() + "." + request.getFormato().toLowerCase();
        Path filePath = dirPath.resolve(fileName);

        // Agregador de datos simulado (Para trazabilidad o listado común)
        // Spring Data JPA puede usar Query by Example para aplicar los filtros dinámicos
        List<Tramite> tramites = tramiteRepository.findAll();

        if ("CSV".equalsIgnoreCase(request.getFormato())) {
            generarCSV(tramites, filePath);
        } else {
            // Requiere Apache POI (Excel) o iText (PDF) 
            throw new IllegalArgumentException("Formato no implementado aún en esta fase de la guía");
        }

        // Persistir registro de reporte generado
        Reporte reporte = new Reporte();
        reporte.setGeneradoPorId(adminId);
        reporte.setTipo(request.getTipo());
        reporte.setFiltros(request.getFiltros());
        reporte.setFormato(request.getFormato());
        reporte.setUrlArchivo(filePath.toString());
        reporte.setFechaGeneracion(LocalDateTime.now());
        
        return reporteRepository.save(reporte);
    }

    private void generarCSV(List<Tramite> tramites, Path filePath) throws Exception {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Estado,ClienteID,FechaInicio\n");
        for (Tramite t : tramites) {
            csv.append(t.getId()).append(",")
               .append(t.getEstadoActual()).append(",")
               .append(t.getClienteId()).append(",")
               .append(t.getFechaInicio() != null ? t.getFechaInicio() : "").append("\n");
        }
        Files.writeString(filePath, csv.toString());
    }

    public byte[] descargarReporte(String reporteId) throws Exception {
        Reporte reporte = reporteRepository.findById(reporteId)
            .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado"));
            
        Path path = Paths.get(reporte.getUrlArchivo());
        return Files.readAllBytes(path);
    }
}
```

---

## 6. Controladores REST

### `HistorialTramiteController.java`
*(CU-29)*. Implementa en `com.example.demo.controllers` para que el Admin consulte grillas con paginación (Paginación proveída base por Spring Data Web Support).

```java
package com.example.demo.controllers;

import com.example.demo.models.Tramite;
import com.example.demo.repositories.TramiteRepository;
// Opcional: Usar Page y Pageable de org.springframework.data.domain
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tramites/historial")
public class HistorialTramiteController {

    @Autowired
    private TramiteRepository tramiteRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<Tramite>> obtenerHistorialAdministrativo(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String departamentoId) {
        
        // En ciclo avanzado se inyecta predicates query o Specification (MongoDB criteria)
        List<Tramite> tramites = tramiteRepository.findAll();
        // Filtro básico en memoria (sustituir en BD con Query by Example si crece)
        if (estado != null) {
            tramites.removeIf(t -> !estado.equals(t.getEstadoActual()));
        }
        return ResponseEntity.ok(tramites);
    }
}
```

### `ReporteController.java`
*(CU-26)*.

```java
package com.example.demo.controllers;

import com.example.demo.dto.ReporteRequest;
import com.example.demo.models.Reporte;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.services.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private ReporteRepository reporteRepository;

    @PostMapping("/generar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Reporte> generarReporte(@RequestBody ReporteRequest request,
                                                   Authentication authentication) throws Exception {
        Reporte r = reporteService.generarReporte(request, authentication.getName());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/{id}/descargar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> descargarReporte(@PathVariable String id) throws Exception {
        byte[] archivo = reporteService.descargarReporte(id);

        // orElseThrow con mensaje — evita NoSuchElementException sin contexto
        Reporte reporte = reporteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado: " + id));
        String contentType = "CSV".equalsIgnoreCase(reporte.getFormato()) ? "text/csv" : "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte_" + id + "." + reporte.getFormato().toLowerCase() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(archivo);
    }
}
```

---

## 7. Notas 
El diseño asume generación síncrona en CSV para las primeras pruebas. Cuando el volumen escala o si es PDF muy enriquecido, se debe considerar que `generarReporte` encolará la petición (Spring Async o en su defecto un estado de Reporte en "Generando") para que la app no experimente colapsos de memoria. Esto está pensado para complementarse con la siguiente capa (G4-Notificaciones), la cual puede notificar al usuario cuando el Excel termine de crearse en background.