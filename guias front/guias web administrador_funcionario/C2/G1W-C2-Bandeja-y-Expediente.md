# Guía 1W-C2 — Bandeja de Entrada y Expediente Digital (CU-09, CU-10)

**Ciclo 2 · Sistema de Gestión de Trámites - Frontend Web (Administrador / Funcionario)**

> 🎯 **Objetivo:** Implementar la "Bandeja de Entrada" donde el Funcionario recibe los trámites derivados automáticamente por el motor de workflow (CU-09) y el visor interactivo del "Expediente Digital" para revisar toda la documentación e información técnica (CU-10).

---

## 0. Requisitos

✅ Framework Web configurado (Angular, React o Vue). Usaremos sintaxis **TypeScript (Angular/Servicios genéricos)** como base.
✅ Autenticación Web (Ciclo 1) implementada: El usuario actual debe tener rol `FUNCIONARIO` o `ADMINISTRADOR`.
✅ Backend Ciclo 2 corriendo en `http://localhost:8080`.

---

## 1. Actualización de Servicios API

Necesitamos dos endpoints clave del Ciclo 2:
1. **Mis Pendientes:** Obtiene los trámites que el motor de workflow asignó a la bandeja del usuario autenticado.
2. **Expediente Completo:** Obtiene las secciones, datos y documentos adjuntos de un trámite específico.

Crea o actualiza el servicio `tramite.service.ts`:

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class TramiteService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // CU-09: Recibir trámite asignado (Bandeja de entrada)
  getMisPendientes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/tramites/mis-pendientes`);
  }

  // CU-10: Revisar información del trámite
  getExpediente(tramiteId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/expedientes/tramite/${tramiteId}`);
  }
}
```

---

## 2. Pantalla 1: Bandeja de Entrada (CU-09)

Esta es la pantalla principal del Funcionario tras hacer Login. Debe cargar todos los procesos que esperan su intervención.

**`bandeja-entrada.component.ts`**:

```typescript
import { Component, OnInit } from '@angular/core';
import { TramiteService } from '../../services/tramite.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-bandeja-entrada',
  templateUrl: './bandeja-entrada.component.html',
  styleUrls: ['./bandeja-entrada.component.css']
})
export class BandejaEntradaComponent implements OnInit {
  tramitesPendientes: any[] = [];
  cargando = false;

  constructor(private tramiteService: TramiteService, private router: Router) {}

  ngOnInit(): void {
    this.cargando = true;
    this.tramiteService.getMisPendientes().subscribe({
      next: (data) => {
        this.tramitesPendientes = data;
        this.cargando = false;
      },
      error: (err) => {
        console.error('Error al cargar la bandeja:', err);
        this.cargando = false;
      }
    });
  }

  verExpediente(tramiteId: string): void {
    // Redirigir a la vista del Expediente Digital pasándole el ID
    this.router.navigate(['/tramites', tramiteId, 'expediente']);
  }
}
```

**`bandeja-entrada.component.html`**:

```html
<div class="container mt-4">
  <h2>Bandeja de Entrada</h2>
  <p class="text-muted">Trámites pendientes de su revisión según el Workflow.</p>

  <div *ngIf="cargando" class="spinner-border text-primary"></div>

  <table class="table table-hover mt-3" *ngIf="!cargando && tramitesPendientes.length > 0">
    <thead class="table-dark">
      <tr>
        <th>Código</th>
        <th>Estado Actual</th>
        <th>Fecha de Recibido</th>
        <th>Prioridad</th>
        <th>Acciones</th>
      </tr>
    </thead>
    <tbody>
      <tr *ngFor="let tramite of tramitesPendientes">
        <td><strong>{{ tramite.codigo }}</strong></td>
        <td><span class="badge bg-warning text-dark">{{ tramite.estadoActual }}</span></td>
        <td>{{ tramite.fechaInicio | date:'short' }}</td>
        <td>{{ tramite.prioridad }}</td>
        <td>
          <button class="btn btn-primary btn-sm" (click)="verExpediente(tramite.id)">
            📝 Revisar Expediente
          </button>
        </td>
      </tr>
    </tbody>
  </table>

  <div class="alert alert-info" *ngIf="!cargando && tramitesPendientes.length === 0">
    No tiene trámites asignados en este momento.
  </div>
</div>
```

---

## 3. Pantalla 2: Expediente Digital (CU-10)

Aquí el Funcionario inspecciona todo el historial, los documentos subidos y la información capturada hasta el momento. Esta vista lo "empapa" del contexto antes de accionar.

**`expediente-digital.component.ts`**:

```typescript
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TramiteService } from '../../services/tramite.service';

@Component({
  selector: 'app-expediente-digital',
  templateUrl: './expediente-digital.component.html',
  styleUrls: ['./expediente-digital.component.css']
})
export class ExpedienteDigitalComponent implements OnInit {
  tramiteId: string = '';
  expedienteData: any = null;
  cargando = false;

