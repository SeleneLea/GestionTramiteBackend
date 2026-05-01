# Guía 2W — Gestión de Usuarios y Departamentos (Admin)

**Ciclo 1 · Sistema de Gestión de Trámites — Frontend Web**

> **Objetivo:** Panel de administración para CRUD de usuarios, departamentos y roles. Solo accesible con rol `Administrador` o `SuperUser`.

---

## 0. Prerequisitos

- G1W completada (login, interceptor, guards)
- Backend corriendo: usuarios en `/api/usuarios`, departamentos en `/api/departamentos`, roles en `/api/roles`

---

## 1. Modelos

### src/app/core/models/usuario.model.ts (extender)
```typescript
export interface Usuario {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  tipo: string;         // "administrador" | "funcionario" | "cliente"
  rolId: string;
  activo: boolean;
  departamentosIds?: string[];
  fechaRegistro?: string;
  ultimoAcceso?: string;
}

export interface UsuarioCreateRequest {
  nombre: string;
  apellido: string;
  email: string;
  password: string;
  tipo: string;
  rolId: string;
  departamentosIds?: string[];
}

export interface UsuarioUpdateRequest {
  nombre?: string;
  apellido?: string;
  tipo?: string;
  rolId?: string;
  activo?: boolean;
  departamentosIds?: string[];
}
```

### src/app/core/models/departamento.model.ts
```typescript
export interface Departamento {
  id: string;
  codigo: string;
  nombre: string;
  descripcion: string;
  jefeId?: string;
  activo: boolean;
  fechaCreacion?: string;
}

export interface DepartamentoRequest {
  codigo: string;
  nombre: string;
  descripcion: string;
  jefeId?: string;
}
```

### src/app/core/models/rol.model.ts
```typescript
export interface Rol {
  id: string;
  nombre: string;
  descripcion: string;
  permisos: string[];
  esSistema: boolean;
}
```

---

## 2. Servicios

### src/app/core/services/usuario.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Usuario, UsuarioCreateRequest, UsuarioUpdateRequest } from '../models/usuario.model';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private url = `${environment.apiUrl}/usuarios`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.url);
  }

  buscarPorId(id: string): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.url}/${id}`);
  }

  crear(data: UsuarioCreateRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.url, data);
  }

  actualizar(id: string, data: UsuarioUpdateRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.url}/${id}`, data);
  }

  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  toggleActivo(id: string, activo: boolean): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.url}/${id}`, { activo });
  }
}
```

### src/app/core/services/departamento.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Departamento, DepartamentoRequest } from '../models/departamento.model';

