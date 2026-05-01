# Fase 2.7 · Componente form-dinamico

> Formulario que se construye a partir de una **definición de campos** (JSON). Lo usa principalmente el expediente digital, donde cada sección tiene campos distintos definidos por el admin.

---

## 1. Objetivo

Que cualquier feature pueda renderizar un formulario reactivo describiendo los campos como datos, no como HTML. El componente:
- Construye el `FormGroup` automáticamente
- Aplica validaciones declaradas
- Maneja varios tipos de campo (texto, número, fecha, selección, área de texto, archivo, booleano)
- Emite el valor cuando cambia y al enviar

---

## 2. Estructura

```
ui-kit/form-dinamico/
├── form-dinamico.component.ts
├── form-dinamico.component.html
├── form-dinamico.component.scss
└── form-dinamico.types.ts
```

---

## 3. Tipos públicos

### `form-dinamico.types.ts`

```typescript
export type TipoCampo =
  | 'texto'
  | 'textoLargo'
  | 'numero'
  | 'fecha'
  | 'select'
  | 'booleano'
  | 'archivo';

export interface OpcionSelect {
  valor: string;
  label: string;
}

export interface CampoDef {
  /** Clave única del campo. */
  key: string;
  /** Etiqueta visible. */
  label: string;
  /** Tipo de control. */
  tipo: TipoCampo;
  /** Texto de ayuda bajo el campo. */
  ayuda?: string;
  /** Placeholder. */
  placeholder?: string;
  /** Si es obligatorio. */
  requerido?: boolean;
  /** Solo lectura. */
  soloLectura?: boolean;
  /** Para tipo='select'. */
  opciones?: OpcionSelect[];
  /** Para tipo='numero': mínimo / máximo. */
  min?: number;
  max?: number;
  /** Para tipo='texto'/'textoLargo': mínimo / máximo de caracteres. */
  minLength?: number;
  maxLength?: number;
  /** Patrón regex. */
  pattern?: string;
  /** Valor inicial. */
  valorInicial?: any;
}
```

---

## 4. Componente

### `form-dinamico.component.ts`

```typescript
import { ChangeDetectionStrategy, Component, OnInit, computed, effect, input, output } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import type { CampoDef } from './form-dinamico.types';

@Component({
  selector: 'app-form-dinamico',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './form-dinamico.component.html',
  styleUrls: ['./form-dinamico.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormDinamicoComponent implements OnInit {
  readonly campos = input.required<CampoDef[]>();
  readonly valoresIniciales = input<Record<string, any>>({});
  readonly textoBoton = input<string>('Guardar');
  readonly mostrarBoton = input<boolean>(true);

  readonly cambio = output<Record<string, any>>();
  readonly enviar = output<Record<string, any>>();

  formulario!: FormGroup;

  constructor(private fb: FormBuilder) {
    // Reconstruir el form si cambian los campos
    effect(() => {
      this.construirFormulario(this.campos(), this.valoresIniciales());
    });
  }

  ngOnInit(): void {
    if (!this.formulario) {
      this.construirFormulario(this.campos(), this.valoresIniciales());
    }
  }

  private construirFormulario(campos: CampoDef[], iniciales: Record<string, any>) {
    const grupo: Record<string, any> = {};
    for (const c of campos) {
      const validators = [];
      if (c.requerido) validators.push(Validators.required);
      if (c.minLength !== undefined) validators.push(Validators.minLength(c.minLength));
      if (c.maxLength !== undefined) validators.push(Validators.maxLength(c.maxLength));
      if (c.min !== undefined) validators.push(Validators.min(c.min));
      if (c.max !== undefined) validators.push(Validators.max(c.max));
      if (c.pattern) validators.push(Validators.pattern(c.pattern));

      const valor = iniciales[c.key] ?? c.valorInicial ?? this.valorDefaultPorTipo(c.tipo);
      grupo[c.key] = [{ value: valor, disabled: !!c.soloLectura }, validators];
    }
    this.formulario = this.fb.group(grupo);
    this.formulario.valueChanges.subscribe(v => this.cambio.emit(v));
  }

  private valorDefaultPorTipo(tipo: string) {
    return tipo === 'booleano' ? false : '';
  }

  onSubmit() {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviar.emit(this.formulario.getRawValue());
  }

  errores(key: string): string | null {
    const ctrl = this.formulario.get(key);
    if (!ctrl || !ctrl.touched || !ctrl.errors) return null;
    if (ctrl.errors['required']) return 'Este campo es obligatorio';
    if (ctrl.errors['minlength']) return `Mínimo ${ctrl.errors['minlength'].requiredLength} caracteres`;
    if (ctrl.errors['maxlength']) return `Máximo ${ctrl.errors['maxlength'].requiredLength} caracteres`;
    if (ctrl.errors['min']) return `Mínimo ${ctrl.errors['min'].min}`;
    if (ctrl.errors['max']) return `Máximo ${ctrl.errors['max'].max}`;
    if (ctrl.errors['pattern']) return 'Formato inválido';
    return 'Valor inválido';
  }
}
```

