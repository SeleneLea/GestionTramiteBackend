# Guía 5W — Diagramador de Workflow (Admin)

**Ciclo 1 · Sistema de Gestión de Trámites — Frontend Web**

> **Objetivo:** Panel de administración para visualizar, crear y editar diagramas de workflow. Incluye visualización del flujo CRE ya sembrado en la BD y la opción de generar diagramas por IA con prompt de texto.

---

## 0. Prerequisitos

- G1W completada (login, guards)
- G3W completada (políticas y actividades ya en BD)
- Diagrama CRE ya existe en la BD (sembrado por DataSeeder): 10 nodos, 11 transiciones

---

## 1. Modelos

### src/app/core/models/diagrama.model.ts
```typescript
export interface DiagramaWorkflow {
  id: string;
  nombre: string;
  politicaId?: string;
  creadorId?: string;
  swimlanes: string[];
  versionActual: number;
  estado: string;        // "borrador" | "publicado" | "archivado"
  generadoPorIa: boolean;
  promptOriginal?: string;
  fechaCreacion?: string;
  ultimaModificacion?: string;
}

export interface DiagramaRequest {
  nombre: string;
  politicaId?: string;
  swimlanes?: string[];
  estado?: string;
}

export interface NodoDiagrama {
  id: string;
  diagramaId: string;
  tipo: string;          // "inicio" | "fin" | "actividad" | "decision" | "fork" | "join"
  nombre: string;
  actividadId?: string;
  departamentoId?: string;
  swimlane?: string;
  orden: number;
  posicion?: { x: number; y: number };
}

export interface NodoRequest {
  tipo: string;
  nombre: string;
  actividadId?: string;
  departamentoId?: string;
  swimlane?: string;
  orden?: number;
  posicion?: { x: number; y: number };
}

export interface FlujoTransicion {
  id: string;
  diagramaId: string;
  nodoOrigenId: string;
  nodoDestinoId: string;
  tipo: string;          // "secuencial" | "paralelo" | "condicional"
  condicion?: string;
  etiqueta?: string;
}

export interface TransicionRequest {
  nodoOrigenId: string;
  nodoDestinoId: string;
  tipo: string;
  condicion?: string;
  etiqueta?: string;
}

export interface PromptFlowRequest {
  prompt: string;
  politicaId?: string;
}

export interface PromptFlowResponse {
  diagrama: DiagramaWorkflow;
  nodos: NodoDiagrama[];
  transiciones: FlujoTransicion[];
  promptUsado: string;
}
```

---

## 2. Servicios

### src/app/core/services/diagrama.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DiagramaWorkflow, DiagramaRequest,
  NodoDiagrama, NodoRequest,
  FlujoTransicion, TransicionRequest,
  PromptFlowRequest, PromptFlowResponse
} from '../models/diagrama.model';

@Injectable({ providedIn: 'root' })
export class DiagramaService {
  private url = `${environment.apiUrl}`;

  constructor(private http: HttpClient) {}

  // ── Diagramas ──────────────────────────────────────
  listarDiagramas(): Observable<DiagramaWorkflow[]> {
    return this.http.get<DiagramaWorkflow[]>(`${this.url}/diagramas`);
  }

  obtenerDiagrama(id: string): Observable<DiagramaWorkflow> {
    return this.http.get<DiagramaWorkflow>(`${this.url}/diagramas/${id}`);
  }

  crearDiagrama(data: DiagramaRequest): Observable<DiagramaWorkflow> {
    return this.http.post<DiagramaWorkflow>(`${this.url}/diagramas`, data);
  }

  actualizarDiagrama(id: string, data: DiagramaRequest): Observable<DiagramaWorkflow> {
    return this.http.put<DiagramaWorkflow>(`${this.url}/diagramas/${id}`, data);
  }

