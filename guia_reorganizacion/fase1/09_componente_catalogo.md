# Fase 1.9 · Componente catalogo (configuración del sistema)

> Encapsular las entidades de configuración del sistema: departamentos, actividades, políticas de negocio, roles y permisos.

---

## 1. Objetivo

Que cualquier consulta de configuración (¿qué departamentos hay activos? ¿cuál es la política X? ¿qué rol tiene este usuario?) pase por `CatalogoPort`. Componente típicamente solo-lectura desde otros componentes; las escrituras vienen del UI admin.

---

## 2. Archivos involucrados

### Modelos y repositorios
| Origen | Destino |
|--------|---------|
| `models/Departamento.java` | `modules/catalogo/domain/Departamento.java` |
| `models/Actividad.java` | `modules/catalogo/domain/Actividad.java` |
| `models/PoliticaNegocio.java` | `modules/catalogo/domain/PoliticaNegocio.java` |
| `models/VersionPolitica.java` | `modules/catalogo/domain/VersionPolitica.java` |
| `models/Rol.java` | `modules/catalogo/domain/Rol.java` |
| `models/Permiso.java` | `modules/catalogo/domain/Permiso.java` |
| `repositories/DepartamentoRepository.java` | `modules/catalogo/internal/...` |
| `repositories/ActividadRepository.java` | `modules/catalogo/internal/...` |
| `repositories/PoliticaNegocioRepository.java` | `modules/catalogo/internal/...` |
| `repositories/VersionPoliticaRepository.java` | `modules/catalogo/internal/...` |
| `repositories/RolRepository.java` | `modules/catalogo/internal/...` |
| `repositories/PermisoRepository.java` | `modules/catalogo/internal/...` |

### Services y controllers
| Origen | Destino |
|--------|---------|
| `services/DepartamentoService.java` | `modules/catalogo/internal/DepartamentoServiceImpl.java` |
| `services/ActividadService.java` | `modules/catalogo/internal/ActividadServiceImpl.java` |
| `services/PoliticaNegocioService.java` | `modules/catalogo/internal/PoliticaServiceImpl.java` |
| `services/RolService.java` | `modules/catalogo/internal/RolServiceImpl.java` |
| `controllers/DepartamentoController.java` | `modules/catalogo/internal/...` |
| `controllers/ActividadController.java` | `modules/catalogo/internal/...` |
| `controllers/PoliticaNegocioController.java` | `modules/catalogo/internal/...` |
| `controllers/RolController.java` | `modules/catalogo/internal/...` |
| `controllers/PermisoController.java` | `modules/catalogo/internal/...` |

### DTOs
`DepartamentoRequest`, `PoliticaNegocioRequest`, `RolRequest`, `AsignarPermisosRequest` → `modules/catalogo/api/dto/`

---

## 3. Estructura final

```
modules/catalogo/
├── api/
│   ├── CatalogoPort.java
│   └── dto/
│       ├── DepartamentoInfo.java
│       ├── ActividadInfo.java
│       ├── PoliticaInfo.java
│       ├── RolInfo.java
│       ├── PermisoInfo.java
│       └── (Requests para CRUDs)
├── domain/
│   ├── Departamento.java
│   ├── Actividad.java
│   ├── PoliticaNegocio.java
│   ├── VersionPolitica.java
│   ├── Rol.java
│   └── Permiso.java
├── internal/
│   ├── DepartamentoServiceImpl.java
│   ├── ActividadServiceImpl.java
│   ├── PoliticaServiceImpl.java
│   ├── RolServiceImpl.java
│   ├── (todos los repositorios)
│   └── (todos los controllers)
├── package-info.java
└── README.md
```

---

## 4. Definición del puerto

### `api/CatalogoPort.java`

```java
package com.example.demo.modules.catalogo.api;

import com.example.demo.modules.catalogo.api.dto.*;

import java.util.List;
import java.util.Optional;

public interface CatalogoPort {

    // Departamentos
    Optional<DepartamentoInfo> buscarDepartamento(String id);
    List<DepartamentoInfo> listarDepartamentosActivos();

    // Actividades
    Optional<ActividadInfo> buscarActividad(String id);

    // Políticas
    Optional<PoliticaInfo> buscarPolitica(String id);
    boolean esPoliticaActiva(String politicaId);

    // Roles y permisos
    Optional<RolInfo> buscarRol(String id);
    List<PermisoInfo> permisosDeRol(String rolId);
}
```

> Solo expone **lecturas** consumidas por otros componentes. Los CRUDs los manejan los controllers REST internos para el admin.

