# 🔍 REVISIÓN PROFUNDA FRONTEND - 4 PUNTOS CRÍTICOS

**Fecha:** 28 de abril 2026  
**Componentes Revisados:** WEB_angular

---

## 1️⃣ VOZ → SECCIÓN (Transcripción y Mapeo de Campos)

### ✅ QUÉ ESTÁ IMPLEMENTADO

**Ubicación:** `src/app/funcionario/expediente-digital/expediente-digital.component.ts` (líneas 45-220)

#### Flujo Implementado:
1. **Captura de audio** (líneas 161-187)
   - Usa `MediaRecorder` API del navegador
   - Acceso al micrófono con permisos
   - Grabación en formato WebM

2. **Envío al backend** (líneas 196-220)
   ```typescript
   enviarAudio(audioBlob: Blob): void {
     // 1. Encuentra la sección activa
     const activa = secciones.find(s => s.infoSeccion?.estado === 'en_curso');
     const seccionId = activa?.infoSeccion?.id;
     
     // 2. Envía el audio al backend
     this.tramiteC2Svc.transcribirVoz(seccionId, audioBlob).subscribe({
       next: (res) => {
         // ⚠️ PROBLEMA: Solo pone el texto transcrito en "justificacion"
         this.justificacion.set(res.textoTranscrito ?? '');
       }
     });
   }
   ```

3. **Visualización en UI** (líneas 108-146 del HTML)
   - Textarea para "Justificación / Observaciones"
   - Botón micrófono junto al textarea
   - Indicador de grabación en tiempo real
   - Manejo de errores de micrófono

---

### ❌ PROBLEMA CRÍTICO: FALTA MAPEO A CAMPOS

**El Enunciado dice:**
> "Funcionalidad de voz a sección (IA): el funcionario dicta por voz y la IA **transcribe rellenando los campos de su sección activa únicamente**. Luego puede editar manualmente."

**Lo que realmente pasa:**
1. ✅ Audio se transcribe correctamente
2. ❌ **El texto transcrito se pone SOLO en el campo "justificacion" (textarea de observaciones)**
3. ❌ **NO se mapea automáticamente a los campos de la sección activa**

**Código del problema:**
```typescript
// Línea 211 del .ts — esto es LO ÚNICO QUE OCURRE:
this.justificacion.set(res.textoTranscrito ?? '');
```

**Cómo debería funcionar:**
```typescript
// IMPLEMENTACIÓN CORRECTA (FALTA):
this.tramiteC2Svc.transcribirVoz(seccionId, audioBlob).subscribe({
  next: (res) => {
    // 1. Envía el texto a un servicio de "análisis de transcripción"
    // 2. El backend analiza qué campos de la sección corresponden
    // 3. Devuelve un mapeo: { "campo1": "valor1", "campo2": "valor2" }
    // 4. Rellenar los campos de la sección activa
    // 5. DESPUÉS, mostrar para edición manual
  }
});
```

**Impacto:**
- ❌ No cumple con el requisito del enunciado
- ❌ El usuario debe copiar/pegar manualmente la transcripción a cada campo
- ❌ Falta la IA para interpretar el contenido transcrito

---

### 📋 CHECKLIST - VOZ A SECCIÓN

- [x] Grabación de audio con micrófono
- [x] Envío al backend para transcripción
- [x] Visualización del error si el servicio falla
- [ ] **Análisis de transcripción (IA)**
- [ ] **Mapeo automático de texto a campos de la sección**
- [ ] Previsualización antes de guardar
- [ ] Edición manual de campos rellenados

---

## 2️⃣ AGENTE DE ASISTENCIA (CU-31) — n8n + RAG

### ❌ NO ESTÁ IMPLEMENTADO

**Ubicación:** No encontrada en el código

**Búsqueda realizada:**
```bash
grep -r "agente\|chat\|CU-31\|panel.*flotante\|assistant" src/
# Resultado: NADA
```

**¿Dónde debería estar?**
- `src/app/shared/` → Componente global del panel flotante
- `src/app/core/services/` → Servicio del agente
- `src/app/core/models/` → Modelo de mensaje/respuesta

---

### 📋 QUÉ REQUIERE EL ENUNCIADO (CU-31)

