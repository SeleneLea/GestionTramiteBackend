# Guía 3W — Gestión de Políticas y Actividades (Admin)

**Ciclo 1 · Sistema de Gestión de Trámites — Frontend Web**

> **Objetivo:** Panel de administración para ver y gestionar políticas de negocio y actividades. Las políticas definen los trámites disponibles; las actividades son las tareas dentro de cada flujo.

---

## 0. Prerequisitos

- G1W y G2W completadas
- Política "Nueva conexión residencial" ya existe en la BD (sembrada por DataSeeder)
- 6 actividades ya existen en la BD

---

## 1. Modelos

### src/app/core/models/politica.model.ts
```typescript
export interface Politica {
  id: string;
  nombre: string;
  descripcion: string;
  categoria: string;
  estado: string;        // "activa" | "borrador" | "archivada"
  versionActual: number;
  diagramaId?: string;
  creadorId?: string;
  fechaCreacion?: string;
  fechaActivacion?: string;
  parametros?: Record<string, any>;
}

export interface PoliticaRequest {
  nombre: string;
  descripcion: string;
  categoria: string;
  estado: string;
}

export interface PoliticaEstadoRequest {
  estado: string;
}
```

### src/app/core/models/actividad.model.ts
```typescript
export interface Actividad {
  id: string;
  nombre: string;
  descripcion: string;
  departamentoId: string;
  slaHoras: number;
  tipoSalida: string;    // "derivar" | "completar" | "aprobar"
  reutilizable: boolean;
  fechaCreacion?: string;
}

export interface ActividadRequest {
  nombre: string;
  descripcion: string;
  departamentoId: string;
  slaHoras: number;
  tipoSalida: string;
  reutilizable: boolean;
}
```

---

## 2. Servicios

### src/app/core/services/politica.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Politica, PoliticaRequest, PoliticaEstadoRequest } from '../models/politica.model';

@Injectable({ providedIn: 'root' })
export class PoliticaService {
  private url = `${environment.apiUrl}/politicas`;

  constructor(private http: HttpClient) {}

  listar(soloActivas = false): Observable<Politica[]> {
    const params = soloActivas ? new HttpParams().set('soloActivas', 'true') : new HttpParams();
    return this.http.get<Politica[]>(this.url, { params });
  }

  buscarPorId(id: string): Observable<Politica> {
    return this.http.get<Politica>(`${this.url}/${id}`);
  }

  crear(data: PoliticaRequest): Observable<Politica> {
    return this.http.post<Politica>(this.url, data);
  }

  actualizar(id: string, data: PoliticaRequest): Observable<Politica> {
    return this.http.put<Politica>(`${this.url}/${id}`, data);
  }

  cambiarEstado(id: string, estado: string): Observable<Politica> {
    const body: PoliticaEstadoRequest = { estado };
    return this.http.patch<Politica>(`${this.url}/${id}/estado`, body);
  }

  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
```

### src/app/core/services/actividad.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Actividad, ActividadRequest } from '../models/actividad.model';

@Injectable({ providedIn: 'root' })
export class ActividadService {
  private url = `${environment.apiUrl}/actividades`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(this.url);
  }

  listarPorDepartamento(deptoId: string): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(`${this.url}?departamentoId=${deptoId}`);
  }

  buscarPorId(id: string): Observable<Actividad> {
    return this.http.get<Actividad>(`${this.url}/${id}`);
  }

  crear(data: ActividadRequest): Observable<Actividad> {
    return this.http.post<Actividad>(this.url, data);
  }

  actualizar(id: string, data: ActividadRequest): Observable<Actividad> {
    return this.http.put<Actividad>(`${this.url}/${id}`, data);
  }

  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
```

---

## 3. Componente: Lista de Políticas

### politicas-lista.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { PoliticaService } from '../../../core/services/politica.service';
import { Politica } from '../../../core/models/politica.model';

@Component({
  selector: 'app-politicas-lista',
  templateUrl: './politicas-lista.component.html'
})
export class PoliticasListaComponent implements OnInit {
  politicas: Politica[] = [];
  loading = false;
  error = '';
  exito = '';

  estadoColores: Record<string, string> = {
    activa:    'bg-success',
    borrador:  'bg-warning text-dark',
    archivada: 'bg-secondary'
  };

  constructor(private politicaSvc: PoliticaService) {}

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.loading = true;
    this.politicaSvc.listar().subscribe({
      next: p => { this.politicas = p; this.loading = false; },
      error: () => { this.error = 'Error al cargar políticas'; this.loading = false; }
    });
  }

