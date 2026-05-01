# Fase 2.5 · Componente card-tramite

> Tarjeta resumen de un trámite. Reutilizable en lista de cliente, bandeja del funcionario, dashboard admin y cualquier listado de trámites.

---

## 1. Objetivo

Una tarjeta consistente que muestra:
- Código y política
- Badge de estado (usa `app-badge-estado`)
- Progreso (usa `app-progress-bar`)
- Etapa actual (solo si no está cerrado)
- Fechas (creación, cierre)

Y emite un evento `(click)` para navegar al detalle.

---

## 2. Estructura

```
ui-kit/card-tramite/
├── card-tramite.component.ts
├── card-tramite.component.html
└── card-tramite.component.scss
```

---

## 3. Modelo de entrada

El componente NO conoce ningún modelo del backend específico. Recibe un `TramiteCardData`:

### `ui-kit/card-tramite/tramite-card-data.ts`

```typescript
export interface TramiteCardData {
  id: string;
  codigo: string;
  politicaNombre: string;
  estado: string;
  progreso: number;
  nodoActualNombre?: string;
  fechaInicio?: string;
  fechaCierreReal?: string | null;
  prioridad?: number;
}
```

> **Por qué un DTO propio del componente:** desacoplamos el `<app-card-tramite>` de cómo se llaman los campos en el modelo del backend. Si mañana cambia el backend, el componente UI no se entera; solo cambia el mapping en el feature consumidor.

---

## 4. Código

### `card-tramite.component.ts`

```typescript
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { BadgeEstadoComponent } from '../badge-estado/badge-estado.component';
import { ProgressBarComponent } from '../progress-bar/progress-bar.component';
import { esEstadoTerminal } from '../../core/utils/estado.utils';
import type { TramiteCardData } from './tramite-card-data';

@Component({
  selector: 'app-card-tramite',
  standalone: true,
  imports: [DatePipe, BadgeEstadoComponent, ProgressBarComponent],
  templateUrl: './card-tramite.component.html',
  styleUrls: ['./card-tramite.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardTramiteComponent {
  readonly tramite = input.required<TramiteCardData>();

  /** Si la card es clickeable (cursor pointer + hover) */
  readonly clickeable = input<boolean>(true);

  /** Click en cualquier parte de la card */
  readonly seleccion = output<string>();

  readonly esCerrado = computed(() => esEstadoTerminal(this.tramite().estado));

  onClick(): void {
    if (this.clickeable()) {
      this.seleccion.emit(this.tramite().id);
    }
  }
}
```

### `card-tramite.component.html`

```html
<article
  class="card card-tramite"
  [class.card-clickeable]="clickeable()"
  (click)="onClick()"
  (keydown.enter)="onClick()"
  [attr.tabindex]="clickeable() ? 0 : null"
  role="button"
>
  <div class="card-body">
    <header class="card-header">
      <div class="card-header-info">
        <h3 class="card-codigo">{{ tramite().codigo }}</h3>
        <p class="card-politica">{{ tramite().politicaNombre }}</p>
      </div>
      <app-badge-estado [estado]="tramite().estado" tamano="sm" />
    </header>

    @if (!esCerrado() && tramite().nodoActualNombre) {
      <div class="card-etapa">
        <i class="bi bi-info-circle" aria-hidden="true"></i>
        <span>Etapa: {{ tramite().nodoActualNombre }}</span>
      </div>
    }

    <div class="card-progreso">
      <app-progress-bar [progreso]="tramite().progreso" [estado]="tramite().estado" />
    </div>

    <footer class="card-footer">
      @if (tramite().fechaInicio) {
        <span class="card-fecha">
          <i class="bi bi-calendar-event"></i>
          Creado: {{ tramite().fechaInicio | date:'dd/MM/yyyy' }}
        </span>
      }
      @if (tramite().fechaCierreReal) {
        <span class="card-fecha card-fecha--cierre">
          <i class="bi bi-check-circle"></i>
          Cerrado: {{ tramite().fechaCierreReal | date:'dd/MM/yyyy' }}
        </span>
      }
    </footer>
  </div>
</article>
```

### `card-tramite.component.scss`

```scss
.card-tramite {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.5rem;
  background: var(--bs-body-bg);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.card-clickeable {
  cursor: pointer;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  &:focus {
    outline: 2px solid var(--bs-primary);
    outline-offset: 2px;
  }
}

.card-body {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
}

.card-codigo {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
}

.card-politica {
  margin: 0.25rem 0 0;
  font-size: 0.85rem;
  color: var(--bs-secondary-color);
}

.card-etapa {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  color: var(--bs-secondary-color);
}

.card-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.75rem;
  color: var(--bs-secondary-color);
  border-top: 1px solid var(--bs-border-color);
  padding-top: 0.5rem;
}

.card-fecha {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.card-fecha--cierre {
  color: var(--bs-success);
}
```

---

## 5. Pasos

### Paso A — Crear archivos

```bash
mkdir src/app/ui-kit/card-tramite
# crear los 4 archivos (ts, html, scss, tramite-card-data.ts)
```

### Paso B — Agregar al barrel

```typescript
// ui-kit/index.ts
export { CardTramiteComponent } from './card-tramite/card-tramite.component';
export type { TramiteCardData } from './card-tramite/tramite-card-data';
```

### Paso C — Pantalla piloto: `funcionario/tramites-lista`

```typescript
import { CardTramiteComponent, TramiteCardData } from '../../../ui-kit';
import { Router } from '@angular/router';

@Component({
  ...,
  imports: [CardTramiteComponent],
})
export class TramitesListaComponent {
  private readonly router = inject(Router);

  // Mapper del modelo del backend al DTO del componente
  toCardData(t: Tramite): TramiteCardData {
    return {
      id: t.id,
      codigo: t.codigo,
      politicaNombre: t.politicaNombre ?? '',
      estado: t.estadoActual ?? t.estado,
      progreso: t.progreso ?? 0,
      nodoActualNombre: t.nodoActualNombre,
      fechaInicio: t.fechaInicio,
      fechaCierreReal: t.fechaCierreReal,
    };
  }

  abrirDetalle(id: string) {
    this.router.navigate(['/tramites', id]);
  }
}
```

```html
@for (t of tramites(); track t.id) {
  <app-card-tramite
    [tramite]="toCardData(t)"
    (seleccion)="abrirDetalle($event)"
  />
}
```

### Paso D — Migrar otras pantallas

| Pantalla | Acción |
|----------|--------|
| `funcionario/bandeja-entrada` | Migrar — la card de cada pendiente usa `app-card-tramite` |
| `admin/dashboard` (sección "trámites recientes") | Migrar |
| `admin/historial` | Probablemente usa tabla, no card — se migra en `06_data_table.md` |

---

## 6. Verificación

- Las tres listas de trámites muestran cards con look consistente
- Los estados pintan correctamente (verde / rojo / naranja / azul)
- El progreso refleja `progresoEfectivo` (los aprobados al 100 %)
- Click en la card navega al detalle

---

## 7. Commit

```bash
git add .
git commit -m "feat(ui-kit): app-card-tramite reutilizable con DTO desacoplado"
```

---

## Próximo paso

Continuar con **`06_data_table.md`**.