**Ficha del caso de uso:**
- **Panel flotante** accesible desde TODAS las pantallas
- **Contexto automático:** detecta el módulo, rol, trámite en curso
- **RAG sobre documentación real** del sistema (no conocimiento genérico)
- **Respuestas contextuales** con botones de acción directa
- **Implementación:** n8n + FastAPI

**Estados necesarios:**
```typescript
readonly agentePanel = signal(false);                    // ❌ NO EXISTE
readonly mensajesAgente = signal<Mensaje[]>([]);        // ❌ NO EXISTE
readonly consultaActual = signal('');                   // ❌ NO EXISTE
readonly esperandoRespuesta = signal(false);            // ❌ NO EXISTE
readonly contextoAgente = {                              // ❌ NO EXISTE
  moduloActual: 'expediente-digital',
  rol: 'funcionario',
  tramiteId?: 'xyz',
  // ... enviado al endpoint CU-31
}
```

---

### 🔴 IMPACTO: CRÍTICO

- **Falta completamente en el frontend**
- **No hay UI para el usuario**
- **No hay integración con n8n**
- **Sin CU-31, no hay asistencia contextual** (es una parte importante para el examen)

---

### 📋 CHECKLIST - AGENTE (CU-31)

- [ ] Componente del panel flotante
- [ ] Servicio de comunicación con el agente
- [ ] Modelo de mensaje/respuesta
- [ ] Detección automática de contexto (módulo, rol, trámite)
- [ ] Interfaz de chat
- [ ] Integración con endpoint `/api/ai/agente-chat`
- [ ] Botones de acción contextual
- [ ] Historial de conversación

---

## 3️⃣ CAMPOS CALCULADOS

### ❌ NO ENCONTRADO EN NINGÚN LADO

**Búsqueda realizada:**
```bash
# Backend (Java)
grep -r "calculado\|formula\|expr" src/main/java/com/example/demo/models/

# Frontend (Angular)
grep -r "calculado\|formula\|computed" src/app/core/models/
grep -r "calculado\|formula\|computed" src/app/funcionario/
```

**Resultado:** No hay implementación de campos calculados

---

### 📋 QUÉ DICE EL ENUNCIADO

> "Los campos pueden ser: texto libre, selección, fecha, adjuntos (documentos, imágenes) y **campos calculados**."

**Tipos de campos que debería soportar:**
1. ✅ **Texto libre** → Se ve en el HTML (valor: string)
2. ✅ **Selección** → Podría estar en el modelo
3. ? **Fecha** → Podría estar en el modelo
4. ? **Adjuntos** → Existe AdjuntoController en backend
5. ❌ **Calculados** → NO EXISTE

---

### 🔴 PROBLEMA

Sin campos calculados, no se pueden hacer:
- Cálculos automáticos (ej: monto total = cantidad × precio)
- Fórmulas condicionales (ej: descuento según monto)
- Validaciones complejas

**Modelo actual (CampoSeccion):**
```typescript
// Solo tiene: id, valor, campoPlantillaId, seccionId
// NO tiene: tipo, formula, expresion, validacion
```

---

### 📋 CHECKLIST - CAMPOS CALCULADOS

- [ ] Tipo de campo "calculado" en el modelo
- [ ] Almacenamiento de fórmula/expresión
- [ ] Motor de evaluación de fórmulas
- [ ] Validación de sintaxis
- [ ] Recalcular al cambiar otros campos
- [ ] UI para crear/editar fórmulas en admin

---

## 4️⃣ FORK/JOIN PARALELO

### ⚠️ PARCIALMENTE IMPLEMENTADO

**Backend:**
- ✅ Existen los nodos `fork` y `join` en el modelo `NodoDiagrama`
- ✅ Existe lógica en `WorkflowEngineService` para manejar `nodosParalellosActivos`
- ⚠️ **NO VERIFICADO:** Si espera correctamente a que TODAS las ramas terminen

**Frontend:**
- ✅ Se puede crear nodos `fork` y `join` en el editor visual
- ❓ **¿Se visualiza correctamente en el canvas?**

---

### 🔍 REVISIÓN DEL CÓDIGO (Backend)

