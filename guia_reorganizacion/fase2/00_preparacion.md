# Fase 2.0 · Preparación previa

> Setup antes de empezar a tocar el frontend Angular.

---

## 1. Estado de partida verificado

Antes de cualquier cambio, asegurar que el frontend está sano:

### 1.1 Levantar la app
```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/WEB_angular"
npm install               # solo si hay cambios en package.json
npm start
```
Debe arrancar `ng serve` sin errores y abrir `http://localhost:4200`.

### 1.2 Smoke tests del frontend

| Acción | Esperado |
|--------|----------|
| Abrir `/login` | Pantalla de login carga |
| Login con admin | Redirige a `/admin/dashboard` |
| Listar departamentos | Tabla con datos |
| Listar políticas | Tabla con datos |
| Editor de diagramas | Carga sin errores de consola |
| Login con funcionario | Redirige a `/funcionario/...` |
| Bandeja de entrada | Lista de pendientes |
| Detalle de trámite | Muestra estado, progreso, etapa |
| Logout | Limpia token y vuelve a login |

Si alguno falla **hoy**, arreglarlo antes de empezar la fase 2.

---

## 2. Control de versiones

### 2.1 Commitear cualquier pendiente
```bash
git status
git add . && git commit -m "..."
```

### 2.2 Crear rama dedicada
```bash
git checkout -b refactor/componentes-frontend
```

### 2.3 Tag del estado actual
```bash
git tag pre-refactor-fase2
```

---

## 3. Auditoría de duplicación visual

Hacer un **paseo manual** por las pantallas y anotar dónde se repite la misma idea visual:

### Plantilla de auditoría rápida

| Pantalla | Badge de estado | Barra de progreso | Card de trámite | Tabla con paginación | Timeline |
|----------|-----------------|---------------------|------------------|----------------------|----------|
| `/admin/dashboard` | ¿usa shared/ui? | | | | |
| `/admin/usuarios` | | | — | | — |
| `/admin/departamentos` | | | — | | — |
| `/admin/politicas` | | | — | | — |
| `/admin/diagramas` | | | — | | — |
| `/admin/historial` | | | — | | ¿propio? |
| `/admin/metricas` | | | — | | — |
| `/funcionario/bandeja-entrada` | | | ¿propio? | | — |
| `/funcionario/tramites-lista` | | | ¿propio? | | — |
| `/funcionario/tramite-detalle` | | | | — | ¿propio? |
| `/funcionario/expediente-digital` | | | — | | — |

> Anotar en el README del componente del UI kit la lista de pantallas donde sí se aplicará.

---

## 4. Estructura objetivo (visión general)

Vamos hacia esto:

```
src/app/
├── core/                      ← (sin cambios mayores)
│   ├── guards/
│   ├── interceptors/
│   ├── models/
│   └── services/
├── ui-kit/                    ← NUEVO: componentes reutilizables propios
│   ├── badge-estado/          (movido desde shared/ui/status-badge)
│   ├── progress-bar/          (NUEVO)
│   ├── card-tramite/          (NUEVO)
│   ├── data-table/            (movido desde shared/ui/table)
│   ├── form-dinamico/         (NUEVO)
│   ├── timeline/              (NUEVO)
│   ├── modal/                 (movido desde shared/ui/modal)
│   ├── input/                 (movido desde shared/ui/input)
│   ├── empty-state/           (movido desde shared/ui/empty-state)
│   └── index.ts               (barrel)
├── layout/                    ← antes shared/navbar, sidebar, etc.
│   ├── navbar/
│   ├── sidebar/
│   └── (componentes de layout)
├── features/                  ← NUEVO: features por dominio (no por rol)
│   ├── auth/                  (desde auth/)
│   ├── tramites/              (desde funcionario/tramite-* + funcionario/tramites-lista + funcionario/bandeja-entrada)
│   ├── expediente/            (desde funcionario/expediente-digital)
│   ├── diagramas/             (desde admin/diagramas)
│   ├── politicas/             (desde admin/politicas)
│   ├── departamentos/         (desde admin/departamentos)
│   ├── actividades/           (desde admin/actividades)
│   ├── usuarios/              (desde admin/usuarios)
│   ├── metricas/              (desde admin/metricas)
│   ├── historial/             (desde admin/historial)
│   └── dashboard/             (desde admin/dashboard)
├── app.config.ts
├── app.routes.ts
└── app.ts
```

> **Decisión clave:** los features ya **no** se llaman `admin/usuarios` ni `funcionario/tramites-lista`. Se llaman `features/usuarios` y `features/tramites`. La separación de **quién puede ver qué** la hace `app.routes.ts` con `canActivate` y guards basados en rol, no la estructura de carpetas.

---

## 5. Convenciones a aplicar

### 5.1 Naming
| Tipo | Patrón | Ejemplo |
|------|--------|---------|
| Selector de componente UI | `app-<nombre>` | `app-badge-estado` |
| Carpeta del componente | `<nombre>` (kebab) | `badge-estado/` |
| Clase del componente | `<Nombre>Component` | `BadgeEstadoComponent` |
| Servicio | `<Nombre>Service` | `EstadoService` |
| Modelo | `<Nombre>` (singular) | `Tramite` |

### 5.2 Standalone components
Todos los componentes nuevos son **standalone** (no en NgModules). Así están los del proyecto hoy.

### 5.3 Inputs y outputs tipados
```typescript
@Input({ required: true }) estado!: string;
@Input() tamaño: 'sm' | 'md' | 'lg' = 'md';
@Output() cambio = new EventEmitter<string>();
```

### 5.4 ChangeDetection OnPush
Todos los componentes UI Kit usan:
```typescript
@Component({
  ...
  changeDetection: ChangeDetectionStrategy.OnPush,
})
```

### 5.5 Signals donde aplique
Para estado interno: `signal()`. Para inputs: usar `input()` (Angular 17+).

---

## 6. Confirmación antes de seguir

- [ ] Frontend levanta sin errores
- [ ] Smoke tests pasan
- [ ] Rama `refactor/componentes-frontend` creada
- [ ] Tag `pre-refactor-fase2` puesto
- [ ] Tabla de auditoría llenada (al menos a ojo)

```bash
git add .
git commit -m "chore: punto cero de fase 2 (auditoria de duplicacion UI)"
```

---

## Próximo paso

Continuar con **`01_estructura_objetivo.md`**.