  cambiarEstado(politica: Politica, nuevoEstado: string): void {
    this.politicaSvc.cambiarEstado(politica.id, nuevoEstado).subscribe({
      next: p => {
        const idx = this.politicas.findIndex(x => x.id === p.id);
        if (idx >= 0) this.politicas[idx] = p;
        this.exito = `Política "${p.nombre}" → ${p.estado}`;
        setTimeout(() => this.exito = '', 3000);
      },
      error: err => this.error = err.error?.message ?? 'Error al cambiar estado'
    });
  }

  eliminar(id: string, nombre: string): void {
    if (!confirm(`¿Eliminar política "${nombre}"?`)) return;
    this.politicaSvc.eliminar(id).subscribe({
      next: () => {
        this.politicas = this.politicas.filter(p => p.id !== id);
        this.exito = 'Política eliminada';
        setTimeout(() => this.exito = '', 3000);
      },
      error: err => this.error = err.error?.message ?? 'Error al eliminar'
    });
  }

  getAccionesEstado(estadoActual: string): string[] {
    const transiciones: Record<string, string[]> = {
      borrador:  ['activa', 'archivada'],
      activa:    ['archivada'],
      archivada: ['borrador']
    };
    return transiciones[estadoActual] ?? [];
  }
}
```

### politicas-lista.component.html
```html
<div class="container-fluid py-4">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h4 class="mb-0">Políticas de Negocio</h4>
    <a class="btn btn-primary" routerLink="/admin/politicas/nueva">+ Nueva Política</a>
  </div>

  <div *ngIf="exito" class="alert alert-success alert-dismissible">{{ exito }}</div>
  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <div *ngIf="loading" class="text-center py-5">
    <div class="spinner-border text-primary"></div>
  </div>

  <div *ngIf="!loading" class="row g-3">
    <div *ngFor="let p of politicas" class="col-md-6 col-lg-4">
      <div class="card h-100">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-semibold">{{ p.nombre }}</span>
          <span class="badge" [ngClass]="estadoColores[p.estado] ?? 'bg-secondary'">
            {{ p.estado.toUpperCase() }}
          </span>
        </div>
        <div class="card-body">
          <p class="text-muted small mb-2">{{ p.descripcion }}</p>
          <div class="small">
            <span class="text-muted">Categoría:</span> {{ p.categoria }}<br>
            <span class="text-muted">Versión:</span> v{{ p.versionActual }}<br>
            <span class="text-muted">Diagrama:</span>
            <span *ngIf="p.diagramaId" class="text-success">Vinculado</span>
            <span *ngIf="!p.diagramaId" class="text-warning">Sin diagrama</span>
          </div>
        </div>
        <div class="card-footer d-flex flex-wrap gap-1">
          <a [routerLink]="['/admin/politicas', p.id, 'editar']"
             class="btn btn-sm btn-outline-secondary">Editar</a>

          <button *ngFor="let estado of getAccionesEstado(p.estado)"
                  (click)="cambiarEstado(p, estado)"
                  class="btn btn-sm btn-outline-primary">
            → {{ estado }}
          </button>

          <a [routerLink]="['/admin/diagramas']" [queryParams]="{ politicaId: p.id }"
             class="btn btn-sm btn-outline-info">Ver Diagrama</a>

          <button (click)="eliminar(p.id, p.nombre)"
                  class="btn btn-sm btn-outline-danger">Eliminar</button>
        </div>
      </div>
    </div>

    <div *ngIf="politicas.length === 0" class="col-12">
      <div class="text-center text-muted py-5">Sin políticas registradas</div>
    </div>
  </div>
</div>
```

---

## 4. Componente: Formulario Política

### politica-form.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PoliticaService } from '../../../core/services/politica.service';

@Component({
  selector: 'app-politica-form',
  templateUrl: './politica-form.component.html'
})
export class PoliticaFormComponent implements OnInit {
  form!: FormGroup;
  loading = false;
  error = '';
  esEdicion = false;
  politicaId = '';

  categorias = ['conexiones', 'reconexiones', 'mantenimiento', 'reclamos', 'otros'];

  constructor(
    private fb: FormBuilder,
    private politicaSvc: PoliticaService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      nombre:      ['', Validators.required],
      descripcion: ['', Validators.required],
      categoria:   ['conexiones', Validators.required],
      estado:      ['borrador', Validators.required]
    });

    this.politicaId = this.route.snapshot.params['id'];
    if (this.politicaId) {
      this.esEdicion = true;
      this.politicaSvc.buscarPorId(this.politicaId).subscribe(p => this.form.patchValue(p));
    }
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    const obs = this.esEdicion
      ? this.politicaSvc.actualizar(this.politicaId, this.form.value)
      : this.politicaSvc.crear(this.form.value);

    obs.subscribe({
      next: () => this.router.navigate(['/admin/politicas']),
      error: err => { this.error = err.error?.message ?? 'Error al guardar'; this.loading = false; }
    });
  }
}
```

