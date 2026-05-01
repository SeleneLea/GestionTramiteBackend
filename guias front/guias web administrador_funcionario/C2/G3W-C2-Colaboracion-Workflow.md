# Guía 3W-C2 — Edición Colaborativa de Workflows (CU-15)

**Ciclo 2 · Sistema de Gestión de Trámites - Frontend Web (Administrador)**

> 🎯 **Objetivo:** Permitir al Administrador invitar a otros usuarios (administradores o funcionarios) a colaborar en tiempo real sobre el diseño de un diagrama de workflow, implementando el límite de 4 ediciones activas (CU-15).

---

## 0. Requisitos

✅ Framework Web (Angular/React/Vue) con el componente base del "Editor Visual de Diagramas".
✅ Autenticación de Administrador.
✅ Backend G4-C2 corriendo con `ColaboracionController` en `/api/colaboracion`.

---

## 1. Servicio de Colaboración

Crea `colaboracion.service.ts`. La invitación usa un JSON body con el **ID** del usuario (no su email), tal como espera el `ColaboracionController` del backend.

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ColaboracionService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // CU-15: Invitar colaborador al diagrama
  // Endpoint: POST /api/colaboracion/diagrama/{diagramaId}/invitar
  // Body: InvitarColaboradorRequest { usuarioInvitadoId, permisos }
  invitarColaborador(diagramaId: string, usuarioInvitadoId: string, permisos: string = 'editor'): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/colaboracion/diagrama/${diagramaId}/invitar`, {
      usuarioInvitadoId: usuarioInvitadoId,  // ID del usuario, no email
      permisos: permisos                      // "editor" | "visualizador"
    });
  }

  // Obtener lista de usuarios disponibles para invitar
  // Reutiliza el endpoint de usuarios del Ciclo 1 (requiere rol ADMINISTRADOR)
  getUsuariosDisponibles(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/usuarios`);
  }
}
```

---

## 2. Interfaz del Editor Visual (Barra Superior)

En la vista donde el Administrador diagrama los nodos, agrega una barra con el modal de invitación. El modal muestra un `select` de usuarios (por ID y nombre) en lugar de un campo de email.

**`editor-workflow.component.html`** (encabezado del lienzo):

```html
<div class="d-flex justify-content-between align-items-center bg-light p-3 border-bottom">
  <h4 class="mb-0">Editor de Políticas: {{ nombreDiagrama }}</h4>

  <div class="d-flex align-items-center gap-3">
    <!-- Botón para disparar CU-15 -->
    <button class="btn btn-outline-primary" (click)="abrirModalInvitacion()">
      <i class="bi bi-person-plus-fill"></i> Invitar Colaborador
    </button>
  </div>
</div>

<!-- Modal de Invitación -->
<div class="modal fade" id="modalInvitar" tabindex="-1"
     [ngClass]="{'show d-block': mostrarModal}">
  <div class="modal-dialog">
    <div class="modal-content shadow-lg">
      <div class="modal-header bg-primary text-white">
        <h5 class="modal-title">Invitar Colaborador</h5>
        <button type="button" class="btn-close btn-close-white"
                (click)="cerrarModalInvitacion()"></button>
      </div>
      <div class="modal-body">
        <p>Selecciona el usuario que colaborará en el diseño de este diagrama.</p>
        <p class="text-muted small">
          Un usuario no puede participar en más de 4 diagramas simultáneamente.
        </p>

        <div *ngIf="cargandoUsuarios" class="text-center">
          <span class="spinner-border spinner-border-sm"></span> Cargando usuarios...
        </div>

        <div *ngIf="!cargandoUsuarios">
          <label class="form-label fw-bold">Usuario a invitar:</label>
          <select class="form-select mt-1" [(ngModel)]="usuarioInvitadoId">
            <option value="" disabled>-- Seleccione un usuario --</option>
            <option *ngFor="let u of usuariosDisponibles" [value]="u.id">
              {{ u.nombre }} — {{ u.email }}
            </option>
          </select>

          <label class="form-label fw-bold mt-3">Permisos:</label>
          <select class="form-select" [(ngModel)]="permisosInvitado">
            <option value="editor">Editor (puede modificar el diagrama)</option>
            <option value="visualizador">Visualizador (solo lectura)</option>
          </select>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary"
                (click)="cerrarModalInvitacion()">Cancelar</button>
        <button type="button" class="btn btn-success"
                [disabled]="!usuarioInvitadoId || enviando"
                (click)="enviarInvitacion()">
          <span *ngIf="enviando" class="spinner-border spinner-border-sm"></span>
          Enviar Invitación
        </button>
      </div>
    </div>
  </div>
