# Guía 4W — Motor de Workflow · Panel Funcionario

**Ciclo 1 · Sistema de Gestión de Trámites — Frontend Web**

> **Objetivo:** Pantallas para que el Funcionario vea los trámites activos asignados a su departamento y pueda completar los nodos del workflow.

---

## 0. Prerequisitos

- G1W completada (login, guards)
- Backend motor de workflow corriendo (`/api/workflow/**`)
- Usuario funcionario: `funcionario@cre.bo` / `func12345` (depto TEC)

---

## 1. Modelos

### src/app/core/models/tramite.model.ts
```typescript
export interface TramiteResumen {
  id: string;
  codigo: string;
  politicaId: string;
  politicaNombre?: string;
  clienteId: string;
  clienteNombre?: string;
  estado: string;       // "activo" | "completado" | "archivado"
  prioridad: number;
  progreso: number;     // 0-100
  fechaInicio: string;
  fechaLimite?: string;
  nodoActualId?: string;
  nodoActualNombre?: string;
}

export interface TramiteDetalle extends TramiteResumen {
  historial: HistorialNodo[];
  nodoActual?: NodoEstado;
}

export interface NodoEstado {
  nodoId: string;
  nombre: string;
  tipo: string;         // "actividad" | "fork" | "join" | "decision" | "fin"
  departamentoId?: string;
  actividadId?: string;
  estado: string;       // "pendiente" | "en_progreso" | "completado"
  funcionarioId?: string;
  fechaInicio?: string;
}

export interface HistorialNodo {
  nodoId: string;
  nombre: string;
  estado: string;
  funcionarioId?: string;
  fechaCompletado?: string;
  resultado?: string;
  observaciones?: string;
}

export interface CompletarNodoRequest {
  resultado: string;      // "aprobado" | "rechazado" | "completado"
  observaciones?: string;
  datos?: Record<string, any>;
}
```

---

## 2. Servicio de Workflow

### src/app/core/services/workflow.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TramiteResumen, TramiteDetalle, CompletarNodoRequest } from '../models/tramite.model';

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private url = `${environment.apiUrl}/workflow`;

  constructor(private http: HttpClient) {}

  // Listar todos los trámites (funcionario ve los asignados a su depto)
  listarTramites(): Observable<TramiteResumen[]> {
    return this.http.get<TramiteResumen[]>(`${this.url}/tramites`);
  }

  // Obtener estado actual de un trámite
  obtenerEstado(tramiteId: string): Observable<TramiteDetalle> {
    return this.http.get<TramiteDetalle>(`${this.url}/${tramiteId}/estado`);
  }

  // Completar el nodo actual
  completarNodo(tramiteId: string, data: CompletarNodoRequest): Observable<TramiteDetalle> {
    return this.http.post<TramiteDetalle>(`${this.url}/${tramiteId}/completar-nodo`, data);
  }

  // Ver historial completo
  obtenerHistorial(tramiteId: string): Observable<HistorialNodo[]> {
    return this.http.get<any[]>(`${this.url}/${tramiteId}/historial`);
  }
}
```

---

## 3. Componente: Lista de Trámites (Funcionario)

### tramites-lista.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { WorkflowService } from '../../../core/services/workflow.service';
import { TramiteResumen } from '../../../core/models/tramite.model';

@Component({
  selector: 'app-tramites-lista',
  templateUrl: './tramites-lista.component.html'
})
export class TramitesListaComponent implements OnInit {
  tramites: TramiteResumen[] = [];
  loading = false;
  error = '';
  filtroEstado = '';

  estados = ['activo', 'en_progreso', 'completado', 'archivado'];

  estadoColores: Record<string, string> = {
    activo:       'bg-primary',
    en_progreso:  'bg-warning text-dark',
    completado:   'bg-success',
    archivado:    'bg-secondary'
  };

  constructor(private workflowSvc: WorkflowService) {}

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.loading = true;
    this.workflowSvc.listarTramites().subscribe({
      next: t => { this.tramites = t; this.loading = false; },
      error: () => { this.error = 'Error al cargar trámites'; this.loading = false; }
    });
  }

  get tramitesFiltrados(): TramiteResumen[] {
    if (!this.filtroEstado) return this.tramites;
    return this.tramites.filter(t => t.estado === this.filtroEstado);
  }

  getPrioridadLabel(p: number): string {
    const labels: Record<number, string> = { 1: 'Baja', 2: 'Normal', 3: 'Alta', 4: 'Urgente', 5: 'Crítica' };
    return labels[p] ?? `P${p}`;
  }
}
```

