# Fase 2.3 · Consolidar lógica de estado y BadgeEstado

> Primer componente real del `ui-kit`. Consolida lo que hoy está disperso: cómo se calcula el color, ícono y texto de cada estado del trámite.

---

## 1. Objetivo

1. Crear `core/utils/estado.utils.ts` con todas las funciones puras que mapean estado → color/ícono/texto/progreso efectivo.
2. Crear (o mejorar) `ui-kit/badge-estado/` para que renderice un badge consistente recibiendo solo el `estado` como string.
3. Reemplazar todo el cálculo de color de estado disperso en plantillas por uso del componente `<app-badge-estado>`.

> Esto **espeja exactamente** lo que ya consolidamos en Flutter (`tramites_seguimiento_service.dart`).

---

## 2. Archivos involucrados

### Crear nuevos
- `src/app/core/utils/estado.utils.ts`
- `src/app/ui-kit/` (carpeta)
- `src/app/ui-kit/badge-estado/badge-estado.component.ts`
- `src/app/ui-kit/badge-estado/badge-estado.component.html`
- `src/app/ui-kit/badge-estado/badge-estado.component.scss`
- `src/app/ui-kit/index.ts`

### Mover (mantener API si es compatible)
- `src/app/shared/ui/status-badge/` → `src/app/ui-kit/badge-estado/`
- Usar el contenido actual como base, mejorar.

---

## 3. `core/utils/estado.utils.ts`

```typescript
// src/app/core/utils/estado.utils.ts

export type ColorVariant = 'success' | 'danger' | 'warning' | 'info' | 'secondary' | 'primary';

/** Estados terminales (el trámite ya no avanza). */
const ESTADOS_TERMINALES = new Set([
  'Aprobado', 'Rechazado', 'Cancelado',
  'completado', 'archivado', 'rechazado',
]);

export function esEstadoTerminal(estado: string): boolean {
  return ESTADOS_TERMINALES.has(estado);
}

/** Color semántico según el estado. */
export function colorEstadoTramite(estado: string): ColorVariant {
  switch (estado) {
    case 'Aprobado':
    case 'completado':
      return 'success';
    case 'Rechazado':
    case 'rechazado':
      return 'danger';
    case 'Cancelado':
    case 'archivado':
      return 'secondary';
    case 'Observado':
    case 'Devuelto':
      return 'warning';
    case 'En proceso':
    case 'Derivado':
    case 'activo':
    case 'en_progreso':
      return 'info';
    default:
      return 'primary';
  }
}

/** Ícono (Bootstrap Icons o emoji). */
export function iconoEstadoTramite(estado: string): string {
  const iconos: Record<string, string> = {
    'Aprobado': 'bi-check-circle-fill',
    'Rechazado': 'bi-x-circle-fill',
    'Cancelado': 'bi-slash-circle',
    'En proceso': 'bi-hourglass-split',
    'Derivado': 'bi-arrow-up-right',
    'Observado': 'bi-exclamation-triangle',
    'Devuelto': 'bi-arrow-counterclockwise',
    'Nuevo': 'bi-stars',
    'borrador': 'bi-pencil',
    'completado': 'bi-check-circle-fill',
    'archivado': 'bi-archive',
    'rechazado': 'bi-x-circle-fill',
  };
  return iconos[estado] ?? 'bi-circle';
}

/** Texto legible. */
export function textoEstadoTramite(estado: string): string {
  const textos: Record<string, string> = {
    'Aprobado': 'Aprobado',
    'Rechazado': 'Rechazado',
    'Cancelado': 'Cancelado',
    'En proceso': 'En Proceso',
    'Derivado': 'Derivado',
    'Observado': 'Observado',
    'Devuelto': 'Devuelto',
    'Nuevo': 'Nuevo',
    'borrador': 'Borrador',
    'activo': 'En Proceso',
    'en_progreso': 'En Proceso',
    'completado': 'Completado',
    'archivado': 'Archivado',
    'rechazado': 'Rechazado',
  };
  return textos[estado] ?? estado;
}

/** Progreso efectivo: 100% para terminales aprobados, real para el resto. */
export function progresoEfectivo(progresoBackend: number, estado: string): number {
  if (estado === 'Aprobado' || estado === 'completado') return 100;
  if (esEstadoTerminal(estado) && progresoBackend === 0) return 100;
  return progresoBackend;
}
```

---

## 4. `ui-kit/badge-estado/`

### `badge-estado.component.ts`

```typescript
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  colorEstadoTramite,
  iconoEstadoTramite,
  textoEstadoTramite,
  type ColorVariant,
} from '../../core/utils/estado.utils';

@Component({
  selector: 'app-badge-estado',
  standalone: true,
  templateUrl: './badge-estado.component.html',
  styleUrls: ['./badge-estado.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BadgeEstadoComponent {
  readonly estado = input.required<string>();
  readonly tamano = input<'sm' | 'md' | 'lg'>('md');
  readonly mostrarIcono = input<boolean>(true);
  readonly mostrarTexto = input<boolean>(true);

  readonly color = computed<ColorVariant>(() => colorEstadoTramite(this.estado()));
  readonly icono = computed(() => iconoEstadoTramite(this.estado()));
  readonly texto = computed(() => textoEstadoTramite(this.estado()));
}
```

