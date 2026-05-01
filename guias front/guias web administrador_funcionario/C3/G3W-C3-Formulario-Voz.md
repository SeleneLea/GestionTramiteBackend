# Guía 3W-C3 — Completar Formulario por Voz (CU-30)

**Ciclo 3 · Sistema de Gestión de Trámites - Frontend Web (Funcionario)**

> 🎯 **Objetivo:** Permitir al Funcionario dictar sus observaciones en el expediente utilizando el micrófono del navegador. El audio se envía al backend (Spring → FastAPI Speech-to-Text), la transcripción regresa como texto y se inserta automáticamente en el área de observaciones de la sección activa.

---

## 0. Requisitos

✅ Guía 2W-C2 (Panel de Acciones) implementada — el área de texto `justificacion` ya existe en `expediente-digital.component`.
✅ Backend G5-C3 corriendo: `POST /api/expedientes/secciones/{seccionId}/transcribir-voz`.
✅ El navegador del funcionario debe tener acceso al micrófono (HTTPS o localhost).
✅ La API Web `MediaRecorder` está disponible en Chrome, Edge y Firefox modernos (no requiere dependencia externa).

---

## 1. Actualización del Servicio de Expediente

Añade el método de transcripción en `tramite.service.ts`:

```typescript
  // CU-30: Enviar audio al backend para transcripción Speech-to-Text
  // Endpoint: POST /api/expedientes/secciones/{seccionId}/transcribir-voz
  // Body: multipart/form-data con campo "audio" (Blob en formato webm/ogg)
  // Respuesta: { textoTranscrito: string, confianza: number }
  transcribirVoz(seccionId: string, audioBlob: Blob): Observable<any> {
    const formData = new FormData();
    formData.append('audio', audioBlob, 'grabacion.webm');

    return this.http.post<any>(
      `${this.baseUrl}/expedientes/secciones/${seccionId}/transcribir-voz`,
      formData
      // No fijar Content-Type: el navegador lo establece automáticamente con el boundary correcto
    );
  }
```

---

## 2. Lógica de Grabación (TypeScript)

Añade estas variables y métodos al componente `expediente-digital.component.ts`. El componente ya tiene `tramiteId`, `seccionesAnteriores` y `justificacion` de la G2W-C2.

```typescript
  // ── Variables para CU-30 (Dictado por voz) ─────────────────────────────
  grabando: boolean = false;
  private mediaRecorder: MediaRecorder | null = null;
  private chunksAudio: BlobPart[] = [];
  errorMicrofono: string = '';

  // Obtiene el ID de la sección activa (en_curso) para enviarla al endpoint
  private get seccionActivaId(): string | null {
    if (!this.expedienteData) return null;
    const activa = this.expedienteData.secciones?.find(
      (s: any) => s.infoSeccion.estado === 'en_curso'
    );
    return activa?.infoSeccion?.id ?? null;
  }

  iniciarGrabacion(): void {
    this.errorMicrofono = '';
    if (!navigator.mediaDevices?.getUserMedia) {
      this.errorMicrofono = 'Tu navegador no soporta grabación de audio.';
      return;
    }

    navigator.mediaDevices.getUserMedia({ audio: true }).then((stream) => {
      this.chunksAudio = [];
      this.mediaRecorder = new MediaRecorder(stream);

      this.mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) this.chunksAudio.push(e.data);
      };

      this.mediaRecorder.onstop = () => {
        const audioBlob = new Blob(this.chunksAudio, { type: 'audio/webm' });
        this.enviarAudio(audioBlob);
        // Detener todas las pistas para apagar el indicador del micrófono
        stream.getTracks().forEach(t => t.stop());
      };

      this.mediaRecorder.start();
      this.grabando = true;
    }).catch(() => {
      this.errorMicrofono = 'No se pudo acceder al micrófono. Verifica los permisos del navegador.';
    });
  }

  detenerGrabacion(): void {
    if (this.mediaRecorder && this.grabando) {
      this.mediaRecorder.stop();
      this.grabando = false;
    }
  }

  private enviarAudio(audioBlob: Blob): void {
    const seccionId = this.seccionActivaId;
    if (!seccionId) {
      this.errorMicrofono = 'No hay sección activa en este expediente para transcribir.';
      return;
    }

    // Indicador visual mientras se procesa en el backend/FastAPI
    this.justificacion = '⏳ Transcribiendo audio...';

    this.tramiteService.transcribirVoz(seccionId, audioBlob).subscribe({
      next: (res) => {
        // Inserta el texto transcrito en el campo de observaciones
        this.justificacion = res.textoTranscrito ?? '';
      },
      error: () => {
        // Si el microservicio FastAPI falla, permite edición manual
        this.justificacion = '';
        this.errorMicrofono = 'El servicio de transcripción no está disponible. Escribe tu respuesta manualmente.';
      }
    });
  }
```

