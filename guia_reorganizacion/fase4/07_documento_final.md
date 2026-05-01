# Fase 4.7 · Documento final de arquitectura (PDF para entregar)

> El **artefacto que se sube a Moodle**. Compila los diagramas, los ADRs y las decisiones en un PDF profesional listo para entregar.

---

## 1. Objetivo

Producir un PDF de ~10-15 páginas que un profesor pueda leer en 10 minutos y entender:

1. Qué hace el sistema
2. Cómo está arquitectado
3. Por qué se tomaron las decisiones clave
4. Cómo se verifica que la arquitectura se mantiene

---

## 2. Estructura del documento

```
Sistema de Gestión de Trámites
Documento de Arquitectura

1. Introducción
   1.1 Propósito del documento
   1.2 Alcance del sistema
   1.3 Stakeholders y roles

2. Arquitectura del sistema
   2.1 Vista de capas
   2.2 Vista de componentes (UML 2.5)        ← incluir imagen del diagrama
   2.3 Vista de despliegue                    ← incluir imagen
   2.4 Vista de procesos / secuencia          ← incluir 1-2 secuencias

3. Componentes del backend
   3.1 Inventario de componentes (tabla)
   3.2 Por cada componente: propósito, Port que provee, Ports que consume
   3.3 Shared kernel

4. Frontend Angular Web
   4.1 Estructura por features
   4.2 UI Kit reutilizable propio
   4.3 Lógica transversal en core/utils

5. Mobile Flutter
   5.1 Estructura por features
   5.2 Widget Kit reutilizable propio

6. Verificación arquitectónica
   6.1 ArchUnit — tests automatizados
   6.2 Resultado de ejecución (captura)

7. Decisiones arquitectónicas (ADRs resumidos)
   7.1 ADR-001 a ADR-008 (síntesis 1 párrafo c/u)

8. Conclusiones y trabajo futuro
   8.1 Lo que se logró en este parcial
   8.2 Deudas técnicas conocidas
   8.3 Próximos pasos recomendados

Anexos:
   A. Diagrama de actividad UML 2.5 del flujo CRE (de Enterprise Architect)
   B. Listado completo de Ports y sus métodos
   C. Convenciones de código aplicadas
```

---

## 3. Cómo armarlo

### Opción A — Word / Google Docs
Más simple si no estás familiarizado con LaTeX.
- Plantilla académica
- Insertar imágenes PNG de los diagramas
- Exportar a PDF al final

### Opción B — LaTeX
Más profesional, genera PDF de mayor calidad. Plantilla básica:

```latex
\documentclass[11pt,a4paper]{article}
\usepackage[utf8]{inputenc}
\usepackage[spanish]{babel}
\usepackage{graphicx}
\usepackage{hyperref}
\usepackage{booktabs}
\usepackage{fancyhdr}

\title{Sistema de Gestión de Trámites \\ \large Documento de Arquitectura}
\author{Equipo del proyecto}
\date{Abril 2026}

\begin{document}
\maketitle
\tableofcontents
\newpage

\section{Introducción}
\subsection{Propósito del documento}
Este documento describe la arquitectura del sistema de gestión de trámites
desarrollado como primer parcial...

\subsection{Alcance}
...

\section{Arquitectura del sistema}
\subsection{Vista de componentes}
\begin{figure}[h]
\centering
\includegraphics[width=\textwidth]{diagramas/componentes.png}
\caption{Diagrama de componentes UML 2.5}
\end{figure}

...

\end{document}
```

### Opción C — Markdown + Pandoc (intermedio)
Escribir en `.md`, exportar con:
```bash
pandoc documento.md -o documento.pdf --toc --pdf-engine=xelatex
```

---

## 4. Esqueleto en Markdown (para Pandoc)

Crear `fase4/documento_arquitectura.md` con esta estructura:

```markdown
---
title: "Sistema de Gestión de Trámites — Documento de Arquitectura"
author: "Equipo del proyecto"
date: "Abril 2026"
toc: true
toc-depth: 2
documentclass: article
geometry: margin=2.5cm
---

# 1. Introducción

## 1.1 Propósito
Este documento describe la arquitectura de software del sistema...

## 1.2 Alcance
El sistema cubre la gestión de trámites de principio a fin: diseño de flujos,
ejecución, expediente digital, notificaciones, métricas, reportes, integración IA.

## 1.3 Stakeholders
- Administrador (web)
- Funcionario (web)
- Cliente (Flutter)

# 2. Arquitectura del sistema

## 2.1 Vista de capas

![Vista de capas](diagramas/capas.png)

El sistema sigue una arquitectura por capas con inversión de dependencias entre componentes...

## 2.2 Vista de componentes (UML 2.5)

![Diagrama de componentes](diagramas/componentes.png)

El backend se divide en 9 componentes de negocio + shared kernel...

(continuar con cada sección)
```

