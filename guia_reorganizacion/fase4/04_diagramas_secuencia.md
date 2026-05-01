# Fase 4.4 · Diagramas de Secuencia

> Mostrar **cómo colaboran los componentes** durante los flujos clave. Es la prueba viva de que los Ports realmente se usan en runtime.

---

## 1. Objetivo

Producir 2-3 diagramas de secuencia UML 2.5 para los flujos más representativos:

1. **Iniciar trámite** — el más complejo, toca casi todos los componentes
2. **Completar nodo** — el flujo runtime más frecuente
3. **(opcional)** Generar diagrama por prompt — muestra integración con IA

---

## 2. Secuencia 1: Iniciar trámite

### Actores y componentes que participan
- Cliente (Flutter)
- WorkflowController (REST)
- WorkflowEnginePort
- CatalogoPort
- WorkflowDesignPort
- ExpedientePort
- NotificacionPort
- TrazabilidadPort

### Bosquejo PlantUML

`fase4/diagramas/secuencia_iniciar_tramite.puml`:

```plantuml
@startuml secuencia_iniciar
title Secuencia — Iniciar Trámite

actor "Cliente\n(Flutter)" as C
participant "WorkflowController" as WC
participant "WorkflowEnginePort" as WP
participant "CatalogoPort" as CP
participant "WorkflowDesignPort" as DP
participant "ExpedientePort" as EP
participant "NotificacionPort" as NP
participant "TrazabilidadPort" as TP

C -> WC : POST /api/tramites/iniciar\n{politicaId, prioridad, ...}
activate WC

WC -> WP : iniciar(req)
activate WP

WP -> CP : esPoliticaActiva(politicaId)
CP --> WP : true

WP -> CP : buscarPolitica(politicaId)
CP --> WP : PoliticaInfo (incluye diagramaId)

WP -> DP : listarNodosDeDiagrama(diagramaId)
DP --> WP : List<NodoInfo>

WP -> WP : crear Tramite + persistir
note right: estado="Nuevo"

WP -> EP : crearParaTramite(tramiteId)
EP --> WP : expedienteId

WP -> EP : crearSeccionesIniciales(expedienteId, nodosActividad)
EP --> WP : List<seccionId>

WP -> WP : avanzarDesde(nodoInicio)
note right: motor evalúa\ny pasa al primer nodo activo

WP -> EP : desbloquearSeccionDeNodo(expedienteId, nodoId)
EP --> WP : OK

WP -> NP : enviar(NotificacionRequest)\n("trámite avanzó de etapa")
NP --> WP : OK

WP -> TP : registrar(tramiteId, clienteId, "iniciar", ...)
TP --> WP : EventoTrazabilidad

WP --> WC : TramiteResponse
deactivate WP

WC --> C : 201 Created\nTramiteResponse
deactivate WC

@enduml
```

---

## 3. Secuencia 2: Completar nodo

```plantuml
@startuml secuencia_completar
title Secuencia — Completar Nodo

actor "Funcionario\n(Web)" as F
participant "WorkflowController" as WC
participant "WorkflowEnginePort" as WP
participant "ExpedientePort" as EP
participant "WorkflowDesignPort" as DP
participant "MetricasPort" as MP
participant "NotificacionPort" as NP
participant "TrazabilidadPort" as TP

F -> WC : POST /api/tramites/{id}/completar-nodo\n{decision, notas, funcionarioId}
activate WC
WC -> WP : completarNodo(id, req)
activate WP

WP -> EP : completarSeccionDeNodo(expedienteId, nodoActivo, funcionarioId)
EP --> WP : SeccionResponse (con fechas inicio/fin)

WP -> MP : registrarMetricaActividad(\n  tramiteId, actividadId, deptoId,\n  inicio, fin)
note right: registra tiempo vs SLA

WP -> DP : listarTransicionesDesde(nodoActivo)
DP --> WP : List<TransicionInfo>

WP -> WP : evaluar tipo de nodo\n(decision/fork/join/actividad/fin)

alt nodo tipo "actividad"
    WP -> EP : desbloquearSeccionDeNodo(expedienteId, nuevoNodoId)
    WP -> NP : enviar("tramite asignado a tu bandeja",\n  funcionarioReceptor)
    WP -> NP : enviar("tu trámite avanzó de etapa",\n  cliente)
end

alt fin del flujo
    WP -> WP : cerrarTramite(estado="Aprobado")
    WP -> NP : enviar("tu trámite fue aprobado", cliente)
end

WP -> TP : registrar(tramiteId, funcionarioId, "completar_nodo", ...)
TP --> WP : EventoTrazabilidad

WP --> WC : TramiteResponse
deactivate WP
WC --> F : 200 OK
deactivate WC

@enduml
```

---

## 4. Secuencia 3: Generar diagrama por prompt (opcional)

```plantuml
@startuml secuencia_prompt
title Secuencia — Generar diagrama desde prompt (CU-14)

actor "Administrador\n(Web)" as A
participant "PromptFlowController" as PC
participant "WorkflowDesignPort" as DP
participant "CatalogoPort" as CP

A -> PC : POST /api/workflow-design/from-prompt\n{nombreDiagrama, prompt, politicaId}
activate PC
PC -> DP : generarDesdePrompt(req, adminId)
activate DP

DP -> CP : listarDepartamentosActivos()
CP --> DP : List<DepartamentoInfo>

DP -> DP : detectar departamentos\nen el prompt (nombre o código)

alt no se detecta ningún departamento
    DP --> PC : IllegalArgumentException
    PC --> A : 400 Bad Request\n"No se detectó ningún depto..."
end

DP -> DP : detectar paralelo / decisión\nen el prompt

DP -> DP : crear nodos (inicio, actividades, fork/join,\n  decisión, fin)

DP -> DP : crear transiciones según patrón

DP --> PC : PromptFlujoResponse(diagrama, nodos, transiciones)
deactivate DP

PC --> A : 201 Created
deactivate PC
@enduml
```

---

## 5. Pasos

### Paso A — Generar las imágenes
Renderizar cada `.puml` con la extensión PlantUML de VS Code (`Alt+D`) y exportar PNG.

### Paso B — Crear los mismos diagramas en EA (si el profe lo exige)
Paquete "5. Diagramas de Secuencia" → New Diagram → Sequence.

### Paso C — Guardar
- `fase4/diagramas/secuencia_iniciar_tramite.png`
- `fase4/diagramas/secuencia_completar_nodo.png`
- `fase4/diagramas/secuencia_prompt.png` (opcional)

---

## 6. Verificación

- [ ] Cada secuencia muestra al menos 4 componentes colaborando
- [ ] Las llamadas son a **Ports** (interfaces), no a clases concretas
- [ ] Los `alt` blocks reflejan ramas reales del código (decisión, paralelo, fin)
- [ ] Los nombres de método coinciden con los del código real

---

## 7. Cómo presentarlo

> *"Aquí muestro cómo se ven los Ports en runtime. Cuando un cliente inicia un trámite, el WorkflowEnginePort orquesta a 5 componentes diferentes — todos vía sus interfaces. Si mañana cambio cómo se envían las notificaciones (de web a push), nada de esto cambia, solo el adaptador que implementa NotificacionPort."*

---

## 8. Commit

```bash
git add fase4/diagramas/secuencia_*.png fase4/diagramas/secuencia_*.puml
git commit -m "docs(arquitectura): diagramas de secuencia de flujos clave"
```

---

## Próximo paso

Continuar con **`05_archunit_tests.md`** — el diferenciador técnico de la entrega.
