# Guía 4F — Seguimiento y Estado de Trámites

**Ciclo 1 · Sistema de Gestión de Trámites - Frontend**

> 🎯 **Objetivo:** El cliente puede ver el estado actual de sus trámites, seguir el progreso, ver actividades pendientes y el historial.

---

## 0. Requisitos

✅ Completadas las Guías 1F, 2F y 3F
✅ Backend sirviendo:
  - `GET /api/workflow/{tramiteId}/estado` (estado actual)
  - `GET /api/usuarios/tramites` (listar trámites del usuario)
  - Expediente con secciones y estado

---

## 1. Modelos TypeScript

### tramite.model.ts (ampliado)
```typescript
export interface TramiteResumen {
  id: string;
  codigo: string;
  politicaNombre: string;
  estado: string;  // borrador, activo, completado, archivado
  nodoActualNombre: string;
  fechaInicio: string;
  fechaCierreReal?: string;
  progreso: number;  // 0-100
}

export interface EstadoTramite {
  tramiteId: string;
  codigo: string;
  estado: string;
  nodoActual: {
    id: string;
    nombre: string;
    tipo: string;  // inicio, actividad, decision, fork, join, fin
    departamento?: string;
  };
  expediente: {
    id: string;
    secciones: SeccionEstado[];
  };
  historial: EventoHistorico[];
  progreso: number;
}

export interface SeccionEstado {
  id: string;
  nombre: string;
  estado: string;  // bloqueada, en_curso, completada
  actividad?: string;
  departamento?: string;
  fechaInicio?: string;
  fechaCompletacion?: string;
}

export interface EventoHistorico {
  id: string;
  tipo: string;  // creacion, cambio_estado, aprobacion, rechazo
  descripcion: string;
  usuario?: string;
  departamento?: string;
  fecha: string;
  detalles?: any;
}
```

---

## 2. Servicio de Seguimiento

### tramites-seguimiento.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, interval, switchMap, startWith } from 'rxjs';
import { environment } from '../../environments/environment';
import { TramiteResumen, EstadoTramite } from '../models/tramite.model';

@Injectable({
  providedIn: 'root'
})
export class TramitesSeguimientoService {

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  /**
   * Obtener lista de trámites del usuario autenticado
   * 
   * NOTA: Requiere que el backend implemente el endpoint:
   * GET /api/workflow/mis-tramites
   * 
   * Este endpoint filtra trámites por clienteId del usuario autenticado.
   * Se debe agregar en WorkflowController en Ciclo 1 Final o C2.
   */
  obtenerMisTramites(): Observable<TramiteResumen[]> {
    return this.http.get<TramiteResumen[]>(`${this.apiUrl}/workflow/mis-tramites`);
  }

  /**
   * Obtener estado detallado de un trámite
   */
  obtenerEstadoTramite(tramiteId: string): Observable<EstadoTramite> {
    return this.http.get<EstadoTramite>(`${this.apiUrl}/workflow/${tramiteId}/estado`);
  }

  /**
   * Obtener estado en tiempo real (actualiza cada 30 segundos)
   */
  obtenerEstadoEnTiempoReal(tramiteId: string): Observable<EstadoTramite> {
    return interval(30000).pipe(
      startWith(0),
      switchMap(() => this.obtenerEstadoTramite(tramiteId))
    );
  }

  /**
   * Calcular porcentaje de progreso basado en el nodo actual
   */
  calcularProgreso(estado: EstadoTramite): number {
    if (!estado.expediente || !estado.expediente.secciones) {
      return 0;
    }

    const secciones = estado.expediente.secciones;
    const completadas = secciones.filter(s => s.estado === 'completada').length;
    return Math.round((completadas / secciones.length) * 100);
  }

  /**
   * Obtener color según estado del trámite
   */
  getColorEstado(estado: string): string {
    const colores: { [key: string]: string } = {
      'borrador': 'secondary',
      'en_progreso': 'info',
      'completado': 'success',
      'rechazado': 'danger',
      'aprobado': 'success'
    };
    return colores[estado] || 'warning';
  }

