# Fase 2.6 · Componente data-table genérica

> Tabla genérica reutilizable con columnas configurables, paginación, búsqueda y acciones por fila. Reemplaza tablas hechas a mano en pantallas admin.

---

## 1. Objetivo

Una tabla parametrizable que se usa con la **misma API** en `usuarios`, `departamentos`, `políticas`, `actividades`, `historial`, etc. Recibe:
- Lista de filas (any[])
- Definición de columnas
- Acciones (botones por fila)
- Configuración de paginación

Y emite eventos: `accion`, `cambioPagina`, `busqueda`.

---

## 2. Estructura

```
ui-kit/data-table/
├── data-table.component.ts
├── data-table.component.html
├── data-table.component.scss
└── data-table.types.ts
```

---

## 3. Tipos públicos

### `data-table.types.ts`

```typescript
export interface ColumnaTabla<T = any> {
  /** Clave del campo en la fila (puede ser nested: 'usuario.nombre'). */
  key: string;
  /** Encabezado visible. */
  label: string;
  /** Ancho CSS opcional ('100px', '20%', etc.). */
  width?: string;
  /** Alineación. */
  align?: 'left' | 'center' | 'right';
  /** Función para transformar el valor antes de mostrarlo. */
  format?: (value: any, row: T) => string;
  /** Si la columna se puede ordenar. */
  sortable?: boolean;
  /** Tipo especial: 'badge' renderiza app-badge-estado, 'fecha' formatea, etc. */
  tipo?: 'texto' | 'badge' | 'fecha' | 'booleano' | 'progreso';
}

export interface AccionFila<T = any> {
  /** ID interno de la acción ('editar', 'borrar', 'ver', etc.). */
  id: string;
  /** Texto visible (o tooltip si solo hay icono). */
  label: string;
  /** Icono Bootstrap. */
  icono?: string;
  /** Variante de color. */
  variante?: 'primary' | 'danger' | 'secondary' | 'success';
  /** Función para deshabilitar condicionalmente. */
  deshabilitada?: (row: T) => boolean;
  /** Función para ocultar condicionalmente. */
  oculta?: (row: T) => boolean;
}

export interface AccionDisparada<T = any> {
  accionId: string;
  fila: T;
}
```

---

## 4. Componente

### `data-table.component.ts`

```typescript
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BadgeEstadoComponent } from '../badge-estado/badge-estado.component';
import { ProgressBarComponent } from '../progress-bar/progress-bar.component';
import type { ColumnaTabla, AccionFila, AccionDisparada } from './data-table.types';

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [DatePipe, FormsModule, BadgeEstadoComponent, ProgressBarComponent],
  templateUrl: './data-table.component.html',
  styleUrls: ['./data-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataTableComponent<T = any> {
  readonly filas = input.required<T[]>();
  readonly columnas = input.required<ColumnaTabla<T>[]>();
  readonly acciones = input<AccionFila<T>[]>([]);

  readonly busqueda = input<boolean>(true);
  readonly placeholderBusqueda = input<string>('Buscar...');
  readonly mensajeVacio = input<string>('Sin resultados');

  readonly tamanoPagina = input<number>(10);

  readonly accion = output<AccionDisparada<T>>();
  readonly textoBusqueda = signal('');
  readonly paginaActual = signal(1);

  readonly filasFiltradas = computed(() => {
    const term = this.textoBusqueda().toLowerCase().trim();
    if (!term) return this.filas();
    return this.filas().filter(f =>
      this.columnas().some(c => {
        const v = this.getValor(f, c.key);
        return String(v ?? '').toLowerCase().includes(term);
      })
    );
  });

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.filasFiltradas().length / this.tamanoPagina()))
  );

  readonly filasPagina = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.filasFiltradas().slice(inicio, inicio + this.tamanoPagina());
  });

  getValor(fila: any, key: string): any {
    return key.split('.').reduce((acc, k) => acc?.[k], fila);
  }

  formatearValor(fila: T, columna: ColumnaTabla<T>): string {
    const valor = this.getValor(fila, columna.key);
    return columna.format ? columna.format(valor, fila) : String(valor ?? '');
  }

  ejecutarAccion(accionId: string, fila: T) {
    this.accion.emit({ accionId, fila });
  }

  cambiarBusqueda(texto: string) {
    this.textoBusqueda.set(texto);
    this.paginaActual.set(1);
  }

  cambiarPagina(p: number) {
    if (p < 1 || p > this.totalPaginas()) return;
    this.paginaActual.set(p);
  }
}
```

### `data-table.component.html`