### politica-form.component.html
```html
<div class="container py-4" style="max-width: 600px">
  <h4 class="mb-4">{{ esEdicion ? 'Editar' : 'Nueva' }} Política</h4>

  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <form [formGroup]="form" (ngSubmit)="submit()">
    <div class="mb-3">
      <label class="form-label">Nombre *</label>
      <input class="form-control" formControlName="nombre" placeholder="ej: Nueva conexión residencial">
    </div>
    <div class="mb-3">
      <label class="form-label">Descripción *</label>
      <textarea class="form-control" formControlName="descripcion" rows="3"></textarea>
    </div>
    <div class="row g-3 mb-3">
      <div class="col-6">
        <label class="form-label">Categoría *</label>
        <select class="form-select" formControlName="categoria">
          <option *ngFor="let c of categorias" [value]="c">{{ c }}</option>
        </select>
      </div>
      <div class="col-6">
        <label class="form-label">Estado *</label>
        <select class="form-select" formControlName="estado">
          <option value="borrador">Borrador</option>
          <option value="activa">Activa</option>
        </select>
      </div>
    </div>

    <div class="d-flex gap-2">
      <button type="submit" class="btn btn-primary" [disabled]="loading || form.invalid">
        <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
        {{ esEdicion ? 'Actualizar' : 'Crear' }}
      </button>
      <a routerLink="/admin/politicas" class="btn btn-secondary">Cancelar</a>
    </div>
  </form>
</div>
```

---

## 5. Componente: Actividades (con SLA)

### actividades.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActividadService } from '../../../core/services/actividad.service';
import { DepartamentoService } from '../../../core/services/departamento.service';
import { Actividad } from '../../../core/models/actividad.model';
import { Departamento } from '../../../core/models/departamento.model';

@Component({
  selector: 'app-actividades',
  templateUrl: './actividades.component.html'
})
export class ActividadesComponent implements OnInit {
  actividades: Actividad[] = [];
  departamentos: Departamento[] = [];
  form!: FormGroup;
  modoEdicion = false;
  editandoId = '';
  loading = false;
  error = '';
  exito = '';

  tiposSalida = ['derivar', 'completar', 'aprobar'];

  constructor(
    private fb: FormBuilder,
    private actSvc: ActividadService,
    private deptoSvc: DepartamentoService
  ) {}

  ngOnInit(): void {
    this.iniciarForm();
    this.actSvc.listar().subscribe(a => this.actividades = a);
    this.deptoSvc.listar().subscribe(d => this.departamentos = d);
  }

  iniciarForm(): void {
    this.form = this.fb.group({
      nombre:        ['', Validators.required],
      descripcion:   ['', Validators.required],
      departamentoId:['', Validators.required],
      slaHoras:      [8, [Validators.required, Validators.min(1)]],
      tipoSalida:    ['completar', Validators.required],
      reutilizable:  [true]
    });
  }

  getNombreDepto(id: string): string {
    return this.departamentos.find(d => d.id === id)?.codigo ?? id;
  }

  editar(a: Actividad): void {
    this.modoEdicion = true;
    this.editandoId = a.id;
    this.form.patchValue(a);
  }

  cancelar(): void {
    this.modoEdicion = false;
    this.editandoId = '';
    this.form.reset({ slaHoras: 8, tipoSalida: 'completar', reutilizable: true });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    const obs = this.modoEdicion
      ? this.actSvc.actualizar(this.editandoId, this.form.value)
      : this.actSvc.crear(this.form.value);

    obs.subscribe({
      next: () => {
        this.exito = `Actividad ${this.modoEdicion ? 'actualizada' : 'creada'}`;
        this.cancelar();
        this.actSvc.listar().subscribe(a => this.actividades = a);
        this.loading = false;
        setTimeout(() => this.exito = '', 3000);
      },
      error: err => { this.error = err.error?.message ?? 'Error al guardar'; this.loading = false; }
    });
  }

