# Fase 2.1 · Estructura objetivo y decisiones de diseño

> Antes de mover código, dejar claro **adónde** va. Este documento es la "foto" del frontend al final de la fase 2.

---

## 1. Estructura final completa

```
WEB_angular/src/app/
│
├── core/                              ← infraestructura común (queda casi igual)
│   ├── guards/
│   │   ├── auth.guard.ts
│   │   └── role.guard.ts
│   ├── interceptors/
│   │   ├── auth.interceptor.ts
│   │   └── error.interceptor.ts
│   ├── models/                        ← interfaces de dominio
│   │   ├── tramite.model.ts
│   │   ├── usuario.model.ts
│   │   └── ...
│   ├── services/                      ← servicios HTTP por componente backend
│   │   ├── auth.service.ts
│   │   ├── workflow.service.ts
│   │   ├── diagrama.service.ts
│   │   └── ...
│   └── utils/                         ← NUEVO: helpers transversales
│       ├── estado.utils.ts            ← cálculo de color/icono/texto por estado
│       └── fecha.utils.ts             ← formato de fechas
│
├── ui-kit/                            ← NUEVO: componentes presentacionales reutilizables
│   ├── badge-estado/
│   │   ├── badge-estado.component.ts
│   │   ├── badge-estado.component.html
│   │   └── badge-estado.component.scss
│   ├── progress-bar/
│   ├── card-tramite/
│   ├── data-table/
│   ├── form-dinamico/
│   ├── timeline/
│   ├── modal/
│   ├── input-text/
│   ├── input-select/
│   ├── empty-state/
│   ├── confirm-dialog/
│   └── index.ts                       ← barrel: exporta todos
│
├── layout/                            ← chrome de la app (no es UI reutilizable de negocio)
│   ├── navbar/
│   ├── sidebar/
│   ├── topbar/
│   └── shell/                         ← layout principal con <router-outlet>
│
├── features/                          ← features por DOMINIO de negocio
│   ├── auth/
│   │   └── login/
│   ├── dashboard/
│   ├── tramites/                      ← antes funcionario/tramites-* + bandeja-entrada
│   │   ├── pages/
│   │   │   ├── lista-tramites.page.ts
│   │   │   ├── bandeja-entrada.page.ts
│   │   │   └── detalle-tramite.page.ts
│   │   ├── components/                ← componentes específicos del feature (no reutilizables)
│   │   └── tramites.routes.ts
│   ├── expediente/
│   ├── diagramas/                     ← antes admin/diagramas
│   ├── politicas/                     ← antes admin/politicas
│   ├── departamentos/                 ← antes admin/departamentos
│   ├── actividades/                   ← antes admin/actividades
│   ├── usuarios/                      ← antes admin/usuarios
│   ├── metricas/                      ← antes admin/metricas
│   └── historial/                     ← antes admin/historial
│
├── app.config.ts
├── app.routes.ts                      ← define qué rol ve qué feature
├── app.ts
└── app.html
```

---

## 2. Decisiones de diseño explicadas

### 2.1 ¿Por qué `features/` en vez de `admin/` y `funcionario/`?

**Antes (por rol):**
- `admin/usuarios` ← implícito: "esta sección es solo para admin"
- `funcionario/tramites-lista` ← solo para funcionario

**Problema:**
- Un funcionario también necesita ver "trámites" (su bandeja). Pero está en `funcionario/`. Si mañana queremos que el cliente también vea "trámites" desde la web, ¿dónde lo ponemos? ¿Duplicamos en `cliente/tramites/`?
- Si el feature `usuarios` mañana lo puede ver también un funcionario (modo lectura), tendríamos que mover archivos.
- La organización por rol acopla la **estructura del código** a una decisión de **autorización** que cambia.

**Después (por dominio):**
- `features/tramites/` — todo lo relacionado con trámites (lista, detalle, bandeja)
- `features/usuarios/` — todo lo relacionado con usuarios

Quién puede ver cada feature lo controla `app.routes.ts`:

