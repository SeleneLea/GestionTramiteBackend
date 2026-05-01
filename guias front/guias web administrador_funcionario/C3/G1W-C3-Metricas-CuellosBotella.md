# Guía 1W-C3 — Panel de Métricas y Cuellos de Botella (CU-24, CU-25)

**Ciclo 3 · Sistema de Gestión de Trámites - Frontend Web (Administrador)**

> 🎯 **Objetivo:** Proporcionar al Administrador un Tablero de Control (Dashboard) gráfico donde se visualicen los tiempos promedio calculados en cada etapa (CU-24) y donde resalten las alertas automáticas de anomalías o cuellos de botella detectados por el sistema / motor de IA (CU-25).

---

## 0. Requisitos

✅ Framework Web (Angular, React, Vue) configurado.
✅ Opcional pero recomendado: Una librería de gráficos como `ng2-charts` (Chart.js) instalada. Si no, usamos tablas visuales.
✅ Backend C3 encendido, con tareas programadas (`@Scheduled`) generando métricas y cuellos de botella en base al SLA.

---

## 1. Actualización de Servicios (API)

Añadimos los métodos en nuestro servicio para consultar los endpoints de métricas que creamos en el backend.

En `metricas.service.ts` (o similar):

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class MetricasService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // CU-24: Métricas individuales por trámite
  // Endpoint real: GET /api/metricas/tramite/{tramiteId}
  // Respuesta: List<MetricaTiempo> — campos: tramiteId, actividadId, departamentoId,
  //            tiempoSegundos, superoSla, fechaInicioActividad, fechaFinActividad
  getMetricasTramite(tramiteId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/metricas/tramite/${tramiteId}`);
  }

  // CU-25: Alertas de cuellos de botella (calculadas por @Scheduled en el backend)
  // Endpoint real: GET /api/metricas/cuellos-botella
  // Respuesta: List<CuelloBotella> — campos: id, actividadId, departamentoId,
  //            tramitesAcumulados, tiempoPromedio, tiempoEsperado, desviacionPorcentaje,
  //            causaSugerida, fechaDeteccion
  getCuellosDeBotella(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/metricas/cuellos-botella`);
  }
}
```

---

## 2. Pantalla 1: Tablero de Control de Rendimiento (Métricas)

Esta pantalla mostrará KPIs visuales de la eficiencia del sistema.

**`dashboard-metricas.component.ts`**:

```typescript
import { Component, OnInit } from '@angular/core';
import { MetricasService } from '../../services/metricas.service';

@Component({
  selector: 'app-dashboard-metricas',
  templateUrl: './dashboard-metricas.component.html',
  styleUrls: ['./dashboard-metricas.component.css']
})
export class DashboardMetricasComponent implements OnInit {
  metricas: any[] = [];
  cuellosDeBotella: any[] = [];
  cargando: boolean = true;

  constructor(private metricasService: MetricasService) {}

  ngOnInit(): void {
    this.cargarDatos();
  }

  cargarDatos(): void {
    this.cargando = true;

    // CU-25: cuellos de botella (endpoint general, no necesita tramiteId)
    this.metricasService.getCuellosDeBotella().subscribe({
      next: (data) => { this.cuellosDeBotella = data; },
      error: (err) => console.error('Error cuellos de botella:', err)
    });

    // CU-24: métricas de tiempo — el backend las expone por tramiteId.
    // Para el dashboard global se listan las métricas recientes via el historial de trámites
    // y se selecciona un tramiteId de muestra, o se deja vacío para mostrar solo los cuellos.
    // En producción, agregar un endpoint /api/metricas/resumen en el backend.
    this.cargando = false;
  }