### tramites-lista.component.html
```html
<div class="container-fluid py-4">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h4 class="mb-0">Trámites Activos</h4>
    <button class="btn btn-sm btn-outline-secondary" (click)="cargar()">
      ↻ Actualizar
    </button>
  </div>

  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <!-- Filtro por estado -->
  <div class="mb-3 d-flex gap-2 flex-wrap">
    <button class="btn btn-sm"
            [ngClass]="!filtroEstado ? 'btn-primary' : 'btn-outline-secondary'"
            (click)="filtroEstado = ''">Todos</button>
    <button *ngFor="let e of estados" class="btn btn-sm"
            [ngClass]="filtroEstado === e ? 'btn-primary' : 'btn-outline-secondary'"
            (click)="filtroEstado = e">{{ e }}</button>
  </div>

  <div *ngIf="loading" class="text-center py-5">
    <div class="spinner-border text-primary"></div>
  </div>

  <div *ngIf="!loading" class="row g-3">
    <div *ngFor="let t of tramitesFiltrados" class="col-md-6 col-xl-4">
      <div class="card h-100">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-bold text-monospace">{{ t.codigo }}</span>
          <span class="badge" [ngClass]="estadoColores[t.estado] ?? 'bg-secondary'">
            {{ t.estado }}
          </span>
        </div>
        <div class="card-body">
          <p class="mb-1 fw-semibold">{{ t.politicaNombre ?? 'Trámite' }}</p>
          <p class="small text-muted mb-2">Cliente: {{ t.clienteNombre ?? t.clienteId }}</p>

          <!-- Progreso -->
          <div class="mb-2">
            <div class="d-flex justify-content-between small mb-1">
              <span>Progreso</span><span>{{ t.progreso }}%</span>
            </div>
            <div class="progress" style="height: 6px">
              <div class="progress-bar bg-primary" [style.width]="t.progreso + '%'"></div>
            </div>
          </div>

          <div class="small text-muted">
            Prioridad: <strong>{{ getPrioridadLabel(t.prioridad) }}</strong><br>
            <span *ngIf="t.nodoActualNombre">Nodo actual: <em>{{ t.nodoActualNombre }}</em></span>
          </div>
        </div>
        <div class="card-footer">
          <a [routerLink]="['/funcionario/tramites', t.id]" class="btn btn-primary btn-sm w-100">
            Ver detalle y procesar
          </a>
        </div>
      </div>
    </div>

    <div *ngIf="tramitesFiltrados.length === 0" class="col-12">
      <div class="text-center text-muted py-5">No hay trámites en este estado</div>
    </div>
  </div>
</div>
```

---

## 4. Componente: Detalle + Completar Nodo

### tramite-detalle.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { WorkflowService } from '../../../core/services/workflow.service';
import { TramiteDetalle } from '../../../core/models/tramite.model';

@Component({
  selector: 'app-tramite-detalle',
  templateUrl: './tramite-detalle.component.html'
})
export class TramiteDetalleComponent implements OnInit {
  tramite!: TramiteDetalle;
  form!: FormGroup;
  loading = false;
  procesando = false;
  error = '';
  exito = '';
  tramiteId = '';

  resultadosPosibles: Record<string, string[]> = {
    derivar:    ['completado'],
    completar:  ['completado'],
    aprobar:    ['aprobado', 'rechazado'],
  };

  constructor(
    private route: ActivatedRoute,
    private fb: FormBuilder,
    private workflowSvc: WorkflowService
  ) {}

  ngOnInit(): void {
    this.tramiteId = this.route.snapshot.params['id'];
    this.iniciarForm();
    this.cargar();
  }

  iniciarForm(): void {
    this.form = this.fb.group({
      resultado:     ['completado', Validators.required],
      observaciones: ['']
    });
  }

  cargar(): void {
    this.loading = true;
    this.workflowSvc.obtenerEstado(this.tramiteId).subscribe({
      next: t => { this.tramite = t; this.actualizarResultados(); this.loading = false; },
      error: () => { this.error = 'Error al cargar el trámite'; this.loading = false; }
    });
  }

  actualizarResultados(): void {
    const tipoSalida = this.tramite?.nodoActual?.actividadId ? 'aprobar' : 'completar';
    const opciones = this.resultadosPosibles[tipoSalida] ?? ['completado'];
    this.form.patchValue({ resultado: opciones[0] });
  }