---

## 5. Capítulo 3 — Inventario de componentes (tabla central)

```markdown
# 3. Componentes del backend

## 3.1 Inventario

| # | Componente | Provee | Consume | Resp.

```

Hay que producir una tabla así:

| # | Componente | Provee | Consume | Responsabilidad |
|---|------------|--------|---------|-----------------|
| 1 | shared | (utilidades) | — | Errores, configs, índices Mongo |
| 2 | auth | AuthPort, JwtPort | — | Autenticación, JWT, usuarios |
| 3 | catalogo | CatalogoPort | — | Departamentos, actividades, políticas, roles |
| 4 | notificaciones | NotificacionPort | — | Envío de notificaciones (web/push/email) |
| 5 | trazabilidad | TrazabilidadPort | — | Auditoría con hash chain SHA-256 |
| 6 | metricas | MetricasPort | CatalogoPort | Tiempos por actividad, cuellos de botella |
| 7 | expediente | ExpedientePort | — | Expediente digital, secciones, adjuntos |
| 8 | workflowdesign | WorkflowDesignPort | CatalogoPort | Diseño de diagramas, prompt-flow, colaboración |
| 9 | workflow (núcleo) | WorkflowEnginePort, TramiteQueryPort | 6 Ports | Motor de ejecución de trámites |
| 10 | aiintegration | VozPort, AgentePort | ExpedientePort | Microservicios IA (FastAPI) |
| 11 | reportes | ReportesPort | TramiteQueryPort | Generación y descarga de reportes CSV |

---

## 6. Capítulo 6 — Verificación arquitectónica

Incluir:
- Tabla de las 9 reglas ArchUnit
- Captura de pantalla de los tests pasando en verde
- Frase: *"Las reglas se ejecutan en cada build; cualquier violación rompe la compilación."*

---

## 7. Capítulo 7 — ADRs resumidos

Por cada ADR, **un párrafo** de máximo 4 líneas:

> **ADR-001 — Componentes vs Capas:** organizamos el código por dominio
> de negocio en `modules/<componente>/`, no por capa técnica. Cumple el
> requisito del enunciado de "componentes reutilizables" y reduce el
> blast radius de los cambios.

Repetir para los 8 ADRs. Total ~2 páginas.

---

## 8. Pasos finales

### Paso A — Recopilar todas las imágenes
Verificar que están en `fase4/diagramas/`:
- componentes.png
- despliegue.png
- capas.png
- hexagonal_workflow.png
- secuencia_iniciar_tramite.png
- secuencia_completar_nodo.png
- archunit_tests_passing.png
- actividad_swimlanes.png (el de UML 2.5 del flujo)

### Paso B — Escribir secciones 1-2 (intro + vistas)
Las más rápidas. ~2 horas.

### Paso C — Sección 3 (inventario de componentes)
Llenar tabla. Por cada componente, 1 párrafo. ~1 hora.

### Paso D — Secciones 4-5 (frontend Angular + Flutter)
Cada una: 1 página con estructura + componentes UI Kit. ~30 min cada una.

### Paso E — Sección 6 (ArchUnit)
Tabla de reglas + captura. ~20 min.

### Paso F — Sección 7 (ADRs resumidos)
Por cada ADR, 1 párrafo síntesis. ~30 min total.

### Paso G — Conclusiones y anexos
30 min.

### Paso H — Exportar a PDF
```bash
pandoc fase4/documento_arquitectura.md -o entrega/documento_arquitectura.pdf \
    --toc --pdf-engine=xelatex
```

### Paso I — Revisar el PDF visualmente
- Imágenes nítidas
- Sin saltos de página feos
- TOC correcto
- ~10-15 páginas

---

## 9. Commit

```bash
git add fase4/documento_arquitectura.md entrega/documento_arquitectura.pdf
git commit -m "docs(arquitectura): documento final de arquitectura PDF"
```

---

## Próximo paso

Continuar con **`08_presentacion.md`** — guion de los 20 minutos de defensa oral.
