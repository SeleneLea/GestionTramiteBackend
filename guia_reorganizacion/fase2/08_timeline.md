# Fase 2.8 · Componente timeline

> Línea de tiempo vertical para mostrar el historial de un trámite (cuándo se inició, qué nodos se completaron, quién lo hizo).

---

## 1. Objetivo

Reemplazar implementaciones inline de timelines en `tramite-detalle` y `historial` con un componente único `<app-timeline>` que recibe una lista de **hitos** y los renderiza con icono, fecha, autor y descripción.

---

## 2. Estructura

```
ui-kit/timeline/
├── timeline.component.ts
├── timeline.component.html
├── timeline.component.scss
└── timeline.types.ts
```

---

## 3. Tipos públicos

### `timeline.types.ts`

```typescript
export type EstadoHito = 'completado' | 'enCurso' | 'pendiente' | 'rechazado';

export interface HitoTimeline {
  id: string;
  titulo: string;
  descripcion?: string;
  fecha?: string;             // ISO
  autor?: string;             // nombre del actor
  departamento?: string;
  estado: EstadoHito;
  icono?: string;             // override del ícono por defecto
}
```

---

## 4. Componente

### `timeline.component.ts`

```typescript
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import type { HitoTimeline, EstadoHito } from './timeline.types';

@Component({
  selector: 'app-timeline',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './timeline.component.html',
  styleUrls: ['./timeline.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimelineComponent {
  readonly hitos = input.required<HitoTimeline[]>();
  readonly compacto = input<boolean>(false);

  iconoPorEstado(estado: EstadoHito, override?: string): string {
    if (override) return override;
    return {
      completado: 'bi-check-circle-fill',
      enCurso: 'bi-hourglass-split',
      pendiente: 'bi-circle',
      rechazado: 'bi-x-circle-fill',
    }[estado];
  }

  colorPorEstado(estado: EstadoHito): string {
    return {
      completado: 'success',
      enCurso: 'info',
      pendiente: 'secondary',
      rechazado: 'danger',
    }[estado];
  }
}
```

### `timeline.component.html`

```html
<ol class="timeline" [class.timeline-compacto]="compacto()">
  @for (h of hitos(); track h.id; let last = $last) {
    <li class="timeline-item">
      <div class="timeline-marca timeline-marca--{{ colorPorEstado(h.estado) }}">
        <i [class]="'bi ' + iconoPorEstado(h.estado, h.icono)"></i>
      </div>

      @if (!last) {
        <div class="timeline-linea timeline-linea--{{ colorPorEstado(h.estado) }}"></div>
      }

      <div class="timeline-contenido">
        <h4 class="timeline-titulo">{{ h.titulo }}</h4>

        @if (h.descripcion) {
          <p class="timeline-desc">{{ h.descripcion }}</p>
        }

        <div class="timeline-meta">
          @if (h.fecha) {
            <span><i class="bi bi-clock"></i> {{ h.fecha | date:'dd/MM/yyyy HH:mm' }}</span>
          }
          @if (h.autor) {
            <span><i class="bi bi-person"></i> {{ h.autor }}</span>
          }
          @if (h.departamento) {
            <span><i class="bi bi-building"></i> {{ h.departamento }}</span>
          }
        </div>
      </div>
    </li>
  }
</ol>
```

### `timeline.component.scss`

```scss
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  position: relative;
}

.timeline-item {
  position: relative;
  padding-left: 3rem;
  padding-bottom: 1.5rem;
}

.timeline-compacto .timeline-item { padding-bottom: 0.75rem; }

.timeline-marca {
  position: absolute;
  left: 0;
  top: 0;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 0.85rem;
  z-index: 2;
}

.timeline-marca--success { background: var(--bs-success); }
.timeline-marca--info    { background: var(--bs-info); }
.timeline-marca--secondary { background: var(--bs-secondary); }
.timeline-marca--danger  { background: var(--bs-danger); }

.timeline-linea {
  position: absolute;
  left: calc(1rem - 1px);
  top: 2rem;
  bottom: 0;
  width: 2px;
  z-index: 1;
}
.timeline-linea--success    { background: var(--bs-success); }
.timeline-linea--info       { background: var(--bs-info); }
.timeline-linea--secondary  { background: var(--bs-secondary); }
.timeline-linea--danger     { background: var(--bs-danger); }

.timeline-contenido {
  background: var(--bs-tertiary-bg);
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
}

.timeline-titulo {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
}

.timeline-desc {
  margin: 0.35rem 0 0.5rem;
  font-size: 0.85rem;
  color: var(--bs-secondary-color);
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.75rem;
  color: var(--bs-secondary-color);

  span {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
  }
}
```

---

## 5. Ejemplo de uso

```typescript
// features/tramites/pages/detalle-tramite.page.ts
hitos = computed(() => {
  const t = this.estado();
  if (!t) return [];
  return t.historial.map(h => ({
    id: h.id,
    titulo: h.descripcion,
    fecha: h.fecha,
    autor: h.usuario,
    departamento: h.departamento,
    estado: this.mapearEstado(h.tipo),
  }) as HitoTimeline);
});

private mapearEstado(tipo: string): EstadoHito {
  switch (tipo) {
    case 'aprobacion':
    case 'completacion': return 'completado';
    case 'rechazo': return 'rechazado';
    case 'cambio_estado': return 'enCurso';
    default: return 'pendiente';
  }
}
```

```html
<app-timeline [hitos]="hitos()" />
```

---

## 6. Commit

```bash
git add .
git commit -m "feat(ui-kit): app-timeline para historial de tramites"
```

---

## Próximo paso

Continuar con **`09_reorganizar_features.md`**.
