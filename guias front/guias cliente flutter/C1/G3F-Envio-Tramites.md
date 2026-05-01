# Guía 3F — Envío y Creación de Trámites

**Ciclo 1 · Sistema de Gestión de Trámites - Frontend**

> 🎯 **Objetivo:** El cliente completa un formulario dinámico basado en la política del trámite y lo envía al sistema. El backend inicia el motor de workflow.

---

## 0. Requisitos

✅ Completadas las Guías 1F y 2F
✅ Backend sirviendo:
  - `GET /api/politicas/{id}` (obtener formulario plantilla)
  - `POST /api/workflow/iniciar` (crear nuevo trámite)
  - `GET /api/actividades` (para obtener campos del formulario)

---

## 1. Modelos TypeScript

### formulario.model.ts (nuevo)
```typescript
export interface CampoFormulario {
  id: string;
  nombre: string;
  tipo: string;  // text, email, tel, date, textarea, select, file, checkbox
  etiqueta: string;
  placeholder?: string;
  requerido: boolean;
  validaciones?: {
    minLength?: number;
    maxLength?: number;
    pattern?: string;
  };
  opciones?: Array<{ label: string; value: string }>;  // Para select
}

export interface FormularioPlantilla {
  id: string;
  nombre: string;
  descripcion?: string;
  campos: CampoFormulario[];
  version: number;
}

export interface SolicitudTramite {
  politicaId: string;
  datos: { [key: string]: any };
  archivos?: File[];
}

export interface RespuestaTramite {
  tramiteId: string;
  codigo: string;
  estado: string;
  mensaje: string;
}
```

### expediente.model.ts (nuevo)
```typescript
export interface ExpedienteDigital {
  id: string;
  codigo: string;
  clienteId: string;
  politicaId: string;
  tramiteId: string;
  secciones: SeccionExpediente[];
  estado: string;
  fechaCreacion: string;
}

export interface SeccionExpediente {
  id: string;
  nombre: string;
  estado: string;  // bloqueada, en_curso, completada
  campos: any;
}
```

---

## 2. Servicio para Envío de Trámites

### tramites-envio.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SolicitudTramite, RespuestaTramite } from '../models/formulario.model';
import { FormularioPlantilla } from '../models/formulario.model';

@Injectable({
  providedIn: 'root'
})
export class TramitesEnvioService {

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  /**
   * Obtener plantilla del formulario para una política
   */
  obtenerFormularioPlantilla(politicaId: string): Observable<FormularioPlantilla> {
    // En ciclo 1 es una plantilla básica
    // En ciclo 2 se puede ser más dinámico
    return new Observable(observer => {
      // Simulación: en ciclo 2 vendría del backend
      const plantilla: FormularioPlantilla = {
        id: 'form-' + politicaId,
        nombre: 'Formulario de Solicitud',
        descripcion: 'Por favor completa todos los campos requeridos',
        version: 1,
        campos: [
          {
            id: 'nombre_completo',
            nombre: 'nombre_completo',
            tipo: 'text',
            etiqueta: 'Nombre Completo',
            placeholder: 'Ej: Juan Pérez',
            requerido: true,
            validaciones: { minLength: 3, maxLength: 100 }
          },
          {
            id: 'cedula',
            nombre: 'cedula',
            tipo: 'text',
            etiqueta: 'Número de Cédula',
            placeholder: 'Ej: 1234567890',
            requerido: true,
            validaciones: { pattern: '^[0-9]{7,10}$' }
          },
          {
            id: 'email',
            nombre: 'email',
            tipo: 'email',
            etiqueta: 'Correo Electrónico',
            placeholder: 'Ej: correo@ejemplo.com',
            requerido: true
          },
          {
            id: 'telefono',
            nombre: 'telefono',
            tipo: 'tel',
            etiqueta: 'Teléfono de Contacto',
            placeholder: 'Ej: 3001234567',
            requerido: true
          },
          {
            id: 'descripcion_solicitud',
            nombre: 'descripcion_solicitud',
            tipo: 'textarea',
            etiqueta: 'Descripción de la Solicitud',
            placeholder: 'Describe detalladamente qué necesitas...',
            requerido: true,
            validaciones: { minLength: 10, maxLength: 1000 }
          },
          {
            id: 'archivo_soporte',
            nombre: 'archivo_soporte',
            tipo: 'file',
            etiqueta: 'Archivo de Soporte (PDF, JPG, PNG)',
            requerido: false
          }
        ]
      };
      observer.next(plantilla);
      observer.complete();
    });
  }

