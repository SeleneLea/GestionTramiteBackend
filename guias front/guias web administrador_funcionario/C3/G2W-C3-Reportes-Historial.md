# Guía 2W-C3 — Historial Auditoría y Generación de Reportes (CU-29, CU-26)

**Ciclo 3 · Sistema de Gestión de Trámites - Frontend Web (Administrador)**

> 🎯 **Objetivo:** Proporcionar al Administrador una visión consolidada y auditable de todos los trámites para su control de gestión (CU-29), y facilitar la extracción de dicha información en formatos visuales (PDF, Excel) para la evaluación de objetivos (CU-26).

---

## 0. Requisitos

✅ Componentes HTTP web (`HttpClientModule` en Angular) habilitados para manejar respuestas binarias (`Blob` / `arraybuffer`) al descargar archivos.
✅ Backend corriendo con acceso a los endpoints `/api/tramites/historial` y `/api/reportes/`.
✅ Rol de **Administrador** (los funcionarios comunes no deben poder ver todo el historial de la empresa ni emitir reportes globales).

---

## 1. Actualización de Servicios (API)

Añadimos funciones para buscar en el historial con filtros, y para manejar los archivos devueltos por el sistema.

Crea o actualiza `reportes.service.ts`:

```typescript
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ReportesService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // CU-29: Obtener historial con filtros (Solo Administradores)
  getHistorialTramites(estado?: string, fechaDesde?: string, fechaHasta?: string): Observable<any[]> {
    let params = new HttpParams();
    if (estado) params = params.set('estado', estado);
    if (fechaDesde) params = params.set('desde', fechaDesde);
    if (fechaHasta) params = params.set('hasta', fechaHasta);

    return this.http.get<any[]>(`${this.baseUrl}/tramites/historial`, { params });
  }

  // CU-26: Generar reporte (PDF, Excel o CSV) — devuelve el ID del reporte creado
  // Endpoint real: POST /api/reportes/generar  (backend G3-C3)
  // Body: { filtros, formato: 'PDF'|'EXCEL'|'CSV' }
  generarReporte(data: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/reportes/generar`, data);
  }

  // CU-26: Descargar archivo del reporte ya generado
  // Endpoint real: GET /api/reportes/{id}/descargar
  descargarReporte(reporteId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/reportes/${reporteId}/descargar`, {
      responseType: 'blob'
    });
  }
}
```

---

## 2. Pantalla: Historial y Buscador (CU-29)

Construimos un componente para buscar cualquier trámite del sistema, filtrarlo y enviarlo al servicio de reportes exportables.

**`historial-tramites.component.ts`**:

```typescript
import { Component, OnInit } from '@angular/core';
import { ReportesService } from '../../services/reportes.service';

@Component({
  selector: 'app-historial-tramites',
  templateUrl: './historial-tramites.component.html'
})
export class HistorialTramitesComponent implements OnInit {
  tramites: any[] = [];
  cargando: boolean = false;
  
  // Filtros
  filtroEstado: string = '';
  filtroDesde: string = '';
  filtroHasta: string = '';

  constructor(private reportesService: ReportesService) {}

  ngOnInit(): void {
    this.buscarHistorial();
  }

  buscarHistorial(): void {
    this.cargando = true;
    this.reportesService.getHistorialTramites(this.filtroEstado, this.filtroDesde, this.filtroHasta)
      .subscribe({
        next: (data) => {
          this.tramites = data;
          this.cargando = false;
        },
        error: (err) => {
          console.error(err);
          this.cargando = false;
        }
      });
  }

  // CU-26: Lógica para forzar la descarga de Archivos en el Navegador
  // CU-26: Flujo en dos pasos: 1) generar reporte → obtener ID, 2) descargar por ID
  exportar(formato: 'PDF' | 'EXCEL' | 'CSV'): void {
    const requestData = {
      filtros: { estado: this.filtroEstado, desde: this.filtroDesde, hasta: this.filtroHasta },
      formato: formato
    };

    // Paso 1: solicitar generación del reporte
    this.reportesService.generarReporte(requestData).subscribe({
      next: (reporte) => {
        // Paso 2: descargar el archivo usando el ID devuelto
        this.reportesService.descargarReporte(reporte.id).subscribe({
          next: (blob) => {
            const ext = formato === 'PDF' ? 'pdf' : formato === 'EXCEL' ? 'xlsx' : 'csv';
            this.triggerDownload(blob, `Reporte_Tramites.${ext}`);
          },
          error: (err) => console.error('Error al descargar:', err)
        });
      },
      error: (err) => console.error('Error al generar reporte:', err)
    });
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }
}
```

---

## 3. Plantilla Visual (HTML)

**`historial-tramites.component.html`**:

```html
<div class="container mt-4">
  <h2 class="mb-4"><i class="bi bi-journal-text"></i> Historial y Auditoría Global</h2>

  <!-- Panel de Filtros -->
  <div class="card shadow-sm mb-4 bg-light">
    <div class="card-body">
      <form class="row g-3 align-items-end" (ngSubmit)="buscarHistorial()">
        
        <div class="col-md-3">
          <label class="form-label fw-bold">Estado</label>
          <!-- Valores reales de estadoActual en el modelo Tramite -->
          <select class="form-select" name="estado" [(ngModel)]="filtroEstado">
            <option value="">TODOS</option>
            <option value="Aprobado">Aprobados</option>
            <option value="En proceso">En proceso</option>
            <option value="Observado">Observados / Devueltos</option>
            <option value="Cancelado por el usuario">Cancelados</option>
            <option value="Rechazado">Rechazados</option>
            <option value="Nuevo">Nuevos</option>
            <option value="Derivado">Derivados</option>
          </select>
        </div>

        <div class="col-md-3">
          <label class="form-label fw-bold">Desde (Inicio)</label>
          <input type="date" class="form-control" name="desde" [(ngModel)]="filtroDesde">
        </div>

        <div class="col-md-3">
          <label class="form-label fw-bold">Hasta</label>
          <input type="date" class="form-control" name="hasta" [(ngModel)]="filtroHasta">
        </div>

        <div class="col-md-3 d-flex gap-2">
          <button type="submit" class="btn btn-primary w-100"><i class="bi bi-search"></i> Buscar</button>
          <button type="button" class="btn btn-outline-secondary w-50" (click)="filtroEstado=''; filtroDesde=''; filtroHasta=''; buscarHistorial()">Limpiar</button>
        </div>
      </form>
    </div>
  </div>

  <!-- Barra de Exportación (CU-26) -->
  <div class="d-flex justify-content-between mb-3">
    <h5>Resultados Encontrados: <strong>{{ tramites.length }}</strong></h5>
    
    <div class="btn-group">
      <button class="btn btn-outline-danger" [disabled]="tramites.length === 0" (click)="exportar('PDF')">
        <i class="bi bi-file-earmark-pdf-fill"></i> Exportar a PDF
      </button>
      <button class="btn btn-outline-success" [disabled]="tramites.length === 0" (click)="exportar('EXCEL')">
        <i class="bi bi-file-earmark-excel-fill"></i> Exportar a Excel
      </button>
    </div>
  </div>

  <!-- Tabla de Auditoría Continua -->
  <div class="table-responsive shadow-sm">
    <table class="table table-hover table-bordered bg-white">
      <thead class="table-dark">
        <tr>
          <th>Código</th>
          <th>Política (Trámite)</th>
          <th>Estado Global</th>
          <th>Inicio</th>
          <th>Última Actualización</th>
          <th>Usuario Inicial</th>
          <th>Ver Detalle</th>
        </tr>
      </thead>
      <tbody>
        <tr *ngIf="cargando">
          <td colspan="7" class="text-center py-4"><span class="spinner-border text-primary"></span> Consultando la base de datos...</td>
        </tr>
        
        <tr *ngIf="!cargando && tramites.length === 0">
          <td colspan="7" class="text-center text-muted py-4">No se encontraron trámites para los filtros seleccionados.</td>
        </tr>

        <tr *ngFor="let t of tramites">
          <td><strong>{{ t.codigo }}</strong></td>
          <!-- politicaId es el campo real del modelo Tramite (no politicaNombre) -->
          <td>{{ t.politicaId ?? 'No especificada' }}</td>
          <td>
            <!-- Valores reales de estadoActual: Nuevo|En proceso|Derivado|Observado|Rechazado|Aprobado|Cancelado por el usuario -->
            <span class="badge"
                  [ngClass]="{
                    'bg-success':          t.estadoActual === 'Aprobado',
                    'bg-warning text-dark': t.estadoActual === 'Observado' || t.estadoActual === 'En proceso',
                    'bg-danger':           t.estadoActual === 'Rechazado' || t.estadoActual === 'Cancelado por el usuario',
                    'bg-info text-dark':   t.estadoActual === 'Nuevo' || t.estadoActual === 'Derivado'
                  }">
              {{ t.estadoActual }}
            </span>
          </td>
          <td>{{ t.fechaInicio | date:'dd/MM/yyyy HH:mm' }}</td>
          <!-- fechaCierreReal es el campo real del modelo (no fechaActualizacion) -->
          <td>{{ t.fechaCierreReal ? (t.fechaCierreReal | date:'dd/MM/yyyy HH:mm') : '—' }}</td>
          <!-- clienteId es el campo real del modelo (no solicitanteEmail) -->
          <td>{{ t.clienteId ?? 'Desconocido' }}</td>
          <td class="text-center">
            <button class="btn btn-sm btn-light" [routerLink]="['/tramites', t.id, 'expediente']">🔍</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</div>
```

---

## 4. Validando el Flujo

1. Accede al sistema web con perfil de **Administrador**.
2. Ingresa a la sección "Historial General" (CU-29).
3. Prueba elegir `Estado: Observado` y una fecha de ayer. Presiona *Buscar*. Observa la tabla filtrarse.
4. Presiona el botón verde de **Exportar a Excel**. El navegador decodificará el `Blob` binario que llega desde el backend (`POST /api/reportes/excel`), generará un enlace virtual temporal anclado al DOM y disparará la descarga del archivo `Reporte_Tramites.xlsx`. (Aplica exactamente igual para el botón rojo de PDF) (CU-26).