```typescript
{
  path: 'tramites',
  loadChildren: () => import('./features/tramites/tramites.routes').then(m => m.TRAMITES_ROUTES),
  canActivate: [authGuard, roleGuard(['ADMINISTRADOR', 'FUNCIONARIO', 'CLIENTE'])]
},
{
  path: 'usuarios',
  loadChildren: () => import('./features/usuarios/usuarios.routes').then(m => m.USUARIOS_ROUTES),
  canActivate: [authGuard, roleGuard(['ADMINISTRADOR'])]
},
```

### 2.2 ¿Qué va en `ui-kit/` y qué va en `features/<feature>/components/`?

**`ui-kit/`** — componente reutilizable **entre features**:
- Si lo usan al menos 2 features → va a `ui-kit/`
- Si lo podría usar otro proyecto Angular sin cambios → va a `ui-kit/`
- No conoce reglas de negocio (recibe datos por `@Input`, emite eventos por `@Output`)

**`features/<feature>/components/`** — componente específico del feature:
- Solo lo usa **ese** feature
- Conoce reglas de negocio del feature
- Inyecta servicios del dominio del feature

**Ejemplo concreto:**
- `<app-badge-estado [estado]="...">`  → ui-kit (genérico)
- `<app-card-tramite [tramite]="...">`  → ui-kit (lo usa lista, bandeja, detalle, dashboard)
- `<app-form-secciones-expediente>` → `features/expediente/components/` (solo lo usa expediente)

### 2.3 ¿Por qué `core/utils/`?

Hoy hay funciones repetidas como `getColorEstado()` que están en distintos services. Las consolidamos en:

```typescript
// core/utils/estado.utils.ts
export function colorEstadoTramite(estado: string): 'success' | 'danger' | 'info' | 'warning' | 'secondary' {
  switch (estado) {
    case 'Aprobado': case 'completado': return 'success';
    case 'Rechazado': case 'rechazado': return 'danger';
    case 'Cancelado': case 'archivado': return 'secondary';
    case 'Observado': case 'Devuelto': return 'warning';
    default: return 'info';
  }
}

export function iconoEstadoTramite(estado: string): string { ... }
export function textoEstadoTramite(estado: string): string { ... }
export function progresoEfectivo(progreso: number, estado: string): number { ... }
export function esEstadoTerminal(estado: string): boolean { ... }
```

> Misma idea que ya consolidamos en Flutter (`tramites_seguimiento_service.dart` con `getColorEstadoFlutter`, `progresoEfectivo`, `esEstadoTerminal`).

---

## 3. Política de migración

### 3.1 Migrar gradualmente, no de golpe
1. Primero crear todos los componentes nuevos del `ui-kit/` (subfases 2.3 a 2.8)
2. Reemplazarlos pantalla por pantalla
3. Cuando todas las pantallas usen el componente nuevo, borrar el código duplicado
4. Recién al final reorganizar las features (subfase 2.9)

### 3.2 Compatibilidad temporal
Si una pantalla aún usa el badge "viejo" mientras otra usa el "nuevo", **está bien temporalmente**. Eso es el costo de migrar gradualmente.

---

## 4. Mapa de dependencias entre componentes UI Kit

```
                     ┌──────────────────┐
                     │   ui-kit         │
                     │                  │
   ┌─────────────────┼ ─ badge-estado    │
   │   ┌─────────────┼ ─ progress-bar   │
   │   │             │                  │
   │   │   ┌─────────┼ ─ card-tramite   │ usa badge + progress
   │   │   │         │                  │
   │   │   │         │  ─ data-table    │
   │   │   │         │  ─ modal         │
   │   │   │         │  ─ form-dinamico │ usa input-text + input-select
   │   │   │         │  ─ timeline      │
   │   │   │         │  ─ empty-state   │
   │   │   │         │  ─ confirm-dialog│ usa modal
   │   │   │         └──────────────────┘
   │   │   │
   │   │   └──→ usado en: features/tramites, features/dashboard
   │   └──→ usado en: features/tramites, features/expediente
   └──→ usado en: features/tramites, features/expediente, features/historial
```

**Regla:** un componente del `ui-kit` puede usar a otro componente del `ui-kit` (ej: `card-tramite` usa `badge-estado`). No al revés (`badge-estado` no debe importar nada de `card-tramite`).

---

## 5. Próximo paso

Continuar con **`02_auditoria_ui_kit.md`** para inventariar exactamente qué tenemos hoy en `shared/ui/`.