  resolverCuello(id: string): void {
    // Si tu backend permite descartar o marcar como "Resuelta" la alerta
    alert(`Redirigiendo a analizar la alerta ID: ${id}...`);
  }
}
```

---

## 3. Plantilla Visual (HTML)

**`dashboard-metricas.component.html`**:

```html
<div class="container mt-4 mb-5">
  
  <div class="d-flex justify-content-between align-items-center mb-4">
    <h2>📊 Tablero de Rendimiento</h2>
    <span class="badge bg-secondary p-2">Actualizado en tiempo real</span>
  </div>

  <div *ngIf="cargando" class="text-center py-5">
    <div class="spinner-border text-primary" role="status"></div>
    <p class="mt-2 text-muted">Aguarde, calculando tiempos de atención...</p>
  </div>

  <div *ngIf="!cargando">
    
    <!-- Alertas Prioritarias (CU-25) -->
    <div class="card border-danger shadow-sm mb-4" *ngIf="cuellosDeBotella.length > 0">
      <div class="card-header bg-danger text-white">
        <h5 class="mb-0 "><i class="bi bi-exclamation-triangle-fill"></i> Alertas: Cuellos de Botella Detectados</h5>
      </div>
      <div class="card-body bg-light">
        <div class="row">
          <div class="col-md-6 mb-3" *ngFor="let cb of cuellosDeBotella">
            <div class="card h-100 border-warning">
              <div class="card-body">
                <!-- cb.actividadId es el campo real de CuelloBotella (no actividadAfectada) -->
                <h6 class="card-title text-danger">⚠️ Actividad: {{ cb.actividadId }}</h6>
                <!-- cb.tramitesAcumulados = cantidad de trámites detectados con SLA excedido -->
                <p class="card-text text-muted small"><strong>Trámites afectados:</strong> {{ cb.tramitesAcumulados }}</p>
                <!-- cb.departamentoId es el campo real (no cb.departamento) -->
                <p class="card-text text-muted small"><strong>Departamento:</strong> {{ cb.departamentoId }}</p>
                <p class="card-text">
                  Tiempo en el nodo sobrepasa el SLA estipulado.
                  <!-- cb.desviacionPorcentaje es el campo real (no factorDesviacion); es un porcentaje -->
                  (Excedido en: <strong class="text-danger">{{ cb.desviacionPorcentaje | number:'1.1-1' }}%</strong> sobre el SLA).
                </p>
                <p class="card-text text-muted small" *ngIf="cb.causaSugerida">
                  <em>{{ cb.causaSugerida }}</em>
                </p>
              </div>
              <div class="card-footer bg-white border-0">
                <button class="btn btn-outline-danger btn-sm w-100" (click)="resolverCuello(cb.id)">
                  Investigar Causa
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="alert alert-success mt-4" *ngIf="cuellosDeBotella.length === 0">
      <i class="bi bi-check-circle-fill"></i> El sistema opera dentro de los tiempos SLA esperados. No hay anomalías detectadas.
    </div>

    <!-- Registros de Tiempo por Actividad (CU-24) -->
    <!-- GET /api/metricas/tramite/{tramiteId} devuelve List<MetricaTiempo> con una fila por actividad completada -->
    <h4 class="mt-5 mb-3">Registros de Tiempo por Actividad</h4>
    <div class="card shadow-sm">
      <div class="card-body p-0">
        <table class="table table-hover table-striped m-0">
          <thead class="table-dark">
            <tr>
              <!-- m.actividadId es el campo real de MetricaTiempo (no actividadNombre) -->
              <th>ID Actividad</th>
              <!-- m.departamentoId es el campo real (no departamento) -->
              <th>Departamento</th>
              <!-- m.tiempoSegundos es el campo real; se convierte a horas en el template -->
              <th>Tiempo Real (h)</th>
              <!-- m.tramiteId identifica el trámite al que pertenece la medición -->
              <th>Trámite</th>
              <!-- m.superoSla es boolean real (no comparación con slaHoras) -->
              <th>Estatus SLA</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of metricas">
              <td><strong>{{ m.actividadId }}</strong></td>
              <td>{{ m.departamentoId }}</td>
              <td>{{ (m.tiempoSegundos / 3600) | number:'1.1-1' }} h</td>
              <td>{{ m.tramiteId }}</td>
              <td>
                <span class="badge bg-success" *ngIf="!m.superoSla">Dentro del SLA</span>
                <span class="badge bg-danger"  *ngIf="m.superoSla">SLA Excedido</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

  </div>
</div>
```

---

## 4. Validando el Flujo

1. Accede al Dashboard web con un usuario que posea el rol `ADMINISTRADOR`.
2. Observarás una llamada hacia `GET /api/metricas/cuellos-botella`. Si el algoritmo/SLA detectó algún Trámite que se haya demorado más de lo programado en una respectiva "Bandeja de Entrada", se generará automáticamente una tarjeta roja en la interfaz para que el administrador pueda intervenir y redistribuir la carga (vía reasignación manual).
3. Para ver métricas de un trámite individual carga `GET /api/metricas/tramite/{tramiteId}` y la tabla se poblará con los registros reales de `MetricaTiempo` (uno por actividad completada).