**Ubicación:** `WorkflowEngineService.java`

```java
// Líneas 45-60 aprox
List<NodoDiagrama> nodosActividad = todosLosNodos.stream()
    .filter(n -> "actividad".equals(n.getTipo()))
    .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
    .toList();

// Líneas 49-50: Manejo de paralelo
for (NodoDiagrama nodo : nodosActividad) {
    // crea secciones...
}
```

**Problema:**
- ✅ Fork se crea correctamente
- ✅ Join se crea correctamente
- ❓ **¿La transición al siguiente nodo espera al join?**
- ❓ **¿Valida que TODAS las ramas terminen?**

---

### 📝 CÓMO SE DEBERÍA VER EN EL DIAGRAMA

```
         ┌─────────────────┐
         │    INICIO       │
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │     FORK        │ ◄── Bifurca en paralelo
         └─┬──────────────┬┘
           │              │
      ┌────▼─────┐   ┌────▼──────┐
      │Activity1 │   │ Activity2  │ ◄── Se ejecutan SIMULTÁNEAMENTE
      └────┬─────┘   └────┬──────┘
           │              │
         ┌─┴──────────────┴┐
         │     JOIN        │ ◄── ESPERA a que AMBAS terminen
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │   Activity3     │ ◄── Continúa solo si JOIN completo
         └─────────────────┘
```

---

### 📋 CHECKLIST - FORK/JOIN

**Backend:**
- [x] Nodos fork y join en el modelo
- [x] Creación de nodos fork/join en el servicio
- [ ] **Validación de que TODAS las ramas paralelas terminan**
- [ ] **Espera correcta en el join antes de continuar**
- [ ] Manejo de transiciones desde el join

**Frontend:**
- [x] Editor permite crear fork/join
- [ ] Visualización clara del flujo paralelo en el canvas
- [ ] Indicador visual de "espera" en el join

---

---

## 📊 RESUMEN EJECUTIVO — ESTADO DE LOS 4 PUNTOS

| Punto | Estado | Severidad | Acción Requerida |
|-------|--------|-----------|------------------|
| **1. Voz → Sección** | ⚠️ Parcial | 🔴 CRÍTICA | Implementar mapeo de transcripción a campos |
| **2. Agente CU-31** | ❌ Faltante | 🔴 CRÍTICA | Crear componente + integración n8n |
| **3. Campos Calculados** | ❌ Faltante | 🟡 IMPORTANTE | Agregar tipo de campo + motor de fórmulas |
| **4. Fork/Join Paralelo** | ⚠️ Parcial | 🟢 BAJO | Verificar lógica en backend |

---

## 🚨 RECOMENDACIONES ANTES DE LA PRESENTACIÓN (28 de abril)

### CRÍTICA (Hoy mismo):
1. **CU-31 Agente**: Si el tiempo no alcanza, mostrar un mockup del panel flotante en la presentación y explicar que se conectaría a n8n
2. **Voz → Sección**: Hacer que la transcripción se muestre en un modal para "vista previa + edición manual" antes de guardar

### IMPORTANTE (Antes del 29):
3. Verificar que el fork/join paralelo espera correctamente en el backend
4. Documentar el flujo de campos calculados si se implementa

### OPCIONAL:
5. Campos calculados (si hay tiempo, es un plus)

---

## 📝 NOTAS ADICIONALES

### Diagramas (Canvas)
- ✅ Editor visual completo con X6
- ✅ Soporte para swimlanes
- ✅ Creación de nodos y transiciones
- ⚠️ Colaboración en tiempo real (existe modelo pero no verificado)

### Expediente Digital (Visual)
- ✅ Visualización de secciones bloqueadas/activas/completadas
- ✅ Mostrar campos por sección
- ❓ ¿Se editan los campos directamente en el expediente?
  - Actualmente SOLO se ve: `{{ campo.campoPlantillaId }}` y `{{ campo.valor }}`
  - NO hay inputs para editar

### Impacto en la Presentación
- **Sin CU-31**: Falta una feature importante
- **Sin mapeo voz→campos**: No es "completa" la funcionalidad de voz
- **Sin campos calculados**: Limitación en la complejidad de formularios