  get puedeCompletar(): boolean {
    return !!this.tramite?.nodoActual && this.tramite.nodoActual.tipo === 'actividad'
           && this.tramite.estado !== 'completado';
  }

  completarNodo(): void {
    if (this.form.invalid || !this.puedeCompletar) return;
    this.procesando = true;
    this.error = '';

    this.workflowSvc.completarNodo(this.tramiteId, this.form.value).subscribe({
      next: t => {
        this.tramite = t;
        this.exito = 'Nodo completado correctamente';
        this.form.reset({ resultado: 'completado', observaciones: '' });
        this.procesando = false;
        setTimeout(() => this.exito = '', 4000);
      },
      error: err => {
        this.error = err.error?.message ?? 'Error al completar el nodo';
        this.procesando = false;
      }
    });
  }

  getEstadoColor(estado: string): string {
    const colores: Record<string, string> = {
      pendiente:   'bg-secondary',
      en_progreso: 'bg-warning text-dark',
      completado:  'bg-success',
      activo:      'bg-primary'
    };
    return colores[estado] ?? 'bg-secondary';
  }
}
```

### tramite-detalle.component.html
```html
<div class="container py-4" *ngIf="tramite">
  <div class="d-flex align-items-center gap-3 mb-4">
    <a routerLink="/funcionario/tramites" class="btn btn-sm btn-outline-secondary">← Volver</a>
    <h4 class="mb-0">Trámite: <code>{{ tramite.codigo }}</code></h4>
    <span class="badge bg-primary">{{ tramite.estado }}</span>
  </div>

  <div *ngIf="exito" class="alert alert-success">{{ exito }}</div>
  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
  <div *ngIf="loading" class="text-center py-5"><div class="spinner-border text-primary"></div></div>

  <div *ngIf="!loading" class="row g-4">

    <!-- Info General -->
    <div class="col-md-5">
      <div class="card mb-3">
        <div class="card-header fw-semibold">Información del Trámite</div>
        <div class="card-body small">
          <table class="table table-sm mb-0">
            <tr><td class="text-muted">Política</td><td>{{ tramite.politicaNombre }}</td></tr>
            <tr><td class="text-muted">Cliente</td><td>{{ tramite.clienteNombre }}</td></tr>
            <tr><td class="text-muted">Inicio</td><td>{{ tramite.fechaInicio | date:'short' }}</td></tr>
            <tr><td class="text-muted">Progreso</td>
              <td>
                <div class="progress" style="height:6px; width:100px">
                  <div class="progress-bar" [style.width]="tramite.progreso+'%'"></div>
                </div>
                {{ tramite.progreso }}%
              </td>
            </tr>
          </table>
        </div>
      </div>

      <!-- Nodo Actual -->
      <div class="card" *ngIf="tramite.nodoActual">
        <div class="card-header fw-semibold d-flex justify-content-between">
          Nodo Actual
          <span class="badge" [ngClass]="getEstadoColor(tramite.nodoActual.estado)">
            {{ tramite.nodoActual.estado }}
          </span>
        </div>
        <div class="card-body">
          <p class="fw-bold mb-1">{{ tramite.nodoActual.nombre }}</p>
          <p class="text-muted small">Tipo: {{ tramite.nodoActual.tipo }}</p>

          <!-- Formulario completar nodo -->
          <div *ngIf="puedeCompletar" class="mt-3 border-top pt-3">
            <p class="fw-semibold small mb-2">Completar este nodo:</p>
            <form [formGroup]="form" (ngSubmit)="completarNodo()">
              <div class="mb-2">
                <label class="form-label small">Resultado *</label>
                <select class="form-select form-select-sm" formControlName="resultado">
                  <option value="completado">Completado</option>
                  <option value="aprobado">Aprobado</option>
                  <option value="rechazado">Rechazado</option>
                </select>
              </div>
              <div class="mb-3">
                <label class="form-label small">Observaciones</label>
                <textarea class="form-control form-control-sm" formControlName="observaciones" rows="2"></textarea>
              </div>
              <button type="submit" class="btn btn-success btn-sm w-100" [disabled]="procesando">
                <span *ngIf="procesando" class="spinner-border spinner-border-sm me-2"></span>
                {{ procesando ? 'Procesando...' : 'Completar Nodo' }}
              </button>
            </form>
          </div>
          <div *ngIf="tramite.estado === 'completado'" class="text-center text-success mt-3">
            Trámite finalizado
          </div>
        </div>
      </div>
    </div>

    <!-- Historial -->
    <div class="col-md-7">
      <div class="card">
        <div class="card-header fw-semibold">Historial del Flujo</div>
        <div class="card-body p-0">
          <ul class="list-group list-group-flush">
            <li *ngFor="let h of tramite.historial" class="list-group-item py-2">
              <div class="d-flex justify-content-between align-items-start">
                <div>
                  <span class="fw-semibold">{{ h.nombre }}</span><br>
                  <span class="text-muted small" *ngIf="h.observaciones">{{ h.observaciones }}</span>
                </div>
                <div class="text-end">
                  <span class="badge" [ngClass]="getEstadoColor(h.estado)">{{ h.estado }}</span><br>
                  <span class="text-muted small" *ngIf="h.resultado">→ {{ h.resultado }}</span>
                </div>
              </div>
            </li>
            <li *ngIf="!tramite.historial?.length" class="list-group-item text-muted text-center py-3">
              Sin historial aún
            </li>
          </ul>
        </div>
      </div>
    </div>

  </div>