@Injectable({ providedIn: 'root' })
export class DepartamentoService {
  private url = `${environment.apiUrl}/departamentos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Departamento[]> {
    return this.http.get<Departamento[]>(this.url);
  }

  buscarPorId(id: string): Observable<Departamento> {
    return this.http.get<Departamento>(`${this.url}/${id}`);
  }

  crear(data: DepartamentoRequest): Observable<Departamento> {
    return this.http.post<Departamento>(this.url, data);
  }

  actualizar(id: string, data: DepartamentoRequest): Observable<Departamento> {
    return this.http.put<Departamento>(`${this.url}/${id}`, data);
  }

  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
```

### src/app/core/services/rol.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Rol } from '../models/rol.model';

@Injectable({ providedIn: 'root' })
export class RolService {
  private url = `${environment.apiUrl}/roles`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Rol[]> {
    return this.http.get<Rol[]>(this.url);
  }
}
```

---

## 3. Componente: Lista de Usuarios

### usuarios-lista.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { UsuarioService } from '../../../core/services/usuario.service';
import { RolService } from '../../../core/services/rol.service';
import { Usuario } from '../../../core/models/usuario.model';
import { Rol } from '../../../core/models/rol.model';

@Component({
  selector: 'app-usuarios-lista',
  templateUrl: './usuarios-lista.component.html'
})
export class UsuariosListaComponent implements OnInit {
  usuarios: Usuario[] = [];
  roles: Rol[] = [];
  loading = false;
  error = '';
  exito = '';

  constructor(
    private usuarioSvc: UsuarioService,
    private rolSvc: RolService
  ) {}

  ngOnInit(): void {
    this.cargarDatos();
  }

  cargarDatos(): void {
    this.loading = true;
    this.usuarioSvc.listar().subscribe({
      next: u => { this.usuarios = u; this.loading = false; },
      error: () => { this.error = 'Error al cargar usuarios'; this.loading = false; }
    });
    this.rolSvc.listar().subscribe({
      next: r => this.roles = r
    });
  }

  getNombreRol(rolId: string): string {
    return this.roles.find(r => r.id === rolId)?.nombre ?? rolId;
  }

  toggleActivo(usuario: Usuario): void {
    this.usuarioSvc.toggleActivo(usuario.id, !usuario.activo).subscribe({
      next: u => {
        const idx = this.usuarios.findIndex(x => x.id === u.id);
        if (idx >= 0) this.usuarios[idx] = u;
        this.exito = `Usuario ${u.activo ? 'activado' : 'desactivado'}`;
        setTimeout(() => this.exito = '', 3000);
      },
      error: () => this.error = 'Error al cambiar estado'
    });
  }

  eliminar(id: string): void {
    if (!confirm('¿Eliminar este usuario?')) return;
    this.usuarioSvc.eliminar(id).subscribe({
      next: () => {
        this.usuarios = this.usuarios.filter(u => u.id !== id);
        this.exito = 'Usuario eliminado';
        setTimeout(() => this.exito = '', 3000);
      },
      error: () => this.error = 'Error al eliminar'
    });
  }
}
```

### usuarios-lista.component.html
```html
<div class="container-fluid py-4">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h4 class="mb-0">Gestión de Usuarios</h4>
    <a class="btn btn-primary" routerLink="/admin/usuarios/nuevo">
      + Nuevo Usuario
    </a>
  </div>

  <div *ngIf="exito" class="alert alert-success">{{ exito }}</div>
  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <div *ngIf="loading" class="text-center py-5">
    <div class="spinner-border text-primary"></div>
  </div>

  <div *ngIf="!loading" class="card">
    <div class="table-responsive">
      <table class="table table-hover mb-0">
        <thead class="table-light">
          <tr>
            <th>Nombre</th>
            <th>Email</th>
            <th>Rol</th>
            <th>Tipo</th>
            <th>Estado</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let u of usuarios">
            <td>{{ u.nombre }} {{ u.apellido }}</td>
            <td>{{ u.email }}</td>
            <td>{{ getNombreRol(u.rolId) }}</td>
            <td>
              <span class="badge" [ngClass]="{
                'bg-danger': u.tipo === 'administrador',
                'bg-primary': u.tipo === 'funcionario',
                'bg-secondary': u.tipo === 'cliente'
              }">{{ u.tipo }}</span>
            </td>
            <td>
              <span class="badge" [ngClass]="u.activo ? 'bg-success' : 'bg-secondary'">
                {{ u.activo ? 'Activo' : 'Inactivo' }}
              </span>
            </td>
            <td>
              <div class="btn-group btn-group-sm">
                <a [routerLink]="['/admin/usuarios', u.id, 'editar']" class="btn btn-outline-secondary">Editar</a>
                <button (click)="toggleActivo(u)" class="btn btn-outline-warning">
                  {{ u.activo ? 'Desactivar' : 'Activar' }}
                </button>
                <button (click)="eliminar(u.id)" class="btn btn-outline-danger">Eliminar</button>
              </div>
            </td>
          </tr>
          <tr *ngIf="usuarios.length === 0">
            <td colspan="6" class="text-center text-muted py-4">Sin usuarios registrados</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</div>
```

---

## 4. Componente: Formulario Usuario (Crear / Editar)

### usuario-form.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { UsuarioService } from '../../../core/services/usuario.service';
import { RolService } from '../../../core/services/rol.service';
import { DepartamentoService } from '../../../core/services/departamento.service';
import { Rol } from '../../../core/models/rol.model';
import { Departamento } from '../../../core/models/departamento.model';

@Component({
  selector: 'app-usuario-form',
  templateUrl: './usuario-form.component.html'
})
export class UsuarioFormComponent implements OnInit {
  form!: FormGroup;
  roles: Rol[] = [];
  departamentos: Departamento[] = [];
  loading = false;
  error = '';
  esEdicion = false;
  usuarioId = '';

  tiposDisponibles = ['administrador', 'funcionario', 'cliente'];