</div>

<!-- Lienzo del diagrama -->
<div id="diagram-canvas" class="w-100 h-100 border">...</div>
```

---

## 3. Lógica del Componente

**`editor-workflow.component.ts`**:

```typescript
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ColaboracionService } from '../../services/colaboracion.service';

@Component({
  selector: 'app-editor-workflow',
  templateUrl: './editor-workflow.component.html',
  styleUrls: ['./editor-workflow.component.css']
})
export class EditorWorkflowComponent implements OnInit {
  diagramaId: string = '';
  nombreDiagrama: string = 'Flujo de Nueva Conexión';

  // Modal
  mostrarModal: boolean = false;
  enviando: boolean = false;

  // Datos del formulario de invitación
  usuarioInvitadoId: string = '';
  permisosInvitado: string = 'editor';

  // Lista de usuarios para el select
  usuariosDisponibles: any[] = [];
  cargandoUsuarios: boolean = false;

  constructor(
    private route: ActivatedRoute,
    private colabService: ColaboracionService
  ) {}

  ngOnInit(): void {
    this.diagramaId = this.route.snapshot.paramMap.get('id') || '';
  }

  abrirModalInvitacion(): void {
    this.mostrarModal = true;
    this.usuarioInvitadoId = '';
    this.permisosInvitado = 'editor';
    // Cargar usuarios solo la primera vez
    if (this.usuariosDisponibles.length === 0) {
      this.cargandoUsuarios = true;
      this.colabService.getUsuariosDisponibles().subscribe({
        next: (data) => {
          this.usuariosDisponibles = data;
          this.cargandoUsuarios = false;
        },
        error: () => {
          alert('No se pudo cargar la lista de usuarios.');
          this.cargandoUsuarios = false;
        }
      });
    }
  }

  cerrarModalInvitacion(): void {
    this.mostrarModal = false;
  }

  enviarInvitacion(): void {
    if (!this.usuarioInvitadoId) return;

    this.enviando = true;
    // Envía el ID del usuario y los permisos como JSON body
    this.colabService.invitarColaborador(
      this.diagramaId,
      this.usuarioInvitadoId,
      this.permisosInvitado
    ).subscribe({
      next: () => {
        alert('Invitación enviada correctamente.');
        this.cerrarModalInvitacion();
        this.enviando = false;
      },
      error: (err) => {
        // El backend devuelve 400 cuando el usuario ya tiene 4 diagramas activos (CU-15)
        if (err.status === 400) {
          alert('Error: El usuario ya ha alcanzado el límite de 4 ediciones activas simultáneas.');
        } else {
          alert('Error al enviar la invitación. Revise la consola.');
          console.error(err);
        }
        this.enviando = false;
      }
    });
  }
}
```

---

## 4. Responder Invitación (Vista del Invitado)

El usuario invitado recibe una notificación (almacenada en la colección `notificaciones`). Para aceptar o rechazar, el frontend debe llamar al segundo endpoint del backend:

```typescript
// En colaboracion.service.ts
responderInvitacion(colaboracionId: string, decision: 'ACEPTAR' | 'RECHAZAR'): Observable<any> {
  return this.http.post<any>(`${this.baseUrl}/colaboracion/${colaboracionId}/responder`, {
    decision: decision  // "ACEPTAR" | "RECHAZAR"
  });
}
```

---

## 5. Cierre del Ciclo 2 en Web

Con la adición del **Editor Colaborativo (CU-15)** para configurar los modelos de diagramas:
1. El diagrama se crea y ajusta entre varios (CU-15).
2. Los clientes inician un flujo con él (CU-07, Guía 1 Mobile).
3. Los usuarios web lo reciben (CU-09), lo abren (CU-10) y asumen acciones sobre el mismo (CU-11, CU-16, CU-17, CU-18).

🎉 **Las guías Web funcionales del Ciclo 2 están completas.**