  /**
   * Obtener icono según tipo de evento
   */
  getIconoEvento(tipo: string): string {
    const iconos: { [key: string]: string } = {
      'creacion': '✓',
      'cambio_estado': '→',
      'aprobacion': '✓',
      'rechazo': '✗',
      'completacion': '✓',
      'archivo': '📎'
    };
    return iconos[tipo] || '•';
  }
}
```

---

## 3. Componente Lista de Mis Trámites

### mis-tramites.component.ts
```typescript
import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { TramitesSeguimientoService } from '../../services/tramites-seguimiento.service';
import { TramiteResumen } from '../../models/tramite.model';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-mis-tramites',
  templateUrl: './mis-tramites.component.html',
  styleUrls: ['./mis-tramites.component.css']
})
export class MisTramitesComponent implements OnInit, OnDestroy {

  tramites: TramiteResumen[] = [];
  loading = false;
  errorMessage: string | null = null;
  filtroEstado: string = '';
  busqueda: string = '';

  estadosDisponibles = [
    { label: 'Todos', value: '' },
    { label: 'Borradores', value: 'borrador' },
    { label: 'En Proceso', value: 'en_progreso' },
    { label: 'Completados', value: 'completado' },
    { label: 'Archivados', value: 'archivado' }
  ];

  private destroy$ = new Subject<void>();

