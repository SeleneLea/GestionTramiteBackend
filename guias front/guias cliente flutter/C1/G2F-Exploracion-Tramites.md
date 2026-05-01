# Guía 2F — Exploración de Trámites Disponibles

**Ciclo 1 · Sistema de Gestión de Trámites - Frontend**

> 🎯 **Objetivo:** El cliente puede ver la lista de políticas/trámites disponibles, filtrar por estado y explorar los detalles de cada uno.

---

## 0. Requisitos

✅ Completada la Guía 1F (Autenticación)
✅ Backend sirviendo endpoints:
  - `GET /api/politicas` (listar políticas)
  - `GET /api/politicas/{id}` (obtener detalle)
  - `GET /api/actividades` (listar actividades)
  - `GET /api/departamentos` (listar departamentos)

---

## 1. Actualizar Modelos TypeScript

### politica.model.ts (nuevo archivo)
```typescript
export interface PoliticaNegocio {
  id: string;
  nombre: string;
  descripcion: string;
  estado: string;  // borrador, activa, archivada
  duracionDiasLimite: number;
  requiereAprobacion: boolean;
  activo: boolean;
  fechaCreacion: string;
}

export interface ListaPoliticas {
  politicas: PoliticaNegocio[];
  total: number;
}
```

### actividad.model.ts (nuevo archivo)
```typescript
export interface Actividad {
  id: string;
  nombre: string;
  descripcion: string;
  departamentoId: string;
  duracionDiasLimite: number;
  requiereAprobacion: boolean;
  archivos: string[];
  activo: boolean;
}
```

### departamento.model.ts (nuevo archivo)
```typescript
export interface Departamento {
  id: string;
  nombre: string;
  correoContacto: string;
  activo: boolean;
}
```

---

## 2. Crear Servicio de Trámites

### tramites.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PoliticaNegocio, ListaPoliticas } from '../models/politica.model';
import { Actividad } from '../models/actividad.model';
import { Departamento } from '../models/departamento.model';

@Injectable({
  providedIn: 'root'
})
export class TramitesService {

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  /**
   * Obtener lista de políticas/trámites disponibles
   */
  obtenerPoliticas(estado?: string): Observable<PoliticaNegocio[]> {
    let params = new HttpParams();
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PoliticaNegocio[]>(`${this.apiUrl}/politicas`, { params });
  }

  /**
   * Obtener detalles de una política específica
   */
  obtenerPoliticaPorId(id: string): Observable<PoliticaNegocio> {
    return this.http.get<PoliticaNegocio>(`${this.apiUrl}/politicas/${id}`);
  }