### `api/dto/PoliticaInfo.java`

```java
package com.example.demo.modules.catalogo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoliticaInfo {
    private String id;
    private String nombre;
    private String diagramaId;
    private String estado;             // activa, inactiva, borrador
    private int versionActual;
}
```

(DTOs análogos para `DepartamentoInfo`, `ActividadInfo`, etc.)

---

## 5. Pasos de migración

### Paso A — Mover archivos por bloque

### Paso B — Crear el Port y DTOs

### Paso C — Implementar el Port

Una clase agregadora `CatalogoFacadeImpl` que implementa `CatalogoPort` y delega a los services internos:

```java
@Service
class CatalogoFacadeImpl implements CatalogoPort {

    @Autowired private DepartamentoRepository departamentoRepo;
    @Autowired private ActividadRepository actividadRepo;
    @Autowired private PoliticaNegocioRepository politicaRepo;
    @Autowired private RolRepository rolRepo;
    @Autowired private PermisoRepository permisoRepo;

    @Override
    public Optional<DepartamentoInfo> buscarDepartamento(String id) {
        return departamentoRepo.findById(id).map(this::toDepartamentoInfo);
    }

    @Override
    public List<DepartamentoInfo> listarDepartamentosActivos() {
        return departamentoRepo.findByActivoTrue()
                .stream().map(this::toDepartamentoInfo).toList();
    }

    @Override
    public boolean esPoliticaActiva(String politicaId) {
        return politicaRepo.findById(politicaId)
                .map(p -> "activa".equals(p.getEstado()))
                .orElse(false);
    }

    // resto de métodos + mappers
}
```

### Paso D — Adaptar consumidores

`WorkflowEngineService` (al iniciar trámite):
```java
// Antes:
PoliticaNegocio politica = politicaRepository.findById(req.getPoliticaId())
    .orElseThrow(...);
if (!"activa".equals(politica.getEstado())) { throw ...; }

// Después:
if (!catalogo.esPoliticaActiva(req.getPoliticaId())) {
    throw new IllegalArgumentException("La política debe estar activa");
}
PoliticaInfo politica = catalogo.buscarPolitica(req.getPoliticaId())
    .orElseThrow(() -> new IllegalArgumentException("Política no encontrada"));
```

`PromptFlowService` (deuda de 1.7) — ahora ya tiene CatalogoPort disponible:
```java
// Antes:
List<Departamento> todos = departamentoRepository.findByActivoTrue();

// Después:
List<DepartamentoInfo> todos = catalogo.listarDepartamentosActivos();
```

`MetricasServiceImpl` (deuda de 1.5):
```java
// Antes:
Actividad act = actividadRepository.findById(actividadId).orElse(null);

// Después:
ActividadInfo act = catalogo.buscarActividad(actividadId).orElse(null);
```

### Paso E — `package-info.java` y README

```java
/**
 * Componente: catalogo
 *
 * Propósito:
 *   Configuración del sistema: departamentos, actividades, políticas de
 *   negocio, roles y permisos.
 *
 * Puerto público:
 *   - CatalogoPort (solo lecturas para otros componentes)
 *
 * Consume: ninguno
 *
 * Es consumido por:
 *   - workflow (lee políticas y departamentos)
 *   - workflowdesign (lee departamentos para generar diagramas)
 *   - metricas (lee actividades para SLA)
 *   - auth (lee roles para validar permisos)
 *
 * Colecciones MongoDB:
 *   - departamentos, actividades, politicas_negocio, versiones_politica
 *   - roles, permisos
 */
package com.example.demo.modules.catalogo;
```

---

## 6. Verificación

| Flujo | Esperado |
|-------|----------|
| GET `/api/departamentos` | 200 |
| POST `/api/departamentos` (admin) | 201 |
| GET `/api/politicas` | 200 |
| Iniciar trámite con política inactiva | 400 con mensaje correcto (vía CatalogoPort) |
| Generar diagrama por prompt detectando departamentos | 201 (vía CatalogoPort en PromptFlowService) |

---

## 7. Commit sugerido

```bash
git add .
git commit -m "refactor: extraer componente catalogo con CatalogoPort y resolver deudas de fases anteriores"
```

---

## 8. Cierre de deudas

Esta subfase cierra las deudas anotadas en:
- 1.5 metricas (acceso directo a `ActividadRepository`)
- 1.7 workflowdesign (acceso directo a `DepartamentoRepository` desde `PromptFlowService`)

Después de esta subfase, **ningún componente accede a repositorios de otro**.

---

## Próximo paso

Continuar con **`10_componente_ai_y_reportes.md`**.