  constructor(
    private tramitesSeguimientoService: TramitesSeguimientoService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.cargarTramites();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargarTramites(): void {
    this.loading = true;
    this.errorMessage = null;

    this.tramitesSeguimientoService.obtenerMisTramites()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.tramites = data;
          this.loading = false;
        },
        error: (error) => {
          console.error('Error al cargar trámites:', error);
          this.errorMessage = 'Error al cargar tus trámites';
          this.loading = false;
        }
      });
  }

  /**
   * Filtrar trámites
   */
  get tramitesFiltrados(): TramiteResumen[] {
    return this.tramites.filter(t => {
      const estadoMatch = !this.filtroEstado || t.estado === this.filtroEstado;
      const busquedaMatch = !this.busqueda ||
        t.codigo.toLowerCase().includes(this.busqueda.toLowerCase()) ||
        t.politicaNombre.toLowerCase().includes(this.busqueda.toLowerCase());
      return estadoMatch && busquedaMatch;
    });
  }

  /**
   * Ver detalle/seguimiento de un trámite
   */
  verSeguimiento(tramiteId: string): void {
    this.router.navigate(['/tramites', tramiteId, 'seguimiento']);
  }

  /**
   * Continuar con un trámite borrador
   */
  continuarBorrador(tramiteId: string): void {
    this.router.navigate(['/tramites', tramiteId, 'continuar']);
  }

  /**
   * Obtener icono según estado
   */
  getEstadoIcono(estado: string): string {
    const iconos: { [key: string]: string } = {
      'borrador': '📝',
      'en_progreso': '⏳',
      'completado': '✓',
      'archivado': '📦',
      'rechazado': '✗'
    };
    return iconos[estado] || '•';
  }

  /**
   * Obtener color del badge según estado
   */
  getColorEstado(estado: string): string {
    const colores: { [key: string]: string } = {
      'borrador': 'warning',
      'en_progreso': 'info',
      'completado': 'success',
      'archivado': 'secondary',
      'rechazado': 'danger'
    };
    return 'badge-' + (colores[estado] || 'secondary');
  }

  /**
   * Texto legible del estado
   */
  textoEstado(estado: string): string {
    const textos: { [key: string]: string } = {
      'borrador': 'Borrador',
      'en_progreso': 'En Proceso',
      'completado': 'Completado',
      'archivado': 'Archivado',
      'rechazado': 'Rechazado'
    };
    return textos[estado] || estado;
  }
}
```

### mis-tramites.component.html
```html
<div class="container mt-4">
  <!-- Header -->
  <div class="row mb-4">
    <div class="col-md-8">
      <h1>Mis Trámites</h1>
      <p class="text-muted">Aquí puedes ver el estado de todos tus trámites</p>
    </div>
    <div class="col-md-4 text-end">
      <button class="btn btn-primary" routerLink="/tramites">
        + Iniciar Nuevo
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
          placeholder="Buscar por código o nombre..."
          [(ngModel)]="busqueda"
        >
        <button class="btn btn-outline-secondary" type="button">
          🔍
        </button>
      </div>
    </div>
    <div class="col-md-6">
      <select
        class="form-select"
        [(ngModel)]="filtroEstado"
      >
        <option *ngFor="let opcion of estadosDisponibles" [value]="opcion.value">
          {{ opcion.label }}
        </option>
      </select>
    </div>
  </div>

  <!-- Error -->
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
  <div *ngIf="!loading && tramitesFiltrados.length > 0">
    <div class="row">
      <div *ngFor="let tramite of tramitesFiltrados" class="col-md-6 mb-4">
        <div class="card h-100 tramite-card" [ngClass]="'tramite-' + tramite.estado">
          <div class="card-header">
            <div class="d-flex justify-content-between align-items-start">
              <div>
                <h5 class="card-title mb-1">{{ tramite.codigo }}</h5>
                <small class="text-muted">{{ tramite.politicaNombre }}</small>
              </div>
              <span [ngClass]="'badge ' + getColorEstado(tramite.estado)">
                {{ getEstadoIcono(tramite.estado) }} {{ textoEstado(tramite.estado) }}
              </span>
            </div>
          </div>

          <div class="card-body">
            <!-- Nodo Actual -->
            <div class="mb-3">
              <small class="text-secondary">
                <strong>Etapa Actual:</strong> {{ tramite.nodoActualNombre }}
              </small>
            </div>

            <!-- Barra de Progreso -->
            <div class="mb-3">
              <div class="progress">
                <div
                  class="progress-bar"
                  [style.width.%]="tramite.progreso"
                  role="progressbar"
                  [attr.aria-valuenow]="tramite.progreso"
                >
                  {{ tramite.progreso }}%
                </div>
              </div>
            </div>

            <!-- Fechas -->
            <small class="text-muted">
              <div>📅 Creado: {{ tramite.fechaInicio | date: 'medium' }}</div>
              <div *ngIf="tramite.fechaCierreReal">
                ✓ Cerrado: {{ tramite.fechaCierreReal | date: 'medium' }}
              </div>
            </small>
          </div>

          <div class="card-footer bg-white">
            <button
              class="btn btn-primary btn-sm w-100 mb-2"
              (click)="verSeguimiento(tramite.id)"
            >
              👁️ Ver Detalles
            </button>
            <button
              *ngIf="tramite.estado === 'borrador'"
              class="btn btn-warning btn-sm w-100"
              (click)="continuarBorrador(tramite.id)"
            >
              ✏️ Continuar Editando
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Sin Resultados -->
  <div *ngIf="!loading && tramitesFiltrados.length === 0" class="alert alert-info">
    <p class="mb-0">
      <strong>No hay trámites</strong> que coincidan con los filtros seleccionados.
    </p>
  </div>
</div>

<style>
  .tramite-card {
    transition: transform 0.2s, box-shadow 0.2s;
  }

  .tramite-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
  }

  .tramite-borrador {
    border-left: 4px solid #ffc107;
  }

  .tramite-en_progreso {
    border-left: 4px solid #17a2b8;
  }

  .tramite-completado {
    border-left: 4px solid #28a745;
  }

  .tramite-archivado {
    border-left: 4px solid #6c757d;
  }
</style>
```

---

## 4. Componente Detalle y Seguimiento

### tramite-seguimiento.component.ts
```typescript
import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TramitesSeguimientoService } from '../../services/tramites-seguimiento.service';
import { EstadoTramite } from '../../models/tramite.model';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-tramite-seguimiento',
  templateUrl: './tramite-seguimiento.component.html',
  styleUrls: ['./tramite-seguimiento.component.css']
})
export class TramiteSeguimientoComponent implements OnInit, OnDestroy {

