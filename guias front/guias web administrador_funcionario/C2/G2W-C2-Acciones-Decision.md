# Guía 2W-C2 — Toma de Decisiones y Acciones sobre Expediente (CU-11, CU-16, CU-17, CU-18)

**Ciclo 2 · Sistema de Gestión de Trámites - Frontend Web (Administrador / Funcionario)**

> 🎯 **Objetivo:** Implementar el panel de control al final del Expediente Digital. Permitir al Funcionario registrar sus observaciones (CU-16), tomar decisiones para avanzar el workflow (CU-18), devolver el trámite para corrección (CU-17) o reasignarlo a otro compañero (CU-11).

---

## 0. Requisitos

✅ La Guía 1W-C2 (Bandeja de Entrada y Visor de Expediente) debe estar completada y funcional.
✅ Backend G3-C2 implementado: `TramiteDecisionController` en `/api/tramites/{id}/derivar`, `/devolver`, `/decision-final`.
✅ Formularios reactivos (o ngModel en Angular) importados en el módulo.

---

## 1. Actualización del Servicio de Trámites

En `tramite.service.ts` añade o reemplaza los métodos de decisión:

```typescript
  // CU-18: Veredicto formal — Aprobar o Rechazar
  // Endpoint: POST /api/tramites/{id}/decision-final
  // Body: DecisionFinalRequest { decision: "Aprobar" | "Rechazar", justificacion }
  decisionFinal(tramiteId: string, decision: string, justificacion: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tramites/${tramiteId}/decision-final`, {
      decision: decision,       // "Aprobar" o "Rechazar" (tal como espera el backend)
      justificacion: justificacion
    });
  }

  // CU-17: Devolver trámite a un nodo anterior con observaciones obligatorias
  // Endpoint: POST /api/tramites/{id}/devolver
  // Body: DevolverTramiteRequest { nodoDestinoId, observaciones }
  devolverTramite(tramiteId: string, nodoDestinoId: string, observaciones: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tramites/${tramiteId}/devolver`, {
      nodoDestinoId: nodoDestinoId,   // ID del nodo al que se retrocede (obligatorio)
      observaciones: observaciones
    });
  }

  // CU-11: Derivar (reasignar) a otro funcionario dentro del mismo departamento
  // Endpoint: POST /api/tramites/{id}/derivar
  // Body: DerivarTramiteRequest { nuevoFuncionarioId, motivo }
  derivarTramite(tramiteId: string, nuevoFuncionarioId: string, motivo: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/tramites/${tramiteId}/derivar`, {
      nuevoFuncionarioId: nuevoFuncionarioId,  // campo correcto según DerivarTramiteRequest
      motivo: motivo                           // campo correcto (no "justificacion")
    });
  }

  // Auxiliar para CU-11: Listar todos los usuarios (filtramos FUNCIONARIO en el componente)
  // GET /api/usuarios requiere rol ADMINISTRADOR; un funcionario necesita que su admin lo exponga.
  getFuncionariosDisponibles(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/usuarios`);
  }

  // Auxiliar para CU-17: Obtener el expediente para extraer nodos anteriores (secciones completadas)
  getExpediente(tramiteId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/expedientes/tramite/${tramiteId}`);
  }
```

---

## 2. Panel de Acciones en el Expediente (HTML)

Añade este bloque al final de `expediente-digital.component.html`, reemplazando el botón "Siguiente Paso (Próxima Guía)":

```html
<hr class="mt-5">