  constructor(
    private fb: FormBuilder,
    private usuarioSvc: UsuarioService,
    private rolSvc: RolService,
    private deptoSvc: DepartamentoService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.iniciarForm();
    this.rolSvc.listar().subscribe(r => this.roles = r);
    this.deptoSvc.listar().subscribe(d => this.departamentos = d);

    this.usuarioId = this.route.snapshot.params['id'];
    if (this.usuarioId) {
      this.esEdicion = true;
      this.usuarioSvc.buscarPorId(this.usuarioId).subscribe(u => {
        this.form.patchValue(u);
        this.form.get('password')?.clearValidators(); // No requerida al editar
        this.form.get('password')?.updateValueAndValidity();
      });
    }
  }

  iniciarForm(): void {
    this.form = this.fb.group({
      nombre: ['', Validators.required],
      apellido: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      tipo: ['funcionario', Validators.required],
      rolId: ['', Validators.required],
      activo: [true]
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    const obs = this.esEdicion
      ? this.usuarioSvc.actualizar(this.usuarioId, this.form.value)
      : this.usuarioSvc.crear(this.form.value);

    obs.subscribe({
      next: () => this.router.navigate(['/admin/usuarios']),
      error: err => {
        this.error = err.error?.message ?? 'Error al guardar';
        this.loading = false;
      }
    });
  }
}
```

### usuario-form.component.html
```html
<div class="container py-4" style="max-width: 600px">
  <h4 class="mb-4">{{ esEdicion ? 'Editar' : 'Nuevo' }} Usuario</h4>

  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <form [formGroup]="form" (ngSubmit)="submit()">
    <div class="row g-3">
      <div class="col-6">
        <label class="form-label">Nombre *</label>
        <input class="form-control" formControlName="nombre">
      </div>
      <div class="col-6">
        <label class="form-label">Apellido *</label>
        <input class="form-control" formControlName="apellido">
      </div>
      <div class="col-12">
        <label class="form-label">Email *</label>
        <input type="email" class="form-control" formControlName="email">
      </div>
      <div class="col-12" *ngIf="!esEdicion">
        <label class="form-label">Contraseña *</label>
        <input type="password" class="form-control" formControlName="password">
      </div>
      <div class="col-6">
        <label class="form-label">Tipo *</label>
        <select class="form-select" formControlName="tipo">
          <option *ngFor="let t of tiposDisponibles" [value]="t">{{ t }}</option>
        </select>
      </div>
      <div class="col-6">
        <label class="form-label">Rol *</label>
        <select class="form-select" formControlName="rolId">
          <option value="">-- Seleccionar --</option>
          <option *ngFor="let r of roles" [value]="r.id">{{ r.nombre }}</option>
        </select>
      </div>
      <div class="col-12">
        <div class="form-check">
          <input class="form-check-input" type="checkbox" formControlName="activo" id="activo">
          <label class="form-check-label" for="activo">Usuario activo</label>
        </div>
      </div>
    </div>

    <div class="mt-4 d-flex gap-2">
      <button type="submit" class="btn btn-primary" [disabled]="loading || form.invalid">
        <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
        {{ esEdicion ? 'Actualizar' : 'Crear' }}
      </button>
      <a routerLink="/admin/usuarios" class="btn btn-secondary">Cancelar</a>
    </div>
  </form>
</div>
```

---

## 5. Componente: Lista de Departamentos

### departamentos.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { DepartamentoService } from '../../../core/services/departamento.service';
import { Departamento } from '../../../core/models/departamento.model';

@Component({
  selector: 'app-departamentos',
  templateUrl: './departamentos.component.html'
})
export class DepartamentosComponent implements OnInit {
  departamentos: Departamento[] = [];
  form!: FormGroup;
  modoEdicion = false;
  editandoId = '';
  loading = false;
  error = '';
  exito = '';

  constructor(private fb: FormBuilder, private deptoSvc: DepartamentoService) {}

  ngOnInit(): void {
    this.iniciarForm();
    this.cargar();
  }

  iniciarForm(): void {
    this.form = this.fb.group({
      codigo: ['', [Validators.required, Validators.maxLength(5)]],
      nombre: ['', Validators.required],
      descripcion: ['', Validators.required]
    });
  }

  cargar(): void {
    this.deptoSvc.listar().subscribe({
      next: d => this.departamentos = d,
      error: () => this.error = 'Error al cargar departamentos'
    });
  }

  editar(d: Departamento): void {
    this.modoEdicion = true;
    this.editandoId = d.id;
    this.form.patchValue(d);
  }

  cancelar(): void {
    this.modoEdicion = false;
    this.editandoId = '';
    this.form.reset();
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    const obs = this.modoEdicion
      ? this.deptoSvc.actualizar(this.editandoId, this.form.value)
      : this.deptoSvc.crear(this.form.value);

    obs.subscribe({
      next: () => {
        this.exito = `Departamento ${this.modoEdicion ? 'actualizado' : 'creado'}`;
        this.cancelar();
        this.cargar();
        this.loading = false;
        setTimeout(() => this.exito = '', 3000);
      },
      error: err => {
        this.error = err.error?.message ?? 'Error al guardar';
        this.loading = false;
      }
    });
  }

  eliminar(id: string): void {
    if (!confirm('¿Eliminar departamento?')) return;
    this.deptoSvc.eliminar(id).subscribe({
      next: () => { this.departamentos = this.departamentos.filter(d => d.id !== id); },
      error: () => this.error = 'Error al eliminar'
    });
  }
}
```

### departamentos.component.html
```html
<div class="container-fluid py-4">
  <h4 class="mb-4">Departamentos</h4>

  <div *ngIf="exito" class="alert alert-success">{{ exito }}</div>
  <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

  <div class="row g-4">
    <!-- Formulario -->
    <div class="col-md-4">
      <div class="card">
        <div class="card-header">{{ modoEdicion ? 'Editar' : 'Nuevo' }} Departamento</div>
        <div class="card-body">
          <form [formGroup]="form" (ngSubmit)="submit()">
            <div class="mb-3">
              <label class="form-label">Código *</label>
              <input class="form-control text-uppercase" formControlName="codigo" maxlength="5"
                     placeholder="ATC, TEC, LEG, OPE...">
            </div>
            <div class="mb-3">
              <label class="form-label">Nombre *</label>
              <input class="form-control" formControlName="nombre">
            </div>
            <div class="mb-3">
              <label class="form-label">Descripción *</label>
              <textarea class="form-control" formControlName="descripcion" rows="3"></textarea>
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
          <table class="table table-hover mb-0">
            <thead class="table-light">
              <tr>
                <th>Código</th>
                <th>Nombre</th>
                <th>Descripción</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let d of departamentos">
                <td><span class="badge bg-secondary">{{ d.codigo }}</span></td>
                <td>{{ d.nombre }}</td>
                <td class="text-muted small">{{ d.descripcion }}</td>
                <td>
                  <span class="badge" [ngClass]="d.activo ? 'bg-success' : 'bg-secondary'">
                    {{ d.activo ? 'Activo' : 'Inactivo' }}
                  </span>
                </td>
                <td>
                  <div class="btn-group btn-group-sm">
                    <button (click)="editar(d)" class="btn btn-outline-secondary">Editar</button>
                    <button (click)="eliminar(d.id)" class="btn btn-outline-danger">Eliminar</button>
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
| GET | `/api/usuarios` | Listar todos los usuarios |
| POST | `/api/usuarios` | Crear usuario |
| PUT | `/api/usuarios/{id}` | Actualizar usuario |
| DELETE | `/api/usuarios/{id}` | Eliminar usuario |
| GET | `/api/departamentos` | Listar departamentos |
| POST | `/api/departamentos` | Crear departamento |
| PUT | `/api/departamentos/{id}` | Actualizar departamento |
| DELETE | `/api/departamentos/{id}` | Eliminar departamento |
| GET | `/api/roles` | Listar roles (para select) |

---

## Checklist G2W

- [ ] Crear modelos: `usuario.model.ts`, `departamento.model.ts`, `rol.model.ts`
- [ ] Implementar `UsuarioService`, `DepartamentoService`, `RolService`
- [ ] Crear `UsuariosListaComponent` con tabla y acciones
- [ ] Crear `UsuarioFormComponent` para crear/editar
- [ ] Crear `DepartamentosComponent` con lista + formulario inline
- [ ] Proteger rutas con `AuthGuard` + `RolGuard` → roles: `['Administrador', 'SuperUser']`
- [ ] Verificar que se pueden crear y editar usuarios asignando roles
- [ ] Verificar CRUD completo de departamentos (ATC, TEC, LEG, OPE ya existen en DB)

**Próximo:** G3W — Gestión de Políticas y Actividades