  estado: EstadoTramite | null = null;
  loading = false;
  errorMessage: string | null = null;
  tramiteId: string = '';
  autoRefresh = true;

  pestanaActiva: 'resumen' | 'historial' | 'secciones' = 'resumen';

  private destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private tramitesSeguimientoService: TramitesSeguimientoService
  ) { }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.tramiteId = params['id'];
      if (this.tramiteId) {
        this.cargarEstado();
        if (this.autoRefresh) {
          this.activarActualizacionAutomatica();
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargarEstado(): void {
    this.loading = true;
    this.errorMessage = null;

    this.tramitesSeguimientoService.obtenerEstadoTramite(this.tramiteId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.estado = data;
          this.loading = false;
        },
        error: (error) => {
          console.error('Error al cargar estado:', error);
          this.errorMessage = 'Error al cargar el estado del trámite';
          this.loading = false;
        }
      });
  }

  activarActualizacionAutomatica(): void {
    this.tramitesSeguimientoService.obtenerEstadoEnTiempoReal(this.tramiteId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.estado = data;
        }
      });
  }

  obtenerProgreso(): number {
    if (!this.estado) return 0;
    return this.tramitesSeguimientoService.calcularProgreso(this.estado);
  }

  obtenerColorEstado(estado: string): string {
    return this.tramitesSeguimientoService.getColorEstado(estado);
  }

  obtenerIconoEvento(tipo: string): string {
    return this.tramitesSeguimientoService.getIconoEvento(tipo);
  }

  volverAMisTramites(): void {
    this.router.navigate(['/mis-tramites']);
  }

  descargarCertificado(): void {
    // Implementar descarga de certificado
    console.log('Descargando certificado...');
  }

  enviarMensaje(): void {
    // Implementar envío de mensajes/consultas
    console.log('Enviando mensaje...');
  }
}
```

### tramite-seguimiento.component.html
```html
<div class="container-fluid mt-4">
  <!-- Header -->
  <div class="row mb-4">
    <div class="col">
      <button class="btn btn-outline-secondary mb-3" (click)="volverAMisTramites()">
        ← Volver a Mis Trámites
      </button>
    </div>
  </div>

  <!-- Loading -->
  <div *ngIf="loading" class="text-center">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Cargando estado...</span>
    </div>
  </div>

  <!-- Error -->
  <div *ngIf="errorMessage" class="alert alert-danger alert-dismissible fade show">
    {{ errorMessage }}
    <button type="button" class="btn-close" (click)="errorMessage = null"></button>
  </div>

  <!-- Contenido -->
  <div *ngIf="!loading && estado" class="row">
    <!-- Panel Principal -->
    <div class="col-md-8">
      <!-- Card de Estado General -->
      <div class="card mb-4">
        <div class="card-header bg-primary text-white">
          <div class="d-flex justify-content-between align-items-center">
            <div>
              <h5 class="mb-0">{{ estado.codigo }}</h5>
              <small>Trámite ID: {{ estado.tramiteId }}</small>
            </div>
            <span [ngClass]="'badge badge-' + obtenerColorEstado(estado.estado)">
              {{ estado.estado | titlecase }}
            </span>
          </div>
        </div>

        <div class="card-body">
          <!-- Barra de Progreso -->
          <div class="mb-4">
            <h6>Progreso General</h6>
            <div class="progress" style="height: 25px;">
              <div
                class="progress-bar"
                [style.width.%]="obtenerProgreso()"
                role="progressbar"
              >
                {{ obtenerProgreso() }}%
              </div>
            </div>
          </div>

          <!-- Nodo Actual -->
          <div class="alert alert-info" *ngIf="estado.nodoActual">
            <h6 class="alert-heading">⏳ Etapa Actual</h6>
            <div class="row">
              <div class="col-md-6">
                <strong>Nombre:</strong> {{ estado.nodoActual.nombre }}
              </div>
              <div class="col-md-6">
                <strong>Tipo:</strong> <code>{{ estado.nodoActual.tipo }}</code>
              </div>
              <div class="col-md-6" *ngIf="estado.nodoActual.departamento">
                <strong>Departamento:</strong> {{ estado.nodoActual.departamento }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Pestañas -->
      <ul class="nav nav-tabs mb-4">
        <li class="nav-item">
          <a
            class="nav-link"
            [ngClass]="{ active: pestanaActiva === 'resumen' }"
            href="javascript:void(0)"
            (click)="pestanaActiva = 'resumen'"
          >
            📋 Resumen
          </a>
        </li>
        <li class="nav-item">
          <a
            class="nav-link"
            [ngClass]="{ active: pestanaActiva === 'secciones' }"
            href="javascript:void(0)"
            (click)="pestanaActiva = 'secciones'"
          >
            📑 Secciones
          </a>
        </li>
        <li class="nav-item">
          <a
            class="nav-link"
            [ngClass]="{ active: pestanaActiva === 'historial' }"
            href="javascript:void(0)"
            (click)="pestanaActiva = 'historial'"
          >
            📜 Historial
          </a>
        </li>
      </ul>

      <!-- RESUMEN -->
      <div *ngIf="pestanaActiva === 'resumen'">
        <!-- Próximos Pasos -->
        <div class="card mb-3">
          <div class="card-header">
            <h6 class="mb-0">Próximos Pasos</h6>
          </div>
          <div class="card-body">
            <div class="alert alert-warning">
              <p class="mb-0">
                Tu trámite está en fase de revisión. Se te notificará cuando
                se requiera información adicional o cuando se complete el proceso.
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- SECCIONES -->
      <div *ngIf="pestanaActiva === 'secciones'">
        <div class="card">
          <div class="card-header">
            <h6 class="mb-0">Secciones del Expediente</h6>
          </div>
          <div class="list-group list-group-flush">
            <div
              *ngFor="let seccion of estado.expediente.secciones"
              class="list-group-item"
            >
              <div class="d-flex justify-content-between align-items-center">
                <div>
                  <h6 class="mb-1">{{ seccion.nombre }}</h6>
                  <small class="text-muted">
                    {{ seccion.departamento || 'Sin departamento asignado' }}
                  </small>
                </div>
                <span
                  [ngClass]="{
                    'badge': true,
                    'bg-success': seccion.estado === 'completada',
                    'bg-warning': seccion.estado === 'en_curso',
                    'bg-secondary': seccion.estado === 'bloqueada'
                  }"
                >
                  {{ seccion.estado | titlecase }}
                </span>
              </div>
              <div class="mt-2" *ngIf="seccion.fechaInicio">
                <small class="text-muted">
                  Inicio: {{ seccion.fechaInicio | date: 'medium' }}
                </small>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- HISTORIAL -->
      <div *ngIf="pestanaActiva === 'historial'">
        <div class="card">
          <div class="card-header">
            <h6 class="mb-0">Historial de Cambios</h6>
          </div>
          <div class="card-body">
            <div class="timeline">
              <div
                *ngFor="let evento of estado.historial; let last = last"
                class="timeline-item"
                [ngClass]="{ 'last': last }"
              >
                <div class="timeline-marker">
                  {{ obtenerIconoEvento(evento.tipo) }}
                </div>
                <div class="timeline-content">
                  <h6 class="mb-1">{{ evento.descripcion }}</h6>
                  <small class="text-muted">
                    <div>{{ evento.fecha | date: 'medium' }}</div>
                    <div *ngIf="evento.usuario">Usuario: {{ evento.usuario }}</div>
                    <div *ngIf="evento.departamento">Depto: {{ evento.departamento }}</div>
                  </small>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Panel Lateral -->
    <div class="col-md-4">
      <!-- Acciones Rápidas -->
      <div class="card mb-4">
        <div class="card-header">
          <h6 class="mb-0">Acciones</h6>
        </div>
        <div class="card-body">
          <button
            class="btn btn-outline-primary btn-sm w-100 mb-2"
            (click)="descargarCertificado()"
          >
            📥 Descargar Certificado
          </button>
          <button
            class="btn btn-outline-secondary btn-sm w-100"
            (click)="enviarMensaje()"
          >
            💬 Enviar Consulta
          </button>
        </div>
      </div>

      <!-- Información de Contacto -->
      <div class="card bg-light">
        <div class="card-body">
          <h6>¿Necesitas Ayuda?</h6>
          <p class="small text-muted mb-2">
            Si tienes preguntas sobre el estado de tu trámite:
          </p>
          <ul class="small text-muted">
            <li>📧 soporte@tramites.gov.co</li>
            <li>📞 1-800-TRAMITE</li>
            <li>💬 Chat en vivo (lunes a viernes, 8am-6pm)</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</div>