### `form-dinamico.component.html`

```html
<form [formGroup]="formulario" (ngSubmit)="onSubmit()" class="form-dinamico">
  @for (c of campos(); track c.key) {
    <div class="form-grupo">
      <label [for]="'campo-' + c.key" class="form-label">
        {{ c.label }}
        @if (c.requerido) { <span class="text-danger">*</span> }
      </label>

      @switch (c.tipo) {
        @case ('texto') {
          <input
            [id]="'campo-' + c.key"
            type="text"
            class="form-control"
            [formControlName]="c.key"
            [placeholder]="c.placeholder ?? ''"
          />
        }
        @case ('textoLargo') {
          <textarea
            [id]="'campo-' + c.key"
            class="form-control"
            rows="4"
            [formControlName]="c.key"
            [placeholder]="c.placeholder ?? ''"
          ></textarea>
        }
        @case ('numero') {
          <input
            [id]="'campo-' + c.key"
            type="number"
            class="form-control"
            [formControlName]="c.key"
            [min]="c.min ?? null"
            [max]="c.max ?? null"
          />
        }
        @case ('fecha') {
          <input [id]="'campo-' + c.key" type="date" class="form-control" [formControlName]="c.key" />
        }
        @case ('select') {
          <select [id]="'campo-' + c.key" class="form-select" [formControlName]="c.key">
            <option value="">-- Seleccionar --</option>
            @for (op of c.opciones ?? []; track op.valor) {
              <option [value]="op.valor">{{ op.label }}</option>
            }
          </select>
        }
        @case ('booleano') {
          <div class="form-check">
            <input [id]="'campo-' + c.key" type="checkbox" class="form-check-input" [formControlName]="c.key" />
          </div>
        }
        @case ('archivo') {
          <input [id]="'campo-' + c.key" type="file" class="form-control" [formControlName]="c.key" />
        }
      }

      @if (c.ayuda) {
        <small class="form-text text-muted">{{ c.ayuda }}</small>
      }

      @if (errores(c.key); as msg) {
        <div class="invalid-feedback d-block">{{ msg }}</div>
      }
    </div>
  }

  @if (mostrarBoton()) {
    <button type="submit" class="btn btn-primary mt-3" [disabled]="formulario.invalid">
      {{ textoBoton() }}
    </button>
  }
</form>
```

### `form-dinamico.component.scss`

```scss
.form-dinamico { display: flex; flex-direction: column; gap: 1rem; }
.form-grupo { display: flex; flex-direction: column; }
.form-label { font-weight: 600; margin-bottom: 0.25rem; }
```

---

## 5. Ejemplo de uso (sección de expediente)

```typescript
// features/expediente/pages/seccion-edicion.page.ts
campos: CampoDef[] = [
  { key: 'observaciones', label: 'Observaciones', tipo: 'textoLargo', requerido: true, minLength: 10 },
  { key: 'monto', label: 'Monto', tipo: 'numero', min: 0 },
  { key: 'fechaInspeccion', label: 'Fecha de inspección', tipo: 'fecha', requerido: true },
  { key: 'resultado', label: 'Resultado', tipo: 'select', requerido: true, opciones: [
    { valor: 'aprobado', label: 'Aprobado' },
    { valor: 'pendiente', label: 'Pendiente' },
    { valor: 'rechazado', label: 'Rechazado' },
  ]},
  { key: 'evidencia', label: 'Evidencia (foto)', tipo: 'archivo' },
];
```

```html
<app-form-dinamico
  [campos]="campos"
  [valoresIniciales]="datosSeccionActual()"
  textoBoton="Guardar sección"
  (enviar)="guardarSeccion($event)"
/>
```

---

## 6. Commit

```bash
git add .
git commit -m "feat(ui-kit): app-form-dinamico para formularios definidos por datos"
```

---

## Próximo paso

Continuar con **`08_timeline.md`**.