  constructor(
    private route: ActivatedRoute,
    private tramiteService: TramiteService
  ) {}

  ngOnInit(): void {
    this.tramiteId = this.route.snapshot.paramMap.get('id') || '';
    if (this.tramiteId) {
      this.cargarExpediente();
    }
  }

  cargarExpediente(): void {
    this.cargando = true;
    this.tramiteService.getExpediente(this.tramiteId).subscribe({
      next: (data) => {
        this.expedienteData = data;
        this.cargando = false;
      },
      error: (err) => {
        console.error('Error al cargar expediente:', err);
        this.cargando = false;
      }
    });
  }

  verDocumento(url: string | undefined): void {
    if (url) {
      window.open(url, '_blank');
    } else {
      alert('Documento no disponible');
    }
  }
}
```

**`expediente-digital.component.html`**:

```html
<div class="container mt-4" *ngIf="cargando">
  <div class="spinner-border text-primary"></div> Cargando expediente...
</div>

<div class="container mt-4" *ngIf="!cargando && expedienteData">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2>Expediente del Trámite</h2>
    <!-- expedienteData.expediente es el objeto ExpedienteDigital del backend -->
    <span class="badge bg-secondary fs-6">ID: {{ expedienteData.expediente?.tramiteId }}</span>
  </div>

  <div class="row">
    <!-- Columna Izquierda: Secciones y Datos -->
    <!-- La respuesta del backend tiene la forma: { expediente, secciones: [{ infoSeccion, campos }] } -->
    <div class="col-md-8">
      <div class="card mb-4" *ngFor="let seccion of expedienteData.secciones">
        <div class="card-header"
             [ngClass]="{
               'bg-success text-white': seccion.infoSeccion.estado === 'completada',
               'bg-warning text-dark':  seccion.infoSeccion.estado === 'en_curso',
               'bg-light':              seccion.infoSeccion.estado === 'bloqueada'
             }">
          <h5 class="mb-0">
            Sección {{ seccion.infoSeccion.ordenSeccion }} —
            Depto: {{ seccion.infoSeccion.departamentoId }}
            <span class="badge ms-2"
                  [ngClass]="{
                    'bg-success': seccion.infoSeccion.estado === 'completada',
                    'bg-warning text-dark': seccion.infoSeccion.estado === 'en_curso',
                    'bg-secondary': seccion.infoSeccion.estado === 'bloqueada'
                  }">
              {{ seccion.infoSeccion.estado }}
            </span>
          </h5>
        </div>
        <div class="card-body">
          <ul class="list-group list-group-flush">
            <!-- El campo del backend es "valor" (String único en CampoSeccion) -->
            <li class="list-group-item d-flex justify-content-between"
                *ngFor="let campo of seccion.campos">
              <strong>{{ campo.campoPlantillaId }}:</strong>
              <span>{{ campo.valor || 'Sin valor' }}</span>
            </li>
            <li *ngIf="!seccion.campos || seccion.campos.length === 0"
                class="list-group-item text-muted">
              Sin campos registrados.
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Columna Derecha: Resumen del expediente -->
    <div class="col-md-4">
      <div class="card shadow-sm border-info">
        <div class="card-header bg-info text-white">
          <h5 class="mb-0">📋 Resumen</h5>
        </div>
        <div class="card-body">
          <p><strong>Tramite ID:</strong> {{ expedienteData.expediente?.tramiteId }}</p>
          <p><strong>Creado:</strong> {{ expedienteData.expediente?.fechaCreacion | date:'short' }}</p>
          <p><strong>Última actualización:</strong> {{ expedienteData.expediente?.ultimaActualizacion | date:'short' }}</p>
          <p class="text-muted small">
            Los adjuntos por sección se implementarán en el siguiente ciclo.
          </p>
        </div>
      </div>
    </div>
  </div>

  <hr>
  <!-- Aquí irán los botones de Acción (Guía 2W-C2: Informe, Aprobar, Rechazar, Derivar) -->
  <div class="d-flex gap-2">
    <button class="btn btn-secondary" routerLink="/bandeja">Volver a la Bandeja</button>
    <button class="btn btn-success" disabled>Siguiente Paso (Próxima Guía)</button>
  </div>
</div>
```

---

## 4. Validación de la Guía

1. Iniciar sesión en la aplicación web con una cuenta de Funcionario (`funcionario@cre.bo`).
2. Navegar a la Bandeja de Entrada.
3. El sistema llamará a `/api/tramites/mis-pendientes` y dibujará los trámites asignados.
4. Al hacer clic en "Revisar Expediente", se entra a la lectura detallada de las secciones dinámicas y los documentos vinculados (`/api/expedientes/tramite/{id}`).