<style>
  .timeline {
    position: relative;
    padding: 20px 0;
  }

  .timeline-item {
    display: flex;
    margin-bottom: 20px;
    position: relative;
  }

  .timeline-item.last {
    margin-bottom: 0;
  }

  .timeline-marker {
    width: 40px;
    height: 40px;
    background: #007bff;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    flex-shrink: 0;
    position: relative;
    z-index: 2;
  }

  .timeline-item:not(.last)::before {
    content: '';
    position: absolute;
    left: 19px;
    top: 40px;
    bottom: -20px;
    width: 2px;
    background: #e9ecef;
  }

  .timeline-content {
    padding-left: 20px;
  }
</style>
```

---

## 5. Actualizar Rutas

```typescript
// Agregar a app-routing.module.ts
{
  path: 'mis-tramites',
  component: MisTramitesComponent,
  canActivate: [AuthGuard]
},
{
  path: 'tramites/:id/seguimiento',
  component: TramiteSeguimientoComponent,
  canActivate: [AuthGuard]
}
```

---

## 6. Actualizar Navigation

Agregar links en navbar/sidebar:
```html
<a routerLink="/tramites" class="nav-link">📋 Trámites</a>
<a routerLink="/mis-tramites" class="nav-link">📊 Mis Trámites</a>
```

---

## Checklist de Implementación

- [ ] Crear servicio: tramites-seguimiento.service.ts
- [ ] Crear componente: mis-tramites.component.ts + .html + .css
- [ ] Crear componente: tramite-seguimiento.component.ts + .html + .css
- [ ] Actualizar rutas
- [ ] Actualizar app.module.ts
- [ ] Probar carga de lista de trámites
- [ ] Probar detalle y seguimiento
- [ ] Probar actualización automática cada 30 segundos
- [ ] Validar visualización del historial

---

## Endpoints Backend Utilizados

| Método | Endpoint | Descripción | Estado |
|--------|----------|-------------|--------|
| GET | `/api/workflow/mis-tramites` | Listar trámites del usuario | ⚠️ **A IMPLEMENTAR en backend** |
| GET | `/api/workflow/{tramiteId}/estado` | Obtener estado detallado | ✅ Existe (G4 backend) |

### ⚠️ NOTA IMPORTANTE

El endpoint `GET /api/workflow/mis-tramites` **no existe aún en el backend G1-G5**.

**Acción requerida:** Agregar el siguiente método en `WorkflowController`:

```java
@GetMapping("/mis-tramites")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<List<Tramite>> misTramites(Authentication auth) {
    String clienteId = auth.getName();  // Email del usuario autenticado
    List<Tramite> tramites = tramiteService.listarPorCliente(clienteId);
    return ResponseEntity.ok(tramites);
}
```

En `TramiteService`, implementar:
```java
public List<Tramite> listarPorCliente(String clienteId) {
    return tramiteRepository.findByClienteId(clienteId);
}
```

---

## Próximos Pasos

✅ **G4F Completada:** Seguimiento de trámites

👉 **G5F - Dashboard y Navegación:** UI principal, menú, perfil de usuario y cierre del Ciclo 1