  eliminar(id: string): void {
    if (!confirm('¿Eliminar actividad?')) return;
    this.actSvc.eliminar(id).subscribe({
      next: () => this.actividades = this.actividades.filter(a => a.id !== id)
    });
  }
}
```

### actividades.component.html
```html
<div class="container-fluid py-4">
  <h4 class="mb-4">Actividades del Sistema</h4>

  <div *ngIf="exito" class="alert alert-success">{{ exito }}</div>
  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <div class="row g-4">
    <!-- Formulario -->
    <div class="col-md-4">
      <div class="card">
        <div class="card-header">{{ modoEdicion ? 'Editar' : 'Nueva' }} Actividad</div>
        <div class="card-body">
          <form [formGroup]="form" (ngSubmit)="submit()">
            <div class="mb-2">
              <label class="form-label small">Nombre *</label>
              <input class="form-control form-control-sm" formControlName="nombre">
            </div>
            <div class="mb-2">
              <label class="form-label small">Descripción *</label>
              <textarea class="form-control form-control-sm" formControlName="descripcion" rows="2"></textarea>
            </div>
            <div class="mb-2">
              <label class="form-label small">Departamento *</label>
              <select class="form-select form-select-sm" formControlName="departamentoId">
                <option value="">-- Seleccionar --</option>
                <option *ngFor="let d of departamentos" [value]="d.id">
                  {{ d.codigo }} — {{ d.nombre }}
                </option>
              </select>
            </div>
            <div class="row g-2 mb-2">
              <div class="col-6">
                <label class="form-label small">SLA (horas) *</label>
                <input type="number" class="form-control form-control-sm" formControlName="slaHoras" min="1">
              </div>
              <div class="col-6">
                <label class="form-label small">Tipo salida *</label>
                <select class="form-select form-select-sm" formControlName="tipoSalida">
                  <option *ngFor="let t of tiposSalida" [value]="t">{{ t }}</option>
                </select>
              </div>
            </div>
            <div class="mb-3 form-check">
              <input type="checkbox" class="form-check-input" formControlName="reutilizable" id="reutilizable">
              <label class="form-check-label small" for="reutilizable">Reutilizable</label>
            </div>
            <div class="d-flex gap-2">
              <button type="submit" class="btn btn-primary btn-sm" [disabled]="loading || form.invalid">
                {{ modoEdicion ? 'Actualizar' : 'Crear' }}
              </button>
              <button *ngIf="modoEdicion" type="button" class="btn btn-secondary btn-sm" (click)="cancelar()">
                Cancelar
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Lista -->
    <div class="col-md-8">
      <div class="card">
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead class="table-light">
              <tr>
                <th>Nombre</th>
                <th>Dept.</th>
                <th>SLA</th>
                <th>Tipo Salida</th>
                <th>Reutilizable</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let a of actividades">
                <td>{{ a.nombre }}</td>
                <td><span class="badge bg-secondary">{{ getNombreDepto(a.departamentoId) }}</span></td>
                <td>{{ a.slaHoras }}h</td>
                <td><span class="badge bg-info text-dark">{{ a.tipoSalida }}</span></td>
                <td>
                  <span *ngIf="a.reutilizable" class="text-success">✓</span>
                  <span *ngIf="!a.reutilizable" class="text-muted">—</span>
                </td>
                <td>
                  <div class="btn-group btn-group-sm">
                    <button (click)="editar(a)" class="btn btn-outline-secondary">Editar</button>
                    <button (click)="eliminar(a.id)" class="btn btn-outline-danger">Eliminar</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</div>
```

---

## 6. Endpoints utilizados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/politicas` | Listar políticas |
| GET | `/api/politicas?soloActivas=true` | Solo políticas activas |
| POST | `/api/politicas` | Crear política |
| PUT | `/api/politicas/{id}` | Actualizar política |
| PATCH | `/api/politicas/{id}/estado` | Cambiar estado |
| DELETE | `/api/politicas/{id}` | Eliminar política |
| GET | `/api/actividades` | Listar actividades |
| POST | `/api/actividades` | Crear actividad |
| PUT | `/api/actividades/{id}` | Actualizar actividad |
| DELETE | `/api/actividades/{id}` | Eliminar actividad |

---

## Datos ya en la BD (DataSeeder)

| Política | Estado | Categoría |
|----------|--------|-----------|
| Nueva conexión residencial | activa | conexiones |

| Actividad | Depto | SLA | Tipo Salida |
|-----------|-------|-----|-------------|
| Verificar documentos del cliente | ATC | 8h | derivar |
| Inspeccion en campo | TEC | 16h | completar |
| Elaborar presupuesto | TEC | 8h | completar |
| Revisar y aprobar contrato | LEG | 24h | aprobar |
| Cierre y conexion electrica | OPE | 4h | completar |
| Notificar resolucion al cliente | ATC | 2h | completar |

---

## Checklist G3W

- [ ] Crear modelos: `politica.model.ts`, `actividad.model.ts`
- [ ] Implementar `PoliticaService` con `cambiarEstado()`
- [ ] Implementar `ActividadService`
- [ ] Crear `PoliticasListaComponent` con tarjetas y cambio de estado
- [ ] Crear `PoliticaFormComponent` para crear/editar
- [ ] Crear `ActividadesComponent` con lista + formulario inline
- [ ] Verificar que la política "Nueva conexión residencial" aparece como `activa`
- [ ] Verificar que las 6 actividades aparecen con sus SLAs

**Próximo:** G4W — Motor de Workflow para Funcionarios