### `badge-estado.component.html`

```html
<span
  class="badge"
  [class.badge-sm]="tamano() === 'sm'"
  [class.badge-lg]="tamano() === 'lg'"
  [class.bg-success-subtle]="color() === 'success'"
  [class.text-success-emphasis]="color() === 'success'"
  [class.bg-danger-subtle]="color() === 'danger'"
  [class.text-danger-emphasis]="color() === 'danger'"
  [class.bg-warning-subtle]="color() === 'warning'"
  [class.text-warning-emphasis]="color() === 'warning'"
  [class.bg-info-subtle]="color() === 'info'"
  [class.text-info-emphasis]="color() === 'info'"
  [class.bg-secondary-subtle]="color() === 'secondary'"
  [class.text-secondary-emphasis]="color() === 'secondary'"
  [class.bg-primary-subtle]="color() === 'primary'"
  [class.text-primary-emphasis]="color() === 'primary'"
>
  @if (mostrarIcono()) {
    <i class="bi {{ icono() }}" aria-hidden="true"></i>
  }
  @if (mostrarTexto()) {
    <span>{{ texto() }}</span>
  }
</span>
```

### `badge-estado.component.scss`

```scss
.badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35em 0.65em;
  font-size: 0.85rem;
  font-weight: 600;
  border-radius: 999px;
  border: 1px solid currentColor;
  white-space: nowrap;
}

.badge-sm {
  font-size: 0.7rem;
  padding: 0.2em 0.5em;
}

.badge-lg {
  font-size: 1rem;
  padding: 0.5em 1em;
}
```

---

## 5. `ui-kit/index.ts` (barrel)

```typescript
// src/app/ui-kit/index.ts
export { BadgeEstadoComponent } from './badge-estado/badge-estado.component';
// se irán agregando los demás:
// export { ProgressBarComponent } from './progress-bar/progress-bar.component';
// export { CardTramiteComponent } from './card-tramite/card-tramite.component';
// ...
```

---

## 6. Pasos de migración

### Paso A — Crear estructura
```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/WEB_angular/src/app"
mkdir -p ui-kit/badge-estado
mkdir -p core/utils
```

### Paso B — Crear `core/utils/estado.utils.ts`
Pegar el contenido del punto 3.

### Paso C — Crear el componente
Crear los 3 archivos `badge-estado.component.{ts,html,scss}` con el contenido del punto 4.

### Paso D — Crear `ui-kit/index.ts`
Con el contenido del punto 5.

### Paso E — Probar en una pantalla piloto

Elegir una pantalla simple (ej: `funcionario/tramites-lista`) y:

1. Importar el componente:
```typescript
import { BadgeEstadoComponent } from '../../ui-kit/badge-estado/badge-estado.component';

@Component({
  ...,
  imports: [..., BadgeEstadoComponent],
})
```

2. Reemplazar el badge actual:
```html
<!-- Antes: -->
<span [class.bg-success]="t.estado === 'Aprobado'" ...>{{ t.estado }}</span>

<!-- Después: -->
<app-badge-estado [estado]="t.estado" />
```

3. Levantar la app y verificar visualmente.

### Paso F — Reemplazar en las demás pantallas

Buscar todos los lugares con cálculo de color de estado a mano (paso 3.2 de `02_auditoria_ui_kit.md`) y migrarlos uno a uno.

### Paso G — Borrar `shared/ui/status-badge/`

Cuando ningún archivo lo importe ya, eliminar la carpeta vieja y limpiar `shared/ui/index.ts`.

```bash
grep -rn "shared/ui/status-badge\|StatusBadgeComponent" src/app
# Si no devuelve nada → seguro de borrar
rm -rf src/app/shared/ui/status-badge
```

---

## 7. Verificación

| Pantalla | Antes | Después |
|----------|-------|---------|
| `/funcionario/tramites-lista` | Badge con estilo inline | `<app-badge-estado>` con color correcto |
| `/funcionario/bandeja-entrada` | (similar) | (similar) |
| `/funcionario/tramite-detalle` | (similar) | (similar) |
| `/admin/historial` | (similar) | (similar) |

Todas deben verse **iguales o mejor** que antes, con colores consistentes según `colorEstadoTramite()`.

---

## 8. Commit sugerido

```bash
git add .
git commit -m "feat(ui-kit): consolidar logica de estado en core/utils + crear app-badge-estado"
```

---

## Próximo paso

Continuar con **`04_progress_bar.md`** — el siguiente componente reutilizable.
