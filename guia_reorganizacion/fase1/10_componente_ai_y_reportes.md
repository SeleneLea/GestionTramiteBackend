# Fase 1.10 · Componentes ai-integration y reportes

> Dos componentes pequeños, agrupados en una sola subfase porque son periféricos y de bajo riesgo.

---

## Parte A · Componente aiintegration

### A.1 Objetivo

Encapsular las llamadas a microservicios externos de IA (transcripción de voz, agente conversacional) tras puertos `VozPort` y `AgentePort`.

### A.2 Archivos involucrados

| Origen | Destino |
|--------|---------|
| `models/TranscripcionVoz.java` | `modules/aiintegration/domain/TranscripcionVoz.java` |
| `models/LogAgente.java` | `modules/aiintegration/domain/LogAgente.java` |
| `repositories/TranscripcionVozRepository.java` | `modules/aiintegration/internal/...` |
| `repositories/LogAgenteRepository.java` | `modules/aiintegration/internal/...` |
| `services/AiIntegrationService.java` | `modules/aiintegration/internal/AiIntegrationServiceImpl.java` |
| `controllers/AiIntegrationController.java` | `modules/aiintegration/internal/AiIntegrationController.java` |
| `dto/AgenteRequest.java` | `modules/aiintegration/api/dto/AgenteRequest.java` |
| `dto/AgenteResponse.java` | `modules/aiintegration/api/dto/AgenteResponse.java` |

### A.3 Estructura final

```
modules/aiintegration/
├── api/
│   ├── VozPort.java
│   ├── AgentePort.java
│   └── dto/
│       ├── AgenteRequest.java
│       ├── AgenteResponse.java
│       └── TranscripcionResponse.java       ← NUEVO
├── domain/
│   ├── TranscripcionVoz.java
│   └── LogAgente.java
├── internal/
│   ├── AiIntegrationServiceImpl.java
│   ├── (repositorios)
│   └── AiIntegrationController.java
├── package-info.java
└── README.md
```

### A.4 Definición de puertos

#### `api/VozPort.java`

```java
package com.example.demo.modules.aiintegration.api;

import com.example.demo.modules.aiintegration.api.dto.TranscripcionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface VozPort {
    /** Transcribe un audio asociado a una sección del expediente. */
    TranscripcionResponse transcribir(String seccionId, MultipartFile audio,
                                       String funcionarioId);
}
```

#### `api/AgentePort.java`

```java
package com.example.demo.modules.aiintegration.api;

import com.example.demo.modules.aiintegration.api.dto.*;

public interface AgentePort {
    /** Consulta al agente conversacional (CU-31). */
    AgenteResponse consultar(AgenteRequest req, String usuarioId, String rol);
}
```

### A.5 Pasos

1. Mover archivos
2. Crear los 2 Ports
3. `AiIntegrationServiceImpl implements VozPort, AgentePort`
4. Controller usa los Ports
5. `package-info.java` y README

### A.6 Notas

- La integración real con FastAPI (URLs configurables) sigue siendo la misma. Solo cambia la estructura.
- Si en el futuro se quiere intercambiar el proveedor (ej. usar OpenAI directamente desde Java), basta crear un nuevo adaptador (`OpenAiVozAdapter implements VozPort`) sin tocar el resto del sistema.

---

## Parte B · Componente reportes

### B.1 Objetivo

Encapsular generación y descarga de reportes tras `ReportesPort`.

### B.2 Archivos involucrados

| Origen | Destino |
|--------|---------|
| `models/Reporte.java` | `modules/reportes/domain/Reporte.java` |
| `repositories/ReporteRepository.java` | `modules/reportes/internal/ReporteRepository.java` |
| `services/ReporteService.java` | `modules/reportes/internal/ReporteServiceImpl.java` |
| `controllers/ReporteController.java` | `modules/reportes/internal/ReporteController.java` |
| `dto/ReporteRequest.java` | `modules/reportes/api/dto/ReporteRequest.java` |

### B.3 Estructura final

```
modules/reportes/
├── api/
│   ├── ReportesPort.java
│   └── dto/
│       ├── ReporteRequest.java
│       └── ReporteResponse.java
├── domain/
│   └── Reporte.java
├── internal/
│   ├── ReporteServiceImpl.java
│   ├── ReporteRepository.java
│   └── ReporteController.java
├── package-info.java
└── README.md
```

### B.4 Definición del puerto

#### `api/ReportesPort.java`

```java
package com.example.demo.modules.reportes.api;

import com.example.demo.modules.reportes.api.dto.*;

public interface ReportesPort {
    ReporteResponse generar(ReporteRequest req, String adminId);
    byte[] descargar(String reporteId);
}
```

### B.5 Notas

- `ReporteServiceImpl` hoy accede a `TramiteRepository` directamente para listar trámites a reportar. Esto **rompe encapsulación**.
- Solución: cambiar a `tramiteQueryPort.misTramites(...)` o crear un nuevo método en `TramiteQueryPort.listarConFiltros(filtros)`.
- Si esa migración es muy invasiva, dejar como deuda documentada en el README del componente.

---

## C · Verificación general de la subfase 1.10

| Flujo | Esperado |
|-------|----------|
| POST `/api/expedientes/secciones/{id}/transcribir-voz` | 200 (o el error mock si el microservicio FastAPI no está) |
| POST `/api/agente/consultar` | 200 |
| POST `/api/reportes/generar` (admin) | 200 + Reporte |
| GET `/api/reportes/{id}/descargar` | 200 + bytes |

---

## D · Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componentes aiintegration y reportes con sus respectivos Ports"
```

---

## Próximo paso

Continuar con **`11_validacion_final.md`** para cerrar la fase 1.