</div>

<div class="container py-4 text-center" *ngIf="!tramite && !loading">
  <p class="text-muted">Trámite no encontrado</p>
  <a routerLink="/funcionario/tramites" class="btn btn-secondary">← Volver</a>
</div>
```

---

## 5. Rutas del módulo Funcionario

### funcionario-routing.module.ts
```typescript
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TramitesListaComponent } from './tramites-lista/tramites-lista.component';
import { TramiteDetalleComponent } from './tramite-detalle/tramite-detalle.component';

const routes: Routes = [
  { path: '',          redirectTo: 'tramites', pathMatch: 'full' },
  { path: 'tramites',  component: TramitesListaComponent },
  { path: 'tramites/:id', component: TramiteDetalleComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class FuncionarioRoutingModule {}
```

---

## 6. Flujo CRE — Cómo completar el workflow en demo

El trámite "Nueva conexión residencial" tiene este flujo:

```
INICIO
  └─▶ ATC: Verificar Documentos
        └─▶ FORK
              ├─▶ TEC: Inspección en Campo   ─┐
              └─▶ TEC: Elaborar Presupuesto  ─┤
                                              ▼
                                             JOIN
                                              └─▶ LEG: Revisar Contrato
                                                    └─▶ DECISION
                                                          ├─▶ [Aprobado] OPE: Cierre y Conexión → FIN
                                                          └─▶ [Rechazado] vuelve a FORK
```

**Pasos para la demo:**

1. El cliente (Flutter) inicia el trámite → aparece en la lista del funcionario
2. Funcionario ATC completa "Verificar Documentos" → resultado: `completado`
3. Motor avanza al FORK → lanza paralelo TEC
4. Funcionario TEC completa "Inspección en Campo" → resultado: `completado`
5. Funcionario TEC completa "Elaborar Presupuesto" → resultado: `completado`
6. Motor detecta JOIN completado → avanza a LEG
7. Funcionario LEG completa "Revisar Contrato" → resultado: `aprobado`
8. Motor evalúa DECISION → avanza a OPE
9. Funcionario OPE completa "Cierre y Conexión" → resultado: `completado`
10. Motor llega al FIN → trámite `completado`

---

## 7. Endpoints utilizados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/workflow/tramites` | Listar trámites activos |
| GET | `/api/workflow/{id}/estado` | Estado actual del trámite |
| POST | `/api/workflow/{id}/completar-nodo` | Completar nodo activo |
| GET | `/api/workflow/{id}/historial` | Historial de nodos |

---

## Checklist G4W

- [ ] Crear modelo `tramite.model.ts`
- [ ] Implementar `WorkflowService` con `listarTramites()`, `obtenerEstado()`, `completarNodo()`
- [ ] Crear `TramitesListaComponent` con tarjetas y progreso
- [ ] Crear `TramiteDetalleComponent` con historial y formulario de completar nodo
- [ ] Configurar rutas del módulo Funcionario
- [ ] Proteger módulo con `AuthGuard` + `RolGuard` → rol: `Funcionario`
- [ ] Iniciar un trámite desde Flutter y verificar que aparece en la lista web
- [ ] Completar cada nodo del flujo CRE siguiendo el orden de la tabla de demo

**Próximo:** G5W — Diagramador de Workflow para Administradores