  /**
   * Obtener lista de actividades
   */
  obtenerActividades(): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(`${this.apiUrl}/actividades`);
  }

  /**
   * Obtener actividad por ID
   */
  obtenerActividadPorId(id: string): Observable<Actividad> {
    return this.http.get<Actividad>(`${this.apiUrl}/actividades/${id}`);
  }

  /**
   * Obtener lista de departamentos
   */
  obtenerDepartamentos(): Observable<Departamento[]> {
    return this.http.get<Departamento[]>(`${this.apiUrl}/departamentos`);
  }

  /**
   * Obtener departamento por ID
   */
  obtenerDepartamentoPorId(id: string): Observable<Departamento> {
    return this.http.get<Departamento>(`${this.apiUrl}/departamentos/${id}`);
  }
}
```

---

## 3. Componente Listado de Trámites

### tramites-lista.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TramitesService } from '../../services/tramites.service';
import { PoliticaNegocio } from '../../models/politica.model';

@Component({
  selector: 'app-tramites-lista',
  templateUrl: './tramites-lista.component.html',
  styleUrls: ['./tramites-lista.component.css']
})
export class TramitesListaComponent implements OnInit {

  politicas: PoliticaNegocio[] = [];
  loading = false;
  errorMessage: string | null = null;

  // Filtros
  filtroEstado: string = 'activa';
  estadosDisponibles = ['borrador', 'activa', 'archivada'];

  // Búsqueda
  busqueda: string = '';

  constructor(
    private tramitesService: TramitesService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.cargarPoliticas();
  }

  cargarPoliticas(): void {
    this.loading = true;
    this.errorMessage = null;

    this.tramitesService.obtenerPoliticas(this.filtroEstado).subscribe({
      next: (data) => {
        this.politicas = data;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error al cargar políticas:', error);
        this.errorMessage = 'Error al cargar los trámites disponibles';
        this.loading = false;
      }
    });
  }

  /**
   * Filtrar políticas cuando cambia el estado
   */
  onFiltroEstadoChange(): void {
    this.cargarPoliticas();
  }

  /**
   * Filtrar políticas por búsqueda
   */
  get politicasFiltradas(): PoliticaNegocio[] {
    if (!this.busqueda) {
      return this.politicas;
    }

    const termino = this.busqueda.toLowerCase();
    return this.politicas.filter(p =>
      p.nombre.toLowerCase().includes(termino) ||
      p.descripcion.toLowerCase().includes(termino)
    );
  }

  /**
   * Navegar al detalle de un trámite
   */
  verDetalle(politicaId: string): void {
    this.router.navigate(['/tramites', politicaId]);
  }

  /**
   * Iniciar un nuevo trámite (irá a la guía 3F)
   */
  iniciarTramite(politicaId: string): void {
    this.router.navigate(['/tramites', politicaId, 'nuevo']);
  }

  /**
   * Obtener icono según estado
   */
  getEstadoIcono(estado: string): string {
    const iconos: { [key: string]: string } = {
      'activa': '✓',
      'borrador': '⚙',
      'archivada': '📦'
    };
    return iconos[estado] || '•';
  }

  /**
   * Obtener clase CSS según estado
   */
  getEstadoClase(estado: string): string {
    const clases: { [key: string]: string } = {
      'activa': 'badge-success',
      'borrador': 'badge-warning',
      'archivada': 'badge-secondary'
    };
    return clases[estado] || 'badge-info';
  }
}
```

### tramites-lista.component.html
```html
<div class="container mt-4">
  <!-- Header -->
  <div class="row mb-4">
    <div class="col-md-8">
      <h1>Trámites Disponibles</h1>
      <p class="text-muted">Selecciona un trámite para iniciar o ver detalles</p>
    </div>
    <div class="col-md-4 text-end">
      <button class="btn btn-outline-secondary" (click)="cargarPoliticas()">
        🔄 Actualizar
      </button>
    </div>
  </div>

  <!-- Filtros -->
  <div class="row mb-4">
    <div class="col-md-6">
      <div class="input-group">
        <input
          type="text"
          class="form-control"
          placeholder="Buscar trámite..."
          [(ngModel)]="busqueda"
        >
        <button class="btn btn-outline-secondary" type="button">
          🔍 Buscar
        </button>
      </div>
    </div>
    <div class="col-md-6">
      <select
        class="form-select"
        [(ngModel)]="filtroEstado"
        (change)="onFiltroEstadoChange()"
      >
        <option value="">Todos los estados</option>
        <option *ngFor="let estado of estadosDisponibles" [value]="estado">
          {{ estado | titlecase }}
        </option>
      </select>
    </div>
  </div>

  <!-- Mensaje de Error -->
  <div *ngIf="errorMessage" class="alert alert-danger alert-dismissible fade show">
    {{ errorMessage }}
    <button type="button" class="btn-close" (click)="errorMessage = null"></button>
  </div>

  <!-- Loading -->
  <div *ngIf="loading" class="text-center">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Cargando...</span>
    </div>
  </div>

  <!-- Lista de Trámites -->
  <div *ngIf="!loading && politicasFiltradas.length > 0" class="row">
    <div *ngFor="let politica of politicasFiltradas" class="col-md-6 mb-4">
      <div class="card h-100 shadow-sm hover-card">
        <div class="card-header">
          <div class="d-flex justify-content-between align-items-start">
            <h5 class="card-title mb-0">{{ politica.nombre }}</h5>
            <span [ngClass]="'badge ' + getEstadoClase(politica.estado)">
              {{ politica.estado | titlecase }}
            </span>
          </div>
        </div>

        <div class="card-body">
          <p class="card-text text-muted">{{ politica.descripcion }}</p>

          <div class="mb-3">
            <small class="text-secondary">
              <strong>⏱ Límite:</strong> {{ politica.duracionDiasLimite }} días
            </small>
          </div>

          <div>
            <span *ngIf="politica.requiereAprobacion" class="badge bg-warning text-dark">
              ✓ Requiere Aprobación
            </span>
          </div>
        </div>

        <div class="card-footer bg-white">
          <button
            class="btn btn-primary btn-sm w-100 mb-2"
            (click)="iniciarTramite(politica.id)"
            [disabled]="politica.estado !== 'activa'"
          >
            📋 Iniciar Trámite
          </button>
          <button
            class="btn btn-outline-secondary btn-sm w-100"
            (click)="verDetalle(politica.id)"
          >
            👁️ Ver Detalles
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Sin Resultados -->
  <div *ngIf="!loading && politicasFiltradas.length === 0" class="alert alert-info">
    <p class="mb-0">
      <strong>No hay trámites disponibles</strong> con los filtros seleccionados.
    </p>
  </div>
</div>

<style>
  .hover-card {
    transition: transform 0.2s, box-shadow 0.2s;
    cursor: pointer;
  }

  .hover-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1) !important;
  }
</style>
```