```html
<div class="data-table-wrapper">
  @if (busqueda()) {
    <div class="data-table-toolbar">
      <input
        type="search"
        class="form-control form-control-sm"
        [placeholder]="placeholderBusqueda()"
        [ngModel]="textoBusqueda()"
        (ngModelChange)="cambiarBusqueda($event)"
      />
      <span class="data-table-count">{{ filasFiltradas().length }} resultado(s)</span>
    </div>
  }

  <div class="data-table-scroll">
    <table class="data-table">
      <thead>
        <tr>
          @for (col of columnas(); track col.key) {
            <th [style.width]="col.width" [class]="'text-' + (col.align ?? 'left')">
              {{ col.label }}
            </th>
          }
          @if (acciones().length > 0) {
            <th class="text-end">Acciones</th>
          }
        </tr>
      </thead>
      <tbody>
        @if (filasPagina().length === 0) {
          <tr>
            <td [attr.colspan]="columnas().length + (acciones().length > 0 ? 1 : 0)" class="text-center text-muted p-4">
              {{ mensajeVacio() }}
            </td>
          </tr>
        } @else {
          @for (fila of filasPagina(); track $index) {
            <tr>
              @for (col of columnas(); track col.key) {
                <td [class]="'text-' + (col.align ?? 'left')">
                  @switch (col.tipo) {
                    @case ('badge') {
                      <app-badge-estado [estado]="getValor(fila, col.key)" tamano="sm" />
                    }
                    @case ('fecha') {
                      {{ getValor(fila, col.key) | date:'dd/MM/yyyy HH:mm' }}
                    }
                    @case ('booleano') {
                      <i [class]="getValor(fila, col.key) ? 'bi bi-check-circle text-success' : 'bi bi-x-circle text-secondary'"></i>
                    }
                    @case ('progreso') {
                      <app-progress-bar
                        [progreso]="getValor(fila, col.key) || 0"
                        [estado]="getValor(fila, 'estado') || ''"
                        [mostrarPorcentaje]="true"
                        tamano="sm"
                      />
                    }
                    @default {
                      {{ formatearValor(fila, col) }}
                    }
                  }
                </td>
              }
              @if (acciones().length > 0) {
                <td class="text-end">
                  <div class="data-table-acciones">
                    @for (a of acciones(); track a.id) {
                      @if (!a.oculta?.(fila)) {
                        <button
                          type="button"
                          class="btn btn-sm"
                          [class]="'btn-outline-' + (a.variante ?? 'secondary')"
                          [disabled]="a.deshabilitada?.(fila)"
                          [title]="a.label"
                          (click)="ejecutarAccion(a.id, fila)"
                        >
                          @if (a.icono) {
                            <i [class]="'bi ' + a.icono"></i>
                          } @else {
                            {{ a.label }}
                          }
                        </button>
                      }
                    }
                  </div>
                </td>
              }
            </tr>
          }
        }
      </tbody>
    </table>
  </div>

  @if (totalPaginas() > 1) {
    <nav class="data-table-paginador" aria-label="Paginación">
      <button class="btn btn-sm btn-outline-secondary"
              [disabled]="paginaActual() === 1"
              (click)="cambiarPagina(paginaActual() - 1)">
        <i class="bi bi-chevron-left"></i>
      </button>
      <span class="data-table-pagina-info">
        Página {{ paginaActual() }} de {{ totalPaginas() }}
      </span>
      <button class="btn btn-sm btn-outline-secondary"
              [disabled]="paginaActual() === totalPaginas()"
              (click)="cambiarPagina(paginaActual() + 1)">
        <i class="bi bi-chevron-right"></i>
      </button>
    </nav>
  }
</div>
```

### `data-table.component.scss`

```scss
.data-table-wrapper { display: flex; flex-direction: column; gap: 0.75rem; }
.data-table-toolbar { display: flex; align-items: center; gap: 0.75rem; }
.data-table-count { font-size: 0.85rem; color: var(--bs-secondary-color); }

.data-table-scroll { overflow-x: auto; }
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;

  th, td {
    padding: 0.6rem 0.75rem;
    border-bottom: 1px solid var(--bs-border-color);
    vertical-align: middle;
  }
  th { font-weight: 600; background: var(--bs-tertiary-bg); }
  tbody tr:hover { background: var(--bs-secondary-bg); }
}

.data-table-acciones { display: inline-flex; gap: 0.25rem; }

.data-table-paginador {
  display: flex; align-items: center; justify-content: flex-end;
  gap: 0.5rem;
  font-size: 0.85rem;
}
```

---

## 5. Ejemplo de uso

```typescript
// features/usuarios/pages/usuarios-lista.page.ts
columnas: ColumnaTabla<Usuario>[] = [
  { key: 'codigo', label: 'Código', width: '100px' },
  { key: 'nombre', label: 'Nombre' },
  { key: 'email', label: 'Email' },
  { key: 'tipo', label: 'Tipo', tipo: 'badge' },
  { key: 'activo', label: 'Activo', tipo: 'booleano', align: 'center', width: '80px' },
];

acciones: AccionFila<Usuario>[] = [
  { id: 'editar', label: 'Editar', icono: 'bi-pencil', variante: 'primary' },
  { id: 'borrar', label: 'Borrar', icono: 'bi-trash', variante: 'danger',
    deshabilitada: (u) => u.tipo === 'ADMINISTRADOR' },
];

onAccion({ accionId, fila }: AccionDisparada<Usuario>) {
  if (accionId === 'editar') this.editar(fila);
  if (accionId === 'borrar') this.confirmarBorrado(fila);
}
```

```html
<app-data-table
  [filas]="usuarios()"
  [columnas]="columnas"
  [acciones]="acciones"
  placeholderBusqueda="Buscar usuario..."
  (accion)="onAccion($event)"
/>
```

---

## 6. Pantallas a migrar

| Pantalla | Tipo de tabla |
|----------|---------------|
| `admin/usuarios` | Tabla con CRUD |
| `admin/departamentos` | Tabla con CRUD |
| `admin/actividades` | Tabla con CRUD |
| `admin/politicas` | Tabla con CRUD + estado |
| `admin/historial` | Tabla solo lectura con paginación |
| `admin/metricas/cuellos-botella` | Tabla solo lectura |

---

## 7. Commit

```bash
git add .
git commit -m "feat(ui-kit): app-data-table generica con columnas configurables, busqueda y paginacion"
```

---

## Próximo paso

Continuar con **`07_form_dinamico.md`**.