  eliminarDiagrama(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/diagramas/${id}`);
  }

  // ── Nodos ──────────────────────────────────────────
  listarNodos(diagramaId: string): Observable<NodoDiagrama[]> {
    return this.http.get<NodoDiagrama[]>(`${this.url}/diagramas/${diagramaId}/nodos`);
  }

  crearNodo(diagramaId: string, data: NodoRequest): Observable<NodoDiagrama> {
    return this.http.post<NodoDiagrama>(`${this.url}/diagramas/${diagramaId}/nodos`, data);
  }

  actualizarNodo(nodoId: string, data: NodoRequest): Observable<NodoDiagrama> {
    return this.http.put<NodoDiagrama>(`${this.url}/nodos/${nodoId}`, data);
  }

  eliminarNodo(nodoId: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/nodos/${nodoId}`);
  }

  // ── Transiciones ───────────────────────────────────
  listarTransiciones(diagramaId: string): Observable<FlujoTransicion[]> {
    return this.http.get<FlujoTransicion[]>(`${this.url}/diagramas/${diagramaId}/transiciones`);
  }

  crearTransicion(diagramaId: string, data: TransicionRequest): Observable<FlujoTransicion> {
    return this.http.post<FlujoTransicion>(`${this.url}/diagramas/${diagramaId}/transiciones`, data);
  }

  eliminarTransicion(transicionId: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/transiciones/${transicionId}`);
  }

  // ── Generación por IA ──────────────────────────────
  generarConIA(data: PromptFlowRequest): Observable<PromptFlowResponse> {
    return this.http.post<PromptFlowResponse>(`${this.url}/workflow-design/prompt`, data);
  }
}
```

---

## 3. Componente: Lista de Diagramas

### diagramas-lista.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { DiagramaService } from '../../../core/services/diagrama.service';
import { PoliticaService } from '../../../core/services/politica.service';
import { DiagramaWorkflow } from '../../../core/models/diagrama.model';
import { Politica } from '../../../core/models/politica.model';

@Component({
  selector: 'app-diagramas-lista',
  templateUrl: './diagramas-lista.component.html'
})
export class DiagramasListaComponent implements OnInit {
  diagramas: DiagramaWorkflow[] = [];
  politicas: Politica[] = [];
  loading = false;
  error = '';
  exito = '';

  estadoColores: Record<string, string> = {
    borrador:  'bg-warning text-dark',
    publicado: 'bg-success',
    archivado: 'bg-secondary'
  };

  constructor(
    private diagramaSvc: DiagramaService,
    private politicaSvc: PoliticaService
  ) {}

  ngOnInit(): void {
    this.cargar();
    this.politicaSvc.listar().subscribe(p => this.politicas = p);
  }

  cargar(): void {
    this.loading = true;
    this.diagramaSvc.listarDiagramas().subscribe({
      next: d => { this.diagramas = d; this.loading = false; },
      error: () => { this.error = 'Error al cargar diagramas'; this.loading = false; }
    });
  }

  getNombrePolitica(politicaId?: string): string {
    if (!politicaId) return 'Sin política';
    return this.politicas.find(p => p.id === politicaId)?.nombre ?? politicaId;
  }

  eliminar(id: string, nombre: string): void {
    if (!confirm(`¿Eliminar diagrama "${nombre}"?`)) return;
    this.diagramaSvc.eliminarDiagrama(id).subscribe({
      next: () => {
        this.diagramas = this.diagramas.filter(d => d.id !== id);
        this.exito = 'Diagrama eliminado';
        setTimeout(() => this.exito = '', 3000);
      },
      error: err => this.error = err.error?.message ?? 'Error al eliminar'
    });
  }
}
```

### diagramas-lista.component.html
```html
<div class="container-fluid py-4">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h4 class="mb-0">Diagramas de Workflow</h4>
    <div class="d-flex gap-2">
      <a routerLink="/admin/diagramas/ia" class="btn btn-outline-info">✨ Generar con IA</a>
      <a routerLink="/admin/diagramas/nuevo" class="btn btn-primary">+ Nuevo Diagrama</a>
    </div>
  </div>

  <div *ngIf="exito" class="alert alert-success">{{ exito }}</div>
  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <div *ngIf="loading" class="text-center py-5">
    <div class="spinner-border text-primary"></div>
  </div>

  <div *ngIf="!loading" class="row g-3">
    <div *ngFor="let d of diagramas" class="col-md-6 col-lg-4">
      <div class="card h-100">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-semibold text-truncate">{{ d.nombre }}</span>
          <span class="badge ms-2" [ngClass]="estadoColores[d.estado] ?? 'bg-secondary'">
            {{ d.estado }}
          </span>
        </div>
        <div class="card-body small">
          <p class="text-muted mb-1">Política: <strong>{{ getNombrePolitica(d.politicaId) }}</strong></p>
          <p class="text-muted mb-1">Versión: v{{ d.versionActual }}</p>
          <p class="text-muted mb-1">
            Swimlanes:
            <span *ngFor="let s of d.swimlanes" class="badge bg-light text-dark me-1">{{ s }}</span>
          </p>
          <p class="mb-0" *ngIf="d.generadoPorIa">
            <span class="badge bg-info text-dark">✨ Generado por IA</span>
          </p>
        </div>
        <div class="card-footer d-flex gap-2">
          <a [routerLink]="['/admin/diagramas', d.id]" class="btn btn-sm btn-primary flex-fill">
            Ver / Editar
          </a>
          <button (click)="eliminar(d.id, d.nombre)" class="btn btn-sm btn-outline-danger">
            Eliminar
          </button>
        </div>
      </div>
    </div>

    <div *ngIf="diagramas.length === 0" class="col-12">
      <div class="text-center text-muted py-5">Sin diagramas registrados</div>
    </div>
  </div>
</div>
```

---

## 4. Componente: Visualizador + Editor de Diagrama

### diagrama-editor.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DiagramaService } from '../../../core/services/diagrama.service';
import { ActividadService } from '../../../core/services/actividad.service';
import { DepartamentoService } from '../../../core/services/departamento.service';
import { DiagramaWorkflow, NodoDiagrama, FlujoTransicion } from '../../../core/models/diagrama.model';
import { Actividad } from '../../../core/models/actividad.model';
import { Departamento } from '../../../core/models/departamento.model';

@Component({
  selector: 'app-diagrama-editor',
  templateUrl: './diagrama-editor.component.html'
})
export class DiagramaEditorComponent implements OnInit {
  diagrama!: DiagramaWorkflow;
  nodos: NodoDiagrama[] = [];
  transiciones: FlujoTransicion[] = [];
  actividades: Actividad[] = [];
  departamentos: Departamento[] = [];

  loading = false;
  error = '';
  exito = '';
  diagramaId = '';

  // Para agregar nodo
  tiposNodo = ['inicio', 'fin', 'actividad', 'decision', 'fork', 'join'];
  nuevoNodo = { tipo: 'actividad', nombre: '', actividadId: '', departamentoId: '', swimlane: '', orden: 0 };
  mostrarFormNodo = false;

  constructor(
    private route: ActivatedRoute,
    private diagramaSvc: DiagramaService,
    private actSvc: ActividadService,
    private deptoSvc: DepartamentoService
  ) {}

  ngOnInit(): void {
    this.diagramaId = this.route.snapshot.params['id'];
    this.cargar();
    this.actSvc.listar().subscribe(a => this.actividades = a);
    this.deptoSvc.listar().subscribe(d => this.departamentos = d);
  }

  cargar(): void {
    this.loading = true;
    this.diagramaSvc.obtenerDiagrama(this.diagramaId).subscribe(d => this.diagrama = d);
    this.diagramaSvc.listarNodos(this.diagramaId).subscribe(n => {
      this.nodos = n.sort((a, b) => a.orden - b.orden);
    });
    this.diagramaSvc.listarTransiciones(this.diagramaId).subscribe(t => {
      this.transiciones = t;
      this.loading = false;
    });
  }

  getNombreNodo(id: string): string {
    return this.nodos.find(n => n.id === id)?.nombre ?? id;
  }

  getNombreActividad(id?: string): string {
    if (!id) return '—';
    return this.actividades.find(a => a.id === id)?.nombre ?? id;
  }

  getNombreDepto(id?: string): string {
    if (!id) return '—';
    return this.departamentos.find(d => d.id === id)?.codigo ?? id;
  }

  tipoIcono(tipo: string): string {
    const iconos: Record<string, string> = {
      inicio: '▶', fin: '⬛', actividad: '📋', decision: '◆', fork: '⟨', join: '⟩'
    };
    return iconos[tipo] ?? '•';
  }

  tipoBadge(tipo: string): string {
    const badges: Record<string, string> = {
      inicio: 'bg-success', fin: 'bg-dark', actividad: 'bg-primary',
      decision: 'bg-warning text-dark', fork: 'bg-info text-dark', join: 'bg-info text-dark'
    };
    return badges[tipo] ?? 'bg-secondary';
  }

  agregarNodo(): void {
    const req: any = { ...this.nuevoNodo };
    if (!req.actividadId) delete req.actividadId;
    if (!req.departamentoId) delete req.departamentoId;
    if (!req.swimlane) delete req.swimlane;

    this.diagramaSvc.crearNodo(this.diagramaId, req).subscribe({
      next: () => {
        this.exito = 'Nodo agregado';
        this.mostrarFormNodo = false;
        this.nuevoNodo = { tipo: 'actividad', nombre: '', actividadId: '', departamentoId: '', swimlane: '', orden: 0 };
        this.diagramaSvc.listarNodos(this.diagramaId).subscribe(n => this.nodos = n.sort((a,b) => a.orden - b.orden));
        setTimeout(() => this.exito = '', 3000);
      },
      error: err => this.error = err.error?.message ?? 'Error al agregar nodo'
    });
  }

  eliminarNodo(id: string): void {
    if (!confirm('¿Eliminar nodo? También se eliminarán sus transiciones.')) return;
    this.diagramaSvc.eliminarNodo(id).subscribe({
      next: () => {
        this.nodos = this.nodos.filter(n => n.id !== id);
        this.transiciones = this.transiciones.filter(t => t.nodoOrigenId !== id && t.nodoDestinoId !== id);
      }
    });
  }

  eliminarTransicion(id: string): void {
    this.diagramaSvc.eliminarTransicion(id).subscribe({
      next: () => this.transiciones = this.transiciones.filter(t => t.id !== id)
    });
  }
}
```

### diagrama-editor.component.html
```html
<div class="container-fluid py-4" *ngIf="diagrama">
  <div class="d-flex align-items-center gap-3 mb-4">
    <a routerLink="/admin/diagramas" class="btn btn-sm btn-outline-secondary">← Volver</a>
    <div>
      <h4 class="mb-0">{{ diagrama.nombre }}</h4>
      <small class="text-muted">v{{ diagrama.versionActual }} ·
        <span class="badge" [ngClass]="diagrama.estado === 'publicado' ? 'bg-success' : 'bg-warning text-dark'">
          {{ diagrama.estado }}
        </span>
      </small>
    </div>
  </div>

  <div *ngIf="exito" class="alert alert-success">{{ exito }}</div>
  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
  <div *ngIf="loading" class="text-center py-5"><div class="spinner-border text-primary"></div></div>

  <div *ngIf="!loading" class="row g-4">

    <!-- Visualización del flujo (tabla ordenada) -->
    <div class="col-lg-7">
      <div class="card">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-semibold">Nodos del Diagrama</span>
          <button class="btn btn-sm btn-outline-primary" (click)="mostrarFormNodo = !mostrarFormNodo">
            {{ mostrarFormNodo ? '✕ Cancelar' : '+ Agregar Nodo' }}
          </button>
        </div>

        <!-- Formulario nuevo nodo -->
        <div *ngIf="mostrarFormNodo" class="card-body border-bottom bg-light">
          <div class="row g-2">
            <div class="col-4">
              <select class="form-select form-select-sm" [(ngModel)]="nuevoNodo.tipo">
                <option *ngFor="let t of tiposNodo" [value]="t">{{ t }}</option>
              </select>
            </div>
            <div class="col-8">
              <input class="form-control form-control-sm" [(ngModel)]="nuevoNodo.nombre" placeholder="Nombre del nodo">
            </div>
            <div class="col-6" *ngIf="nuevoNodo.tipo === 'actividad'">
              <select class="form-select form-select-sm" [(ngModel)]="nuevoNodo.actividadId">
                <option value="">-- Actividad --</option>
                <option *ngFor="let a of actividades" [value]="a.id">{{ a.nombre }}</option>
              </select>
            </div>
            <div class="col-4">
              <select class="form-select form-select-sm" [(ngModel)]="nuevoNodo.departamentoId">
                <option value="">-- Depto --</option>
                <option *ngFor="let d of departamentos" [value]="d.id">{{ d.codigo }}</option>
              </select>
            </div>
            <div class="col-2">
              <input type="number" class="form-control form-control-sm" [(ngModel)]="nuevoNodo.orden" placeholder="Orden">
            </div>
            <div class="col-12">
              <button class="btn btn-primary btn-sm" (click)="agregarNodo()">Agregar</button>
            </div>
          </div>
        </div>

        <!-- Lista de nodos -->
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead class="table-light">
              <tr>
                <th>#</th><th>Tipo</th><th>Nombre</th><th>Actividad</th><th>Depto</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let n of nodos">
                <td class="text-muted">{{ n.orden }}</td>
                <td>
                  <span class="badge" [ngClass]="tipoBadge(n.tipo)">
                    {{ tipoIcono(n.tipo) }} {{ n.tipo }}
                  </span>
                </td>
                <td>{{ n.nombre }}</td>
                <td class="small">{{ getNombreActividad(n.actividadId) }}</td>
                <td><span *ngIf="n.departamentoId" class="badge bg-secondary">{{ getNombreDepto(n.departamentoId) }}</span></td>
                <td>
                  <button (click)="eliminarNodo(n.id)" class="btn btn-xs btn-outline-danger" style="padding: 1px 6px">✕</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Transiciones -->
    <div class="col-lg-5">
      <div class="card">
        <div class="card-header fw-semibold">Transiciones ({{ transiciones.length }})</div>
        <ul class="list-group list-group-flush">
          <li *ngFor="let t of transiciones" class="list-group-item py-2 small">
            <div class="d-flex justify-content-between align-items-start">
              <div>
                <span class="fw-semibold">{{ getNombreNodo(t.nodoOrigenId) }}</span>
                <span class="text-muted mx-1">→</span>
                <span class="fw-semibold">{{ getNombreNodo(t.nodoDestinoId) }}</span><br>
                <span class="badge me-1" [ngClass]="{
                  'bg-info text-dark': t.tipo === 'paralelo',
                  'bg-warning text-dark': t.tipo === 'condicional',
                  'bg-secondary': t.tipo === 'secuencial'
                }">{{ t.tipo }}</span>
                <span *ngIf="t.etiqueta" class="text-muted">{{ t.etiqueta }}</span>
                <span *ngIf="t.condicion" class="text-muted"> [{{ t.condicion }}]</span>
              </div>
              <button (click)="eliminarTransicion(t.id)" class="btn btn-xs btn-outline-danger" style="padding:1px 6px">✕</button>
            </div>
          </li>
          <li *ngIf="transiciones.length === 0" class="list-group-item text-muted text-center py-3">
            Sin transiciones
          </li>
        </ul>
      </div>
    </div>

  </div>
</div>
```

---

## 5. Componente: Generar Diagrama con IA

### diagrama-ia.component.ts
```typescript
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { DiagramaService } from '../../../core/services/diagrama.service';
import { PoliticaService } from '../../../core/services/politica.service';
import { Politica } from '../../../core/models/politica.model';
import { PromptFlowResponse } from '../../../core/models/diagrama.model';

@Component({
  selector: 'app-diagrama-ia',
  templateUrl: './diagrama-ia.component.html'
})
export class DiagramaIaComponent {
  politicas: Politica[] = [];
  prompt = '';
  politicaId = '';
  loading = false;
  error = '';
  resultado?: PromptFlowResponse;

  promptEjemplo = 'Flujo de solicitud de reconexión eléctrica: el cliente solicita en ATC, TEC hace inspección técnica en paralelo con revisión de deuda, LEG aprueba el acuerdo de pago, OPE ejecuta la reconexión.';

  constructor(
    private diagramaSvc: DiagramaService,
    private politicaSvc: PoliticaService,
    private router: Router
  ) {
    this.politicaSvc.listar().subscribe(p => this.politicas = p);
  }

  generar(): void {
    if (!this.prompt.trim()) return;
    this.loading = true;
    this.error = '';
    this.resultado = undefined;

    this.diagramaSvc.generarConIA({ prompt: this.prompt, politicaId: this.politicaId || undefined }).subscribe({
      next: res => { this.resultado = res; this.loading = false; },
      error: err => { this.error = err.error?.message ?? 'Error al generar'; this.loading = false; }
    });
  }

  verDiagrama(): void {
    if (this.resultado?.diagrama?.id) {
      this.router.navigate(['/admin/diagramas', this.resultado.diagrama.id]);
    }
  }
}
```

### diagrama-ia.component.html
```html
<div class="container py-4" style="max-width: 800px">
  <div class="d-flex align-items-center gap-3 mb-4">
    <a routerLink="/admin/diagramas" class="btn btn-sm btn-outline-secondary">← Volver</a>
    <h4 class="mb-0">✨ Generar Diagrama con IA</h4>
  </div>

  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <div class="card">
    <div class="card-body">
      <p class="text-muted mb-3">
        Describe en lenguaje natural el proceso que quieres modelar.
        El sistema detectará los departamentos, actividades y nodos automáticamente.
      </p>

      <div class="mb-3">
        <label class="form-label">Política asociada (opcional)</label>
        <select class="form-select" [(ngModel)]="politicaId">
          <option value="">-- Sin política --</option>
          <option *ngFor="let p of politicas" [value]="p.id">{{ p.nombre }}</option>
        </select>
      </div>

      <div class="mb-3">
        <label class="form-label">Descripción del flujo *</label>
        <textarea class="form-control" [(ngModel)]="prompt" rows="5"
                  placeholder="Describe el proceso paso a paso..."></textarea>
        <div class="form-text">
          <a href="javascript:void(0)" (click)="prompt = promptEjemplo">Usar ejemplo</a>
        </div>
      </div>

      <button class="btn btn-info" (click)="generar()" [disabled]="loading || !prompt.trim()">
        <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
        {{ loading ? 'Generando...' : '✨ Generar Diagrama' }}
      </button>
    </div>
  </div>

  <!-- Resultado -->
  <div *ngIf="resultado" class="card mt-4 border-success">
    <div class="card-header bg-success text-white fw-semibold">
      Diagrama generado exitosamente
    </div>
    <div class="card-body">
      <p><strong>Nombre:</strong> {{ resultado.diagrama.nombre }}</p>
      <p><strong>Nodos creados:</strong> {{ resultado.nodos.length }}</p>
      <p><strong>Transiciones:</strong> {{ resultado.transiciones.length }}</p>

      <div class="mt-2">
        <p class="fw-semibold mb-1">Nodos:</p>
        <div class="d-flex flex-wrap gap-1">
          <span *ngFor="let n of resultado.nodos" class="badge bg-primary">{{ n.nombre }}</span>
        </div>
      </div>

      <button class="btn btn-success mt-3" (click)="verDiagrama()">
        Ver y editar diagrama →
      </button>
    </div>
  </div>
</div>
```

---

## 6. Endpoints utilizados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/diagramas` | Listar diagramas |
| GET | `/api/diagramas/{id}` | Obtener diagrama |
| POST | `/api/diagramas` | Crear diagrama |
| PUT | `/api/diagramas/{id}` | Actualizar diagrama |
| DELETE | `/api/diagramas/{id}` | Eliminar diagrama |
| GET | `/api/diagramas/{id}/nodos` | Listar nodos |
| POST | `/api/diagramas/{id}/nodos` | Agregar nodo |
| PUT | `/api/nodos/{id}` | Actualizar nodo |
| DELETE | `/api/nodos/{id}` | Eliminar nodo |
| GET | `/api/diagramas/{id}/transiciones` | Listar transiciones |
| POST | `/api/diagramas/{id}/transiciones` | Agregar transición |
| DELETE | `/api/transiciones/{id}` | Eliminar transición |
| POST | `/api/workflow-design/prompt` | Generar diagrama con IA |

---

## Diagrama CRE ya en la BD

Al abrir `/admin/diagramas` verás el diagrama "Flujo - Nueva Conexion Residencial" con:

| Orden | Tipo | Nombre | Depto |
|-------|------|--------|-------|
| 1 | inicio | Inicio | — |
| 2 | actividad | Verificar Documentos | ATC |
| 3 | fork | Fork | — |
| 4 | actividad | Inspeccion en Campo | TEC |
| 5 | actividad | Elaborar Presupuesto | TEC |
| 6 | join | Join | — |
| 7 | actividad | Revisar Contrato | LEG |
| 8 | decision | Contrato aprobado? | — |
| 9 | actividad | Cierre y Conexion | OPE |
| 10 | fin | Fin | — |

---

## Checklist G5W

- [ ] Crear modelo `diagrama.model.ts` con interfaces completas
- [ ] Implementar `DiagramaService` (diagramas, nodos, transiciones, IA)
- [ ] Crear `DiagramasListaComponent` con tarjetas por diagrama
- [ ] Crear `DiagramaEditorComponent` con tabla de nodos y transiciones
- [ ] Crear `DiagramaIaComponent` con formulario de prompt
- [ ] Verificar que el diagrama CRE aparece al entrar en `/admin/diagramas`
- [ ] Abrir el diagrama y confirmar los 10 nodos y 11 transiciones
- [ ] Probar generación por IA con un prompt de ejemplo
- [ ] Proteger rutas con roles `['Administrador', 'SuperUser']`

---

## Resumen de Guías Web C1

| Guía | Contenido | Rol |
|------|-----------|-----|
| G1W | Login JWT, Guards, Navbar | Admin + Funcionario |
| G2W | CRUD Usuarios, Departamentos, Roles | Admin |
| G3W | CRUD Políticas, Actividades | Admin |
| G4W | Ver y completar nodos del workflow | Funcionario |
| G5W | Diagramas, nodos, transiciones, IA | Admin |