---

## 4. Componente Detalle de Trámite

### tramites-detalle.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TramitesService } from '../../services/tramites.service';
import { PoliticaNegocio } from '../../models/politica.model';

@Component({
  selector: 'app-tramites-detalle',
  templateUrl: './tramites-detalle.component.html',
  styleUrls: ['./tramites-detalle.component.css']
})
export class TramitesDetalleComponent implements OnInit {

  politica: PoliticaNegocio | null = null;
  loading = false;
  errorMessage: string | null = null;
  politicaId: string = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private tramitesService: TramitesService
  ) { }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.politicaId = params['id'];
      if (this.politicaId) {
        this.cargarDetalle();
      }
    });
  }

  cargarDetalle(): void {
    this.loading = true;
    this.errorMessage = null;

    this.tramitesService.obtenerPoliticaPorId(this.politicaId).subscribe({
      next: (data) => {
        this.politica = data;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error al cargar detalle:', error);
        this.errorMessage = 'No se pudo cargar el detalle del trámite';
        this.loading = false;
      }
    });
  }

  iniciarTramite(): void {
    this.router.navigate(['/tramites', this.politicaId, 'nuevo']);
  }

  volverALista(): void {
    this.router.navigate(['/tramites']);
  }
}
```

### tramites-detalle.component.html
```html
<div class="container mt-4">
  <!-- Header -->
  <div class="mb-4">
    <button class="btn btn-outline-secondary mb-3" (click)="volverALista()">
      ← Volver a la lista
    </button>
  </div>

  <!-- Loading -->
  <div *ngIf="loading" class="text-center">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Cargando...</span>
    </div>
  </div>

  <!-- Error -->
  <div *ngIf="errorMessage" class="alert alert-danger">
    {{ errorMessage }}
  </div>

  <!-- Detalle -->
  <div *ngIf="!loading && politica" class="row">
    <div class="col-md-8">
      <div class="card">
        <div class="card-header bg-primary text-white">
          <h2 class="mb-0">{{ politica.nombre }}</h2>
        </div>

        <div class="card-body">
          <!-- Descripción -->
          <section class="mb-4">
            <h4>Descripción</h4>
            <p>{{ politica.descripcion }}</p>
          </section>

          <!-- Detalles -->
          <section class="mb-4">
            <h4>Información General</h4>
            <dl class="row">
              <dt class="col-sm-4">Estado:</dt>
              <dd class="col-sm-8">
                <span [ngClass]="'badge badge-' + (politica.estado === 'activa' ? 'success' : politica.estado === 'borrador' ? 'warning' : 'secondary')">
                  {{ politica.estado | titlecase }}
                </span>
              </dd>

              <dt class="col-sm-4">Duración Límite:</dt>
              <dd class="col-sm-8">{{ politica.duracionDiasLimite }} días</dd>

              <dt class="col-sm-4">Requiere Aprobación:</dt>
              <dd class="col-sm-8">
                <span *ngIf="politica.requiereAprobacion" class="text-success">✓ Sí</span>
                <span *ngIf="!politica.requiereAprobacion" class="text-muted">No</span>
              </dd>

              <dt class="col-sm-4">Creado:</dt>
              <dd class="col-sm-8">{{ politica.fechaCreacion | date: 'medium' }}</dd>
            </dl>
          </section>

          <!-- Requisitos -->
          <section class="mb-4">
            <h4>Requisitos para el Trámite</h4>
            <ul class="list-group">
              <li class="list-group-item">
                <strong>Información Personal:</strong> Se requerirá tu nombre, email y teléfono de contacto
              </li>
              <li class="list-group-item">
                <strong>Documentación:</strong> Según el tipo de trámite, deberás subir documentos soporte
              </li>
              <li class="list-group-item">
                <strong>Firma Digital:</strong> Aceptación de términos y condiciones
              </li>
            </ul>
          </section>

          <!-- Flujo -->
          <section>
            <h4>Flujo del Proceso</h4>
            <div class="alert alert-info">
              <p>
                El trámite pasará por diferentes etapas según la política configurada.
                Podrás seguir el estado en tiempo real desde tu panel de control.
              </p>
            </div>
          </section>
        </div>

        <div class="card-footer">
          <button
            class="btn btn-primary btn-lg"
            (click)="iniciarTramite()"
            [disabled]="politica.estado !== 'activa'"
          >
            Iniciar Este Trámite
          </button>
        </div>
      </div>
    </div>

    <!-- Sidebar -->
    <div class="col-md-4">
      <div class="card bg-light">
        <div class="card-body">
          <h5>¿Necesitas Ayuda?</h5>
          <p class="text-muted">
            Si tienes dudas sobre este trámite, puedes:
          </p>
          <ul class="list-unstyled">
            <li>📧 Enviar un correo al departamento responsable</li>
            <li>💬 Consultar en el chat de soporte</li>
            <li>📞 Llamar a nuestro centro de atención</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</div>