<!-- Sección CU-16: Registrar Informe / Justificación (campo común para todas las acciones) -->
<div class="card border-primary mb-4 shadow-sm">
  <div class="card-header bg-primary text-white">
    <h5 class="mb-0">📝 Informe y Resolución (Paso Actual)</h5>
  </div>
  <div class="card-body">

    <div class="mb-3">
      <label class="form-label fw-bold">Justificación / Observaciones (Obligatorio):</label>
      <textarea class="form-control" rows="3" [(ngModel)]="justificacion"
        placeholder="Escriba las conclusiones, problemas hallados o sustentación de su decisión...">
      </textarea>
    </div>

    <!-- CU-17: Selector de nodo destino (visible solo al elegir "Devolver") -->
    <div class="mb-3 p-3 bg-warning-subtle border rounded"
         *ngIf="accionSeleccionada === 'DEVOLVER'">
      <label class="form-label fw-bold">Devolver a la sección:</label>
      <select class="form-select" [(ngModel)]="nodoDestinoId">
        <option value="" disabled>-- Seleccione a dónde retroceder --</option>
        <!-- Secciones completadas del expediente (nodos anteriores válidos) -->
        <option *ngFor="let s of seccionesAnteriores"
                [value]="s.infoSeccion.nodoId">
          Sección {{ s.infoSeccion.ordenSeccion }} — Depto: {{ s.infoSeccion.departamentoId }}
        </option>
      </select>
    </div>

    <!-- CU-11: Selector de compañero (visible solo al elegir "Derivar") -->
    <div class="mb-3 p-3 bg-light border rounded"
         *ngIf="accionSeleccionada === 'DERIVAR'">
      <label class="form-label fw-bold">Seleccione al nuevo responsable:</label>
      <select class="form-select" [(ngModel)]="funcionarioDestinoId">
        <option value="" disabled>-- Seleccione un compañero --</option>
        <option *ngFor="let f of listaFuncionarios" [value]="f.id">
          {{ f.nombre }} ({{ f.email }})
        </option>
      </select>
    </div>

    <!-- Botonera Principal de Decisiones (CU-18, CU-17, CU-11) -->
    <div class="d-flex flex-wrap gap-2 mt-4">

      <!-- CU-18: Aprobar (avanza flujo positivo vía /decision-final) -->
      <button class="btn btn-success"
              [disabled]="procesando || !justificacion"
              (click)="ejecutarAccion('APROBAR')">
        ✅ Aprobar (Avanzar)
      </button>

      <!-- CU-18: Rechazar (finaliza flujo negativamente vía /decision-final) -->
      <button class="btn btn-danger"
              [disabled]="procesando || !justificacion"
              (click)="ejecutarAccion('RECHAZAR')">
        ❌ Rechazar Definitivo
      </button>

      <!-- CU-17: Devolver a Corregir -->
      <button class="btn btn-warning"
              (click)="prepararDevolucion()">
        ⚠️ Devolver a Corregir
      </button>
      <button class="btn btn-warning"
              *ngIf="accionSeleccionada === 'DEVOLVER'"
              [disabled]="procesando || !justificacion || !nodoDestinoId"
              (click)="ejecutarAccion('DEVOLVER')">
        Confirmar Devolución
      </button>

      <!-- CU-11: Derivar a Compañero -->
      <button class="btn btn-info text-white"
              (click)="prepararDerivacion()">
        🔄 Derivar a Compañero
      </button>
      <button class="btn btn-info text-white"
              *ngIf="accionSeleccionada === 'DERIVAR'"
              [disabled]="procesando || !funcionarioDestinoId || !justificacion"
              (click)="ejecutarAccion('DERIVAR')">
        Confirmar Derivación
      </button>

    </div>
  </div>