---

## 3. Botón de Micrófono en el HTML

Añade el botón de voz junto al área de texto de observaciones en `expediente-digital.component.html`, dentro del card de resolución de G2W-C2:

```html
<!-- Reemplaza el textarea de observaciones existente con este bloque -->
<div class="mb-3">
  <label class="form-label fw-bold">
    Justificación / Observaciones (Obligatorio):
  </label>

  <!-- Área de texto + botón de micrófono en la misma fila -->
  <div class="input-group">
    <textarea
      class="form-control"
      rows="3"
      [(ngModel)]="justificacion"
      [placeholder]="grabando ? '🎙️ Grabando... Habla ahora.' : 'Escribe o dicta tus observaciones...'"
      [class.border-danger]="grabando">
    </textarea>

    <!-- Botón CU-30: iniciar/detener grabación -->
    <button
      class="btn"
      [class.btn-danger]="grabando"
      [class.btn-outline-secondary]="!grabando"
      [title]="grabando ? 'Detener grabación' : 'Dictar por voz (CU-30)'"
      (click)="grabando ? detenerGrabacion() : iniciarGrabacion()">
      <i class="bi" [class.bi-mic-fill]="!grabando" [class.bi-stop-circle-fill]="grabando"></i>
    </button>
  </div>

  <!-- Indicador de grabación activa -->
  <div class="text-danger small mt-1 d-flex align-items-center gap-1" *ngIf="grabando">
    <span class="spinner-grow spinner-grow-sm text-danger"></span>
    Grabando... Haz clic en el botón rojo para detener y transcribir.
  </div>

  <!-- Error de micrófono o transcripción -->
  <div class="alert alert-warning py-2 mt-2" *ngIf="errorMicrofono">
    ⚠️ {{ errorMicrofono }}
  </div>
</div>
```

---

## 4. Flujo Completo CU-30

```
Funcionario abre el Expediente (G1W-C2)
    ↓
Llega al panel de resolución (G2W-C2)
    ↓
Hace clic en 🎙️ → navegador pide permiso de micrófono
    ↓
MediaRecorder graba en formato webm
    ↓
Funcionario hace clic en ⏹ → onstop() dispara
    ↓
POST /api/expedientes/secciones/{seccionId}/transcribir-voz (FormData con audio)
    ↓
Backend Spring → llama a FastAPI Speech-to-Text (puerto 8001)
    ↓
FastAPI transcribe → devuelve { textoTranscrito, confianza }
    ↓
Spring persiste en colección transcripcion_voz y devuelve la respuesta
    ↓
Frontend inserta textoTranscrito en el campo justificacion
    ↓
Funcionario revisa/edita el texto y ejecuta Aprobar / Rechazar / Devolver
```

---

## 5. Fallback si FastAPI no está disponible

El backend maneja la caída de FastAPI devolviendo un error 503. En ese caso el frontend:
1. Limpia el campo `justificacion` (borra el mensaje "⏳ Transcribiendo...")
2. Muestra `errorMicrofono` con el mensaje de escritura manual
3. El funcionario puede continuar escribiendo normalmente

---

## 6. Validación Final

1. Abre un expediente con una sección `en_curso`.
2. Haz clic en el micrófono y concede permiso al navegador.
3. Dicta: *"El solicitante presentó todos los documentos requeridos y cumple con los requisitos técnicos establecidos."*
4. Haz clic en detener. Aparecerá "⏳ Transcribiendo audio...".
5. Tras la respuesta del backend, el texto aparece en el área de observaciones.
6. Haz clic en **Aprobar** para completar el flujo.

---

## 7. Cierre completo del Frontend — Ciclo 3

Con esta guía, el frontend del sistema queda **100% completo**:

| Guía | Canal | CUs cubiertos |
|------|-------|---------------|
| G1W-C3 | Web Admin | CU-24 Métricas · CU-25 Cuellos de botella |
| G2W-C3 | Web Admin | CU-26 Reportes · CU-29 Historial auditoría |
| **G3W-C3** | **Web Funcionario** | **CU-30 Formulario por voz** |
| G1F-C3 | Flutter Cliente | CU-21 Línea de tiempo · CU-19 Cancelar trámite |
| G2F-C3 | Flutter Cliente | CU-28 Notificaciones · CU-31 Agente IA |