  /**
   * Enviar trámite al backend
   * 
   * NOTA C1: Solo envía JSON con politicaId y clienteId.
   * Archivos y datos adicionales se agregan en Ciclo 2 (G2-C2).
   * El backend inicia el trámite y lo posiciona en el primer nodo.
   */
  enviarTramite(solicitud: SolicitudTramite, clienteId: string): Observable<RespuestaTramite> {
    // En C1, solo enviamos lo esencial para iniciar el flujo
    return this.http.post<RespuestaTramite>(
      `${this.apiUrl}/workflow/iniciar`,
      {
        clienteId: clienteId,
        politicaId: solicitud.politicaId,
        prioridad: 3  // Por defecto normal
      }
    );
  }

  /**
   * Guardar borrador (Ciclo 2)
   * 
   * TODO: Implementar en Ciclo 2 cuando exista endpoint de borradores
   */
  guardarBorrador(politicaId: string, datos: any): Observable<any> {
    // COMENTADO: No existe en C1
    // return this.http.post(`${this.apiUrl}/borradores`, { politicaId, datos });
    return new Observable(observer => {
      observer.error('Funcionalidad disponible en Ciclo 2');
    });
  }
}
```

---

## 3. Componente Formulario de Envío

### tramites-formulario.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { TramitesService } from '../../services/tramites.service';
import { TramitesEnvioService } from '../../services/tramites-envio.service';
import { PoliticaNegocio } from '../../models/politica.model';
import { FormularioPlantilla, CampoFormulario, SolicitudTramite } from '../../models/formulario.model';

@Component({
  selector: 'app-tramites-formulario',
  templateUrl: './tramites-formulario.component.html',
  styleUrls: ['./tramites-formulario.component.css']
})
export class TramitesFormularioComponent implements OnInit {

  politica: PoliticaNegocio | null = null;
  formulario: FormularioPlantilla | null = null;
  form!: FormGroup;

  politicaId: string = '';
  loading = false;
  enviando = false;
  submitted = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  // Para subida de archivos
  archivosSeleccionados: File[] = [];
  extensionesPermitidas = ['pdf', 'jpg', 'jpeg', 'png', 'doc', 'docx'];
  tamanoMaximoMB = 5;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private formBuilder: FormBuilder,
    private tramitesService: TramitesService,
    private tramitesEnvioService: TramitesEnvioService
  ) { }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.politicaId = params['id'];
      if (this.politicaId) {
        this.cargarDatos();
      }
    });
  }

  /**
   * Cargar política y formulario
   */
  cargarDatos(): void {
    this.loading = true;
    this.errorMessage = null;

    // Cargar política
    this.tramitesService.obtenerPoliticaPorId(this.politicaId).subscribe({
      next: (politica) => {
        this.politica = politica;
        // Cargar formulario
        this.cargarFormulario();
      },
      error: (error) => {
        this.errorMessage = 'Error al cargar la política';
        this.loading = false;
      }
    });
  }

  /**
   * Cargar formulario plantilla
   */
  cargarFormulario(): void {
    this.tramitesEnvioService.obtenerFormularioPlantilla(this.politicaId).subscribe({
      next: (formulario) => {
        this.formulario = formulario;
        this.construirFormulario();
        this.loading = false;
      },
      error: (error) => {
        this.errorMessage = 'Error al cargar el formulario';
        this.loading = false;
      }
    });
  }

  /**
   * Construir FormGroup dinámicamente basado en campos
   */
  construirFormulario(): void {
    const group: { [key: string]: any } = {};

    if (this.formulario && this.formulario.campos) {
      this.formulario.campos.forEach(campo => {
        const validators = [];

        // Requerido
        if (campo.requerido) {
          validators.push(Validators.required);
        }

        // Min/Max Length
        if (campo.validaciones?.minLength) {
          validators.push(Validators.minLength(campo.validaciones.minLength));
        }
        if (campo.validaciones?.maxLength) {
          validators.push(Validators.maxLength(campo.validaciones.maxLength));
        }

        // Pattern
        if (campo.validaciones?.pattern) {
          validators.push(Validators.pattern(campo.validaciones.pattern));
        }

        // Email
        if (campo.tipo === 'email') {
          validators.push(Validators.email);
        }

        group[campo.nombre] = ['', validators];
      });
    }

    this.form = this.formBuilder.group(group);
  }

  /**
   * Obtener controles del formulario
   */
  get controles() {
    return this.form ? this.form.controls : {};
  }

  /**
   * Manejar selección de archivos
   */
  onArchivoSeleccionado(event: any, campo: CampoFormulario): void {
    const files = event.target.files;
    if (files && files.length > 0) {
      const archivo = files[0];

      // Validar extensión
      const ext = archivo.name.split('.').pop()?.toLowerCase();
      if (!ext || !this.extensionesPermitidas.includes(ext)) {
        this.errorMessage = `Formato no permitido. Permitidos: ${this.extensionesPermitidas.join(', ')}`;
        return;
      }

      // Validar tamaño
      const sizeMB = archivo.size / (1024 * 1024);
      if (sizeMB > this.tamanoMaximoMB) {
        this.errorMessage = `El archivo no debe superar ${this.tamanoMaximoMB}MB`;
        return;
      }

      this.archivosSeleccionados.push(archivo);
      this.errorMessage = null;
    }
  }

  /**
   * Remover archivo seleccionado
   */
  removerArchivo(index: number): void {
    this.archivosSeleccionados.splice(index, 1);
  }

  /**
   * Enviar formulario
   * 
   * En C1: Solo inicia el trámite sin datos ni archivos.
   * Los datos se cargan en Ciclo 2 a través de las secciones.
   */
  onSubmit(): void {
    this.submitted = true;
    this.errorMessage = null;
    this.successMessage = null;

    // En C1, no validamos el formulario dinámico
    // Solo iniciamos el trámite
    this.enviando = true;

    const solicitud: SolicitudTramite = {
      politicaId: this.politicaId,
      datos: {},  // Vacío en C1
      archivos: []
    };

    const usuarioId = this.authService.getUsuarioActual()?.id || 'unknown';

    this.tramitesEnvioService.enviarTramite(solicitud, usuarioId).subscribe({
      next: (respuesta) => {
        this.successMessage = `✓ Trámite enviado exitosamente. Código: ${respuesta.codigo}`;
        this.enviando = false;
        
        // Redirigir a mis trámites
        setTimeout(() => {
          this.router.navigate(['/mis-tramites']);
        }, 2000);
      },
      error: (error) => {
        this.errorMessage = error.error?.message || 'Error al enviar el trámite';
        this.enviando = false;
      }
    });
  }

  /**
   * Cancelar y volver
   */
  cancelar(): void {
    this.router.navigate(['/tramites', this.politicaId]);
  }

  /**
   * Guardar como borrador (Ciclo 2)
   * 
   * TODO: Implementar en Ciclo 2
   * 
   * COMENTADO: El endpoint /api/borradores no existe en C1
   */
  /*
  guardarBorrador(): void {
    if (!this.form.valid) {
      this.errorMessage = 'Completa al menos los campos obligatorios';
      return;
    }

    this.tramitesEnvioService.guardarBorrador(this.politicaId, this.form.value).subscribe({
      next: () => {
        this.successMessage = 'Borrador guardado exitosamente';
      },
      error: () => {
        this.errorMessage = 'Error al guardar el borrador';
      }
    });
  }
  */
      }
    });
  }
}
```

