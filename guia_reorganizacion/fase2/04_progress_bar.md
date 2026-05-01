# Fase 2.4 · Componente progress-bar

> Barra de progreso reutilizable que se acopla al estado del trámite (color y porcentaje efectivo).

---

## 1. Objetivo

Reemplazar las múltiples implementaciones inline de barras de progreso por un único `<app-progress-bar>` que:

- Recibe el estado y el progreso del backend
- Calcula el progreso efectivo (100 % para terminales aprobados)
- Pinta el color que corresponde al estado
- Acepta variantes de tamaño

---

## 2. Estructura

```
ui-kit/progress-bar/
├── progress-bar.component.ts
├── progress-bar.component.html
└── progress-bar.component.scss
```

---

## 3. Código

### `progress-bar.component.ts`

```typescript
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  colorEstadoTramite,
  progresoEfectivo,
  type ColorVariant,
} from '../../core/utils/estado.utils';

@Component({
  selector: 'app-progress-bar',
  standalone: true,
  templateUrl: './progress-bar.component.html',
  styleUrls: ['./progress-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressBarComponent {
  /** Progreso 0-100 que viene del backend. */
  readonly progreso = input.required<number>();

  /** Estado del trámite (para definir color y progreso efectivo). */
  readonly estado = input.required<string>();

  /** Si se muestra el porcentaje al lado. */
  readonly mostrarPorcentaje = input<boolean>(true);

  /** Tamaño de la barra. */
  readonly tamano = input<'sm' | 'md' | 'lg'>('md');

  readonly progresoFinal = computed(() =>
    progresoEfectivo(this.progreso(), this.estado())
  );
  readonly color = computed<ColorVariant>(() => colorEstadoTramite(this.estado()));
}
```

### `progress-bar.component.html`

```html
<div class="progress-wrapper">
  <div class="progress" [class.progress-sm]="tamano() === 'sm'" [class.progress-lg]="tamano() === 'lg'">
    <div
      class="progress-bar"
      role="progressbar"
      [style.width.%]="progresoFinal()"
      [attr.aria-valuenow]="progresoFinal()"
      aria-valuemin="0"
      aria-valuemax="100"
      [class.bg-success]="color() === 'success'"
      [class.bg-danger]="color() === 'danger'"
      [class.bg-warning]="color() === 'warning'"
      [class.bg-info]="color() === 'info'"
      [class.bg-secondary]="color() === 'secondary'"
      [class.bg-primary]="color() === 'primary'"
    ></div>
  </div>

  @if (mostrarPorcentaje()) {
    <span class="progress-label">{{ progresoFinal() }}%</span>
  }
</div>
```

### `progress-bar.component.scss`

```scss
.progress-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
}

.progress {
  flex: 1;
  height: 8px;
  background-color: var(--bs-secondary-bg, #e9ecef);
  border-radius: 999px;
  overflow: hidden;
}

.progress-sm { height: 4px; }
.progress-lg { height: 14px; }

.progress-bar {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}

.progress-label {
  font-size: 0.85rem;
  font-weight: 600;
  min-width: 36px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
```

---

## 4. Pasos

### Paso A — Crear archivos
```bash
mkdir src/app/ui-kit/progress-bar
# crear los 3 archivos
```

### Paso B — Agregar al barrel `ui-kit/index.ts`
```typescript
export { ProgressBarComponent } from './progress-bar/progress-bar.component';
```

### Paso C — Probar en pantalla piloto

En `funcionario/tramites-lista`:
```html
<!-- Antes (probablemente algo así): -->
<div class="progress">
  <div class="progress-bar bg-info" [style.width.%]="t.progreso">{{ t.progreso }}%</div>
</div>

<!-- Después: -->
<app-progress-bar [progreso]="t.progreso" [estado]="t.estado" />
```

### Paso D — Reemplazar en las demás pantallas

| Pantalla | Acción |
|----------|--------|
| `funcionario/tramites-lista` | Reemplazar |
| `funcionario/bandeja-entrada` | Reemplazar |
| `funcionario/tramite-detalle` | Reemplazar |
| `admin/dashboard` (si tiene barras de progreso) | Reemplazar |
| `admin/historial` (si aplica) | Reemplazar |

---

## 5. Verificación

| Caso | Esperado |
|------|----------|
| Trámite "En proceso" 40 % | Barra azul al 40 % |
| Trámite "Aprobado" 0 % (bug histórico) | Barra verde al **100 %** ✅ |
| Trámite "Rechazado" 0 % | Barra roja al **100 %** (estado terminal con override) |
| Trámite "Rechazado" 33 % | Barra roja al 33 % (progreso real, no terminal con 0) |
| Trámite "Observado" 50 % | Barra amarilla al 50 % |

---

## 6. Commit

```bash
git add .
git commit -m "feat(ui-kit): app-progress-bar con progreso efectivo y color por estado"
```

---

## Próximo paso

Continuar con **`05_card_tramite.md`**.
