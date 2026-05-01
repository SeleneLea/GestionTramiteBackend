# Fase 4 · Validación arquitectónica

> La fase **más importante para la nota**. Aquí se demuestra que la arquitectura por componentes es real, verificable y coherente — no solo cosmética. Es lo que el profesor evaluará con mayor peso.

---

## Por qué esta fase

Las fases 1-3 reorganizaron el código. Esta fase produce los **artefactos** que demuestran al profesor que:

1. La arquitectura está **diseñada** (no es accidente del refactor)
2. Está **documentada** (diagramas UML 2.5)
3. Está **automatizada** (tests que la verifican en CI)
4. Las **decisiones** están justificadas (ADRs)

> Sin fase 4, las fases 1-3 son solo "limpieza de código". Con fase 4 son una arquitectura formal.

---

## Plan de la fase 4

| # | Archivo | Tarea | Tiempo | Importancia |
|---|---------|-------|--------|-------------|
| 0 | `00_preparacion.md` | Setup de herramientas | 30 min | — |
| 1 | `01_diagrama_componentes.md` | Diagrama UML de componentes con `<<provides>>`/`<<requires>>` | 2 h | 🔴 Crítica |
| 2 | `02_diagrama_despliegue.md` | Diagrama de despliegue (nodos físicos) | 1 h | 🔴 Crítica |
| 3 | `03_diagrama_capas.md` | Vista por capas / hexagonal | 1 h | 🟡 Importante |
| 4 | `04_diagramas_secuencia.md` | Secuencias de flujos clave (iniciar trámite, completar nodo) | 1.5 h | 🟡 Importante |
| 5 | `05_archunit_tests.md` | Tests Java que verifican la arquitectura | 2 h | 🟢 Diferenciador |
| 6 | `06_adrs.md` | Architectural Decision Records | 1.5 h | 🟡 Importante |
| 7 | `07_documento_final.md` | Documento PDF para entregar al profesor | 2 h | 🔴 Crítica |
| 8 | `08_presentacion.md` | Guion de los 20 min de presentación | 1 h | 🔴 Crítica |

**Total estimado: ~12 horas (~1.5 días).**

---

## Estructura

```
fase4/
├── README.md                          ← este archivo
├── 00_preparacion.md
├── 01_diagrama_componentes.md
├── 02_diagrama_despliegue.md
├── 03_diagrama_capas.md
├── 04_diagramas_secuencia.md
├── 05_archunit_tests.md
├── 06_adrs.md
├── 07_documento_final.md
├── 08_presentacion.md
├── diagramas/                         ← imágenes y archivos .eap
│   ├── componentes.png
│   ├── componentes.eap
│   ├── despliegue.png
│   ├── despliegue.eap
│   ├── capas.png
│   ├── secuencia_iniciar_tramite.png
│   ├── secuencia_completar_nodo.png
│   └── actividad_swimlanes.png        ← el diagrama UML 2.5 del flujo (CRE)
└── adr/                               ← Architectural Decision Records
    ├── ADR-001-componentes-vs-capas.md
    ├── ADR-002-puertos-y-adaptadores.md
    ├── ADR-003-shared-kernel.md
    ├── ADR-004-no-camunda.md
    ├── ADR-005-cqrs-light-workflow.md
    └── ADR-006-features-por-dominio.md
```

---

## Herramientas

| Para qué | Herramienta | Por qué |
|----------|-------------|---------|
| Diagramas UML 2.5 | **Enterprise Architect** | Lo pide el enunciado explícitamente |
| Diagramas alternativos rápidos | draw.io / PlantUML | Para borradores y diagramas que no necesitan formalismo UML 2.5 |
| Tests de arquitectura | **ArchUnit** | Estándar Java para enforcement automático |
| Documento final | LaTeX o Word | El PDF que se entrega al profesor |

---

## Lo que NO se hace en fase 4

- **No** se cambia código de producción
- **No** se agregan features
- **No** se refactoriza más

Esta fase es **100% documentación + tests**.

---

## Próximo paso

Empezar por **`00_preparacion.md`**.