### tramites-formulario.component.html
```html
<div class="container mt-4">
  <!-- Header -->
  <div class="row mb-4">
    <div class="col-md-8">
      <button class="btn btn-outline-secondary mb-3" (click)="cancelar()">
        ← Volver
      </button>
      <h1>Iniciar Nuevo Trámite</h1>
      <p class="text-muted" *ngIf="politica">
        {{ politica.nombre }}
      </p>
    </div>
  </div>

  <!-- Loading -->
  <div *ngIf="loading" class="text-center mb-4">
    <div class="spinner-border" role="status">
      <span class="visually-hidden">Cargando formulario...</span>
    </div>
  </div>

  <!-- Errores y Mensajes -->
  <div *ngIf="errorMessage" class="alert alert-danger alert-dismissible fade show mb-4">
    {{ errorMessage }}
    <button type="button" class="btn-close" (click)="errorMessage = null"></button>
  </div>

  <div *ngIf="successMessage" class="alert alert-success alert-dismissible fade show mb-4">
    {{ successMessage }}
    <button type="button" class="btn-close" (click)="successMessage = null"></button>
  </div>

  <!-- Formulario -->
  <div *ngIf="!loading && formulario && form" class="row">
    <div class="col-md-8">
      <div class="card">
        <div class="card-header bg-primary text-white">
          <h5 class="mb-0">{{ formulario.nombre }}</h5>
        </div>

        <div class="card-body">
          <p class="text-muted">{{ formulario.descripcion }}</p>

          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <!-- Generar campos dinámicamente -->
            <div *ngFor="let campo of formulario.campos" class="mb-4">

              <!-- Campos de Texto, Email, Teléfono -->
              <ng-container *ngIf="['text', 'email', 'tel', 'date'].includes(campo.tipo)">
                <label [for]="campo.nombre" class="form-label">
                  {{ campo.etiqueta }}
                  <span *ngIf="campo.requerido" class="text-danger">*</span>
                </label>
                <input
                  [type]="campo.tipo"
                  class="form-control"
                  [id]="campo.nombre"
                  [formControlName]="campo.nombre"
                  [placeholder]="campo.placeholder || ''"
                  [ngClass]="{ 'is-invalid': submitted && controles[campo.nombre]?.errors }"
                >
                <div *ngIf="submitted && controles[campo.nombre]?.errors" class="invalid-feedback d-block">
                  <span *ngIf="controles[campo.nombre]?.errors['required']">Este campo es requerido</span>
                  <span *ngIf="controles[campo.nombre]?.errors['email']">Email inválido</span>
                  <span *ngIf="controles[campo.nombre]?.errors['minlength']">
                    Mínimo {{ campo.validaciones?.minLength }} caracteres
                  </span>
                  <span *ngIf="controles[campo.nombre]?.errors['maxlength']">
                    Máximo {{ campo.validaciones?.maxLength }} caracteres
                  </span>
                  <span *ngIf="controles[campo.nombre]?.errors['pattern']">Formato inválido</span>
                </div>
              </ng-container>

              <!-- TextArea -->
              <ng-container *ngIf="campo.tipo === 'textarea'">
                <label [for]="campo.nombre" class="form-label">
                  {{ campo.etiqueta }}
                  <span *ngIf="campo.requerido" class="text-danger">*</span>
                </label>
                <textarea
                  class="form-control"
                  [id]="campo.nombre"
                  [formControlName]="campo.nombre"
                  [placeholder]="campo.placeholder || ''"
                  rows="4"
                  [ngClass]="{ 'is-invalid': submitted && controles[campo.nombre]?.errors }"
                ></textarea>
                <div *ngIf="submitted && controles[campo.nombre]?.errors" class="invalid-feedback d-block">
                  <span *ngIf="controles[campo.nombre]?.errors['required']">Este campo es requerido</span>
                  <span *ngIf="controles[campo.nombre]?.errors['minlength']">
                    Mínimo {{ campo.validaciones?.minLength }} caracteres
                  </span>
                </div>
              </ng-container>

              <!-- Select -->
              <ng-container *ngIf="campo.tipo === 'select'">
                <label [for]="campo.nombre" class="form-label">
                  {{ campo.etiqueta }}
                  <span *ngIf="campo.requerido" class="text-danger">*</span>
                </label>
                <select
                  class="form-select"
                  [id]="campo.nombre"
                  [formControlName]="campo.nombre"
                  [ngClass]="{ 'is-invalid': submitted && controles[campo.nombre]?.errors }"
                >
                  <option value="">Selecciona una opción</option>
                  <option *ngFor="let opt of campo.opciones" [value]="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </ng-container>

              <!-- File -->
              <ng-container *ngIf="campo.tipo === 'file'">
                <label [for]="campo.nombre" class="form-label">
                  {{ campo.etiqueta }}
                  <span *ngIf="campo.requerido" class="text-danger">*</span>
                </label>
                <input
                  type="file"
                  class="form-control"
                  [id]="campo.nombre"
                  (change)="onArchivoSeleccionado($event, campo)"
                  accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
                  [ngClass]="{ 'is-invalid': submitted && controles[campo.nombre]?.errors }"
                >
                <small class="form-text text-muted">
                  Formatos permitidos: PDF, JPG, PNG, DOC, DOCX (máximo 5MB)
                </small>
              </ng-container>

              <!-- Checkbox -->
              <ng-container *ngIf="campo.tipo === 'checkbox'">
                <div class="form-check">
                  <input
                    type="checkbox"
                    class="form-check-input"
                    [id]="campo.nombre"
                    [formControlName]="campo.nombre"
                  >
                  <label class="form-check-label" [for]="campo.nombre">
                    {{ campo.etiqueta }}
                    <span *ngIf="campo.requerido" class="text-danger">*</span>
                  </label>
                </div>
              </ng-container>

            </div>

            <!-- Archivos Seleccionados -->
            <div *ngIf="archivosSeleccionados.length > 0" class="alert alert-info mb-4">
              <h6>Archivos Seleccionados:</h6>
              <ul class="mb-0">
                <li *ngFor="let archivo of archivosSeleccionados; let i = index">
                  {{ archivo.name }}
                  <button
                    type="button"
                    class="btn btn-sm btn-link text-danger"
                    (click)="removerArchivo(i)"
                  >
                    Remover
                  </button>
                </li>
              </ul>
            </div>

            <!-- Botones -->
            <div class="d-flex gap-2">
              <button
                type="submit"
                class="btn btn-primary btn-lg flex-grow-1"
                [disabled]="enviando"
              >
                <span *ngIf="!enviando">✓ Enviar Trámite</span>
                <span *ngIf="enviando">
                  <span class="spinner-border spinner-border-sm me-2"></span>
                  Enviando...
                </span>
              </button>

              <button
                type="button"
                class="btn btn-outline-warning"
                (click)="guardarBorrador()"
                [disabled]="enviando"
              >
                💾 Guardar Borrador
              </button>

              <button
                type="button"
                class="btn btn-outline-secondary"
                (click)="cancelar()"
                [disabled]="enviando"
              >
                ✕ Cancelar
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Barra Lateral - Ayuda -->
    <div class="col-md-4">
      <div class="card bg-light mb-3">
        <div class="card-body">
          <h5>ℹ️ Información Importante</h5>
          <ul class="small text-muted">
            <li>Todos los campos marcados con * son obligatorios</li>
            <li>Verifica que la información sea correcta antes de enviar</li>
            <li>Recibirás un código de trámite para seguimiento</li>
            <li>El proceso puede durar varios días según la política</li>
          </ul>
        </div>
      </div>

      <div class="card bg-light">
        <div class="card-body">
          <h5>📋 Checklist</h5>
          <div class="form-check small">
            <input type="checkbox" class="form-check-input" id="check1">
            <label class="form-check-label" for="check1">
              Información completada correctamente
            </label>
          </div>
          <div class="form-check small">
            <input type="checkbox" class="form-check-input" id="check2">
            <label class="form-check-label" for="check2">
              Documentos adjuntos y legibles
            </label>
          </div>
          <div class="form-check small">
            <input type="checkbox" class="form-check-input" id="check3">
            <label class="form-check-label" for="check3">
              He leído y acepto los términos
            </label>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
```

---

## 4. Actualizar Rutas

```typescript
// Agregar a app-routing.module.ts
{
  path: 'tramites/:id/nuevo',
  component: TramitesFormularioComponent,
  canActivate: [AuthGuard]
}
```

---

## 5. Actualizar app.module.ts

Agregar `TramitesFormularioComponent` a declarations y servicios.

---

## Checklist de Implementación

- [ ] Crear modelos: formulario.model.ts, expediente.model.ts
- [ ] Crear servicio: tramites-envio.service.ts
- [ ] Crear componente: tramites-formulario.component.ts + .html + .css
- [ ] Actualizar rutas
- [ ] Actualizar app.module.ts
- [ ] Probar envío de un trámite
- [ ] Verificar validaciones del formulario
- [ ] Verificar subida de archivos
- [ ] Probar mensaje de éxito

---

## Endpoints Backend Utilizados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/workflow/iniciar` | Crear y enviar nuevo trámite |

---

## Próximos Pasos

✅ **G3F Completada:** Envío de trámites

👉 **G4F - Seguimiento de Trámites:** Ver estado, historial y actividades pendientes