<style>
  .badge-success { background-color: #28a745; }
  .badge-warning { background-color: #ffc107; color: #000; }
  .badge-secondary { background-color: #6c757d; }
</style>
```

---

## 5. Actualizar Rutas (app-routing.module.ts)

```typescript
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { AuthGuard } from './auth/auth.guard';
import { TramitesListaComponent } from './tramites/tramites-lista/tramites-lista.component';
import { TramitesDetalleComponent } from './tramites/tramites-detalle/tramites-detalle.component';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  
  // Rutas protegidas
  {
    path: 'tramites',
    canActivate: [AuthGuard],
    children: [
      { path: '', component: TramitesListaComponent },
      { path: ':id', component: TramitesDetalleComponent }
    ]
  },

  { path: '', redirectTo: '/tramites', pathMatch: 'full' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

---

## 6. Actualizar app.module.ts

```typescript
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { TramitesListaComponent } from './tramites/tramites-lista/tramites-lista.component';
import { TramitesDetalleComponent } from './tramites/tramites-detalle/tramites-detalle.component';
import { AuthInterceptor } from './auth/auth.interceptor';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    RegisterComponent,
    TramitesListaComponent,
    TramitesDetalleComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    ReactiveFormsModule,
    FormsModule
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
```

---

## Checklist de Implementación

- [ ] Crear modelos: politica.model.ts, actividad.model.ts, departamento.model.ts
- [ ] Crear servicio: tramites.service.ts
- [ ] Crear componente lista: tramites-lista.component.ts + .html + .css
- [ ] Crear componente detalle: tramites-detalle.component.ts + .html + .css
- [ ] Actualizar rutas en app-routing.module.ts
- [ ] Actualizar app.module.ts
- [ ] Instalar ngModel: `npm install @angular/forms`
- [ ] Probar navegación: http://localhost:4200/tramites
- [ ] Verificar filtros y búsqueda funcionan
- [ ] Probar ver detalle al hacer click

---

## Endpoints Backend Utilizados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/politicas` | Listar políticas |
| GET | `/api/politicas/{id}` | Obtener detalle de política |
| GET | `/api/actividades` | Listar actividades |
| GET | `/api/departamentos` | Listar departamentos |

---

## Próximos Pasos

✅ **G2F Completada:** Exploración de trámites disponibles

👉 **G3F - Envío de Trámites:** El cliente llena un formulario y envía un nuevo trámite al sistema
