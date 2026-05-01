# Fase 2.2 · Auditoría del UI kit existente

> Antes de crear nada nuevo, entender exactamente **qué hay** en `shared/ui/` hoy, qué se usa, qué tiene bugs, qué se reutilizaría tal cual y qué hay que reescribir.

---

## 1. Componentes encontrados en `src/app/shared/ui/`

| Carpeta | Estado |
|---------|--------|
| `card/` | A revisar |
| `empty-state/` | A revisar |
| `input/` | A revisar |
| `modal/` | A revisar |
| `status-badge/` | A revisar |
| `table/` | A revisar |
| `index.ts` | Barrel — exporta los anteriores |

Y además, fuera de `ui/`:
- `shared/navbar/`
- `shared/sidebar/`
- `shared/skeleton/`
- `shared/toast/`
- `shared/pages/`

---

## 2. Plantilla de auditoría por componente

Para cada componente del `shared/ui/`, completar esta ficha:

### Plantilla

```markdown
## <componente>/

### API actual
- Selector: `app-<...>`
- Inputs: [...]
- Outputs: [...]
- ChangeDetection: OnPush / Default
- Standalone: sí / no

### Dónde se usa actualmente
Buscar con: `grep -r "app-<selector>" src/app/`
- features/...
- features/...

### Bugs o problemas detectados
- ...

### Decisión
- [ ] Mantener tal cual y solo mover a `ui-kit/`
- [ ] Mejorar (mantener API pero reescribir interno)
- [ ] Cambiar API (rompe compatibilidad — migrar consumidores)
- [ ] Reemplazar por uno nuevo
```

---

## 3. Comandos para auditar rápidamente

### 3.1 Buscar uso de cada selector

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/WEB_angular/src/app"

# Status badge
grep -rn "app-status-badge" --include="*.html" --include="*.ts"

# Card
grep -rn "app-card" --include="*.html" --include="*.ts"

# Modal
grep -rn "app-modal" --include="*.html" --include="*.ts"

# Table
grep -rn "app-table" --include="*.html" --include="*.ts"

# Input
grep -rn "app-input" --include="*.html" --include="*.ts"

# Empty state
grep -rn "app-empty-state" --include="*.html" --include="*.ts"
```

### 3.2 Detectar duplicación de lógica de estado

```bash
# ¿En cuántas plantillas se calcula color de estado a mano?
grep -rn "Aprobado" --include="*.html" --include="*.ts" | grep -v "shared/ui"
grep -rn "Rechazado" --include="*.html" --include="*.ts" | grep -v "shared/ui"
grep -rn "case 'completado'" --include="*.ts"
grep -rn "ngClass.*estado" --include="*.html"
```

Cualquier coincidencia que **no** esté usando `app-status-badge` es candidata a refactor.

### 3.3 Detectar tablas hechas a mano

```bash
grep -rln "<table" --include="*.html" | grep -v "shared/ui"
```

Listar las pantallas que tienen `<table>` en lugar de usar `<app-table>`.

---

## 4. Resultado esperado: matriz de decisión

Tras la auditoría, llenar esta tabla:

| Componente | Usado en (cantidad) | Reusable? | Decisión |
|------------|---------------------|-----------|----------|
| `app-status-badge` | __ pantallas | Sí | Mantener, mover a `ui-kit/badge-estado/` y extender API si falta |
| `app-card` | __ pantallas | Sí | Mantener tal cual |
| `app-modal` | __ pantallas | Sí | Mantener tal cual |
| `app-table` | __ pantallas | Sí | Mejorar genericidad → `ui-kit/data-table/` |
| `app-input` | __ pantallas | Sí | Mantener |
| `app-empty-state` | __ pantallas | Sí | Mantener |

---

## 5. Componentes "shadow" (no en shared/ui pero deberían estar)

Buscar componentes que **se repiten** en distintos features y deberían moverse a `ui-kit`:

```bash
# Buscar componentes con nombres parecidos
find src/app -name "*card*.component.ts" | grep -v "shared/ui"
find src/app -name "*timeline*.component.ts"
find src/app -name "*progreso*.component.ts" -o -name "*progress*.component.ts"
```

Si hay 2 versiones distintas de `tramite-card.component.ts` en `admin/` y `funcionario/`, hay que **fusionarlas** en una sola en `ui-kit/`.

---

## 6. Auditoría de `core/services/`

Verificar que cada servicio hace **solo HTTP**, sin lógica de UI:

```bash
grep -rn "Color\|color\|estilo\|Style" core/services/
```

Si encontramos `getColorEstado()` o `getIconoEstado()` en algún `.service.ts`, eso es lógica de **vista**, no de servicio HTTP. Se mueve a `core/utils/estado.utils.ts`.

---

## 7. Documentar resultado

Crear archivo `fase2/AUDITORIA_RESULTADO.md` (al ejecutar realmente la subfase) con:

```markdown
# Resultado de auditoría UI kit

## Inventario actual
[matriz de la sección 4]

## Componentes "shadow" detectados
- `funcionario/.../card.component.ts` y `admin/.../card.component.ts` — fusionar
- ...

## Lógica de UI dispersa en services
- `core/services/X.service.ts` tiene `getColorEstado()` — mover a `core/utils/`

## Estimación de impacto
- Archivos a modificar: __
- Pantallas a tocar: __
- Componentes a crear nuevos: __
- Componentes a fusionar: __
```

---

## 8. Commit sugerido (es solo doc, no código)

```bash
git add fase2/AUDITORIA_RESULTADO.md
git commit -m "docs: auditoría inicial del UI kit existente"
```

---

## Próximo paso

Continuar con **`03_estado_y_badges.md`** — el primer componente reutilizable concreto.