</div>
```

---

## 3. Lógica del Componente

En `expediente-digital.component.ts` agrega las variables y métodos de decisión:

```typescript
  // Variables de decisión
  justificacion: string = '';
  accionSeleccionada: string = '';
  procesando: boolean = false;

  // CU-17: nodo destino y secciones anteriores para el select
  nodoDestinoId: string = '';
  seccionesAnteriores: any[] = [];

  // CU-11: lista de funcionarios y funcionario seleccionado
  listaFuncionarios: any[] = [];
  funcionarioDestinoId: string = '';

  // ... (ngOnInit y métodos de G1W-C2 se mantienen) ...

  prepararDevolucion(): void {
    this.accionSeleccionada = 'DEVOLVER';
    this.nodoDestinoId = '';
    // Cargar secciones completadas del expediente para mostrar como opciones de retroceso
    if (this.seccionesAnteriores.length === 0) {
      this.tramiteService.getExpediente(this.tramiteId).subscribe({
        next: (data) => {
          // Solo secciones ya completadas son destinos válidos para devolver
          this.seccionesAnteriores = (data.secciones || [])
            .filter((s: any) => s.infoSeccion.estado === 'completada');
        },
        error: () => console.error('No se pudieron cargar las secciones anteriores')
      });
    }
  }

  prepararDerivacion(): void {
    this.accionSeleccionada = 'DERIVAR';
    this.funcionarioDestinoId = '';
    if (this.listaFuncionarios.length === 0) {
      this.tramiteService.getFuncionariosDisponibles().subscribe({
        next: (data) => {
          // Filtrar solo funcionarios usando el campo "tipo" real del modelo Usuario
          // (Usuario NO tiene campo "roles" — tiene "tipo": "funcionario"|"administrador"|"cliente")
          this.listaFuncionarios = data.filter((u: any) => u.tipo === 'funcionario');
        },
        error: () => console.error('No se pudieron cargar los funcionarios')
      });
    }
  }

  ejecutarAccion(tipo: string): void {
    if (!confirm(`¿Está seguro de proceder con la acción: ${tipo}?`)) return;

    this.procesando = true;

    if (tipo === 'APROBAR') {
      // CU-18: Aprobar — usa /decision-final con decision="Aprobar"
      this.tramiteService.decisionFinal(this.tramiteId, 'Aprobar', this.justificacion).subscribe({
        next: () => this.finalizarExitosamente('Trámite aprobado. Ha avanzado al siguiente departamento.'),
        error: (err) => this.manejarError(err)
      });

    } else if (tipo === 'RECHAZAR') {
      // CU-18: Rechazar — usa /decision-final con decision="Rechazar"
      this.tramiteService.decisionFinal(this.tramiteId, 'Rechazar', this.justificacion).subscribe({
        next: () => this.finalizarExitosamente('Trámite rechazado y cerrado.'),
        error: (err) => this.manejarError(err)
      });

    } else if (tipo === 'DEVOLVER') {
      // CU-17: Devolver — requiere nodoDestinoId y observaciones
      this.tramiteService.devolverTramite(this.tramiteId, this.nodoDestinoId, this.justificacion).subscribe({
        next: () => this.finalizarExitosamente('Trámite devuelto para corrección.'),
        error: (err) => this.manejarError(err)
      });

    } else if (tipo === 'DERIVAR') {
      // CU-11: Derivar — requiere nuevoFuncionarioId y motivo
      this.tramiteService.derivarTramite(this.tramiteId, this.funcionarioDestinoId, this.justificacion).subscribe({
        next: () => this.finalizarExitosamente('Trámite reasignado al compañero.'),
        error: (err) => this.manejarError(err)
      });
    }
  }

  finalizarExitosamente(mensaje: string): void {
    alert(mensaje);
    this.procesando = false;
    this.router.navigate(['/bandeja']);
  }

  manejarError(err: any): void {
    console.error(err);
    alert('Error al procesar la acción: ' + (err.error?.message || err.message || 'revise la consola'));
    this.procesando = false;
  }
```

---

## 4. Validación Final

1. Entra a la Bandeja y abre un **Expediente**.
2. Desplázate al fondo de la vista.
3. **Aprobar** → envía `POST /api/tramites/{id}/decision-final` con `{ decision: "Aprobar", justificacion }`. El motor avanza al siguiente nodo.
4. **Rechazar** → envía `POST /api/tramites/{id}/decision-final` con `{ decision: "Rechazar", justificacion }`. El trámite cierra con estado `Rechazado`.
5. **Devolver a Corregir** → muestra un `select` con las secciones completadas del expediente. Al confirmar envía `POST /api/tramites/{id}/devolver` con `{ nodoDestinoId, observaciones }`.
6. **Derivar a Compañero** → muestra un `select` con funcionarios disponibles. Al confirmar envía `POST /api/tramites/{id}/derivar` con `{ nuevoFuncionarioId, motivo }`.
