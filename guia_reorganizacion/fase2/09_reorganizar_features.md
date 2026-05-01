# Fase 2.9 · Reorganizar features (admin/ + funcionario/ → features/)

> Mover los features de la organización por rol a la organización por dominio. Es la subfase más larga en mover archivos pero no agrega lógica.

---

## 1. Objetivo

Eliminar las carpetas raíz `admin/` y `funcionario/` y consolidar todos los features bajo `features/`, donde cada feature representa un **dominio de negocio**, no un rol.

---

## 2. Mapping completo

| Hoy | Mañana |
|-----|--------|
| `admin/dashboard/` | `features/dashboard/` |
| `admin/usuarios/` | `features/usuarios/` |
| `admin/departamentos/` | `features/departamentos/` |
| `admin/actividades/` | `features/actividades/` |
| `admin/politicas/` | `features/politicas/` |
| `admin/diagramas/` | `features/diagramas/` |
| `admin/historial/` | `features/historial/` |
| `admin/metricas/` | `features/metricas/` |
| `funcionario/bandeja-entrada/` | `features/tramites/pages/bandeja-entrada.page.ts` |
| `funcionario/tramites-lista/` | `features/tramites/pages/tramites-lista.page.ts` |
| `funcionario/tramite-detalle/` | `features/tramites/pages/tramite-detalle.page.ts` |
| `funcionario/expediente-digital/` | `features/expediente/pages/expediente-digital.page.ts` |
| `auth/login/` | `features/auth/login/` |
| `shared/navbar/` | `layout/navbar/` |
| `shared/sidebar/` | `layout/sidebar/` |
| `shared/skeleton/` | `layout/skeleton/` |
| `shared/toast/` | `layout/toast/` (o queda en `ui-kit/` si se vuelve genérico) |

---

## 3. Estructura interna de cada feature

Cada feature sigue este patrón:

```
features/<feature>/
├── pages/                     ← componentes ruteables (smart, conectan con servicios)
├── components/                ← componentes propios del feature (dumb, no ruteables)
├── <feature>.routes.ts        ← define las rutas hijas del feature
└── (servicios específicos del feature, si aplica)
```

### Ejemplo: `features/tramites/`

```
features/tramites/
├── pages/
│   ├── tramites-lista.page.ts
│   ├── tramites-lista.page.html
│   ├── tramites-lista.page.scss
│   ├── bandeja-entrada.page.ts
│   ├── bandeja-entrada.page.html
│   ├── bandeja-entrada.page.scss
│   ├── tramite-detalle.page.ts
│   ├── tramite-detalle.page.html
│   └── tramite-detalle.page.scss
├── components/
│   └── (componentes propios si aparecen)
└── tramites.routes.ts
```

### `tramites.routes.ts` ejemplo

```typescript
import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';

export const TRAMITES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/tramites-lista.page').then(m => m.TramitesListaPage),
  },
  {
    path: 'bandeja',
    loadComponent: () => import('./pages/bandeja-entrada.page').then(m => m.BandejaEntradaPage),
    canActivate: [roleGuard(['FUNCIONARIO', 'ADMINISTRADOR'])],
  },
  {
    path: ':id',
    loadComponent: () => import('./pages/tramite-detalle.page').then(m => m.TramiteDetallePage),
  },
];
```

---

## 4. `app.routes.ts` consolidado

```typescript
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.page').then(m => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell.component').then(m => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.page').then(m => m.DashboardPage),
      },
      {
        path: 'tramites',
        loadChildren: () => import('./features/tramites/tramites.routes').then(m => m.TRAMITES_ROUTES),
      },
      {
        path: 'expediente',
        loadChildren: () => import('./features/expediente/expediente.routes').then(m => m.EXPEDIENTE_ROUTES),
      },
      {
        path: 'usuarios',
        canActivate: [roleGuard(['ADMINISTRADOR'])],
        loadChildren: () => import('./features/usuarios/usuarios.routes').then(m => m.USUARIOS_ROUTES),
      },
      {
        path: 'departamentos',
        canActivate: [roleGuard(['ADMINISTRADOR'])],
        loadChildren: () => import('./features/departamentos/departamentos.routes').then(m => m.DEPARTAMENTOS_ROUTES),
      },
      {
        path: 'actividades',
        canActivate: [roleGuard(['ADMINISTRADOR'])],
        loadChildren: () => import('./features/actividades/actividades.routes').then(m => m.ACTIVIDADES_ROUTES),
      },
      {
        path: 'politicas',
        canActivate: [roleGuard(['ADMINISTRADOR'])],
        loadChildren: () => import('./features/politicas/politicas.routes').then(m => m.POLITICAS_ROUTES),
      },
      {
        path: 'diagramas',
        canActivate: [roleGuard(['ADMINISTRADOR'])],
        loadChildren: () => import('./features/diagramas/diagramas.routes').then(m => m.DIAGRAMAS_ROUTES),
      },
      {
        path: 'metricas',
        canActivate: [roleGuard(['ADMINISTRADOR', 'FUNCIONARIO'])],
        loadChildren: () => import('./features/metricas/metricas.routes').then(m => m.METRICAS_ROUTES),
      },
      {
        path: 'historial',
        canActivate: [roleGuard(['ADMINISTRADOR'])],
        loadChildren: () => import('./features/historial/historial.routes').then(m => m.HISTORIAL_ROUTES),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
```

### `core/guards/role.guard.ts`

```typescript
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export function roleGuard(rolesPermitidos: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (rolesPermitidos.includes(auth.rolActual())) return true;
    router.navigate(['/dashboard']);
    return false;
  };
}
```

---

## 5. Pasos de migración

### Paso A — Crear estructura de `features/` y `layout/`

```bash
cd src/app
mkdir -p features layout
mkdir -p features/auth features/dashboard features/tramites/pages features/tramites/components
mkdir -p features/expediente/pages features/diagramas features/politicas features/departamentos
mkdir -p features/actividades features/usuarios features/metricas features/historial
```

### Paso B — Mover por feature, uno a uno

**Importante:** mover **un feature por commit**. Después de cada movida, verificar que la pantalla sigue funcionando antes de continuar.

#### Ejemplo: mover `funcionario/tramites-lista` a `features/tramites/pages/tramites-lista.page.*`

1. Mover los archivos `.ts`, `.html`, `.scss` (en VS Code: arrastrar; o `git mv`)
2. Renombrar archivos: `tramites-lista.component.*` → `tramites-lista.page.*`
3. Actualizar el `selector` si lo tenía: `app-tramites-lista` → `app-tramites-lista-page` (o quitarlo si solo se usa por router)
4. Renombrar la clase: `TramitesListaComponent` → `TramitesListaPage`
5. Actualizar todos los imports rotos (VS Code suele detectarlos automáticamente al guardar)
6. Crear `features/tramites/tramites.routes.ts` apuntando al archivo movido
7. Quitar la entrada vieja en `app.routes.ts` y agregar la nueva
8. Levantar la app y probar la pantalla

#### Orden sugerido

1. `auth/login` → `features/auth/login` (más aislado)
2. `admin/dashboard` → `features/dashboard`
3. `admin/usuarios` → `features/usuarios`
4. `admin/departamentos`, `admin/actividades`, `admin/politicas` → cada uno a su feature
5. `admin/diagramas` → `features/diagramas`
6. `admin/historial`, `admin/metricas` → cada uno a su feature
7. `funcionario/bandeja-entrada`, `funcionario/tramites-lista`, `funcionario/tramite-detalle` → todos a `features/tramites/pages`
8. `funcionario/expediente-digital` → `features/expediente`
9. `shared/navbar`, `shared/sidebar` → `layout/`

### Paso C — Eliminar carpetas viejas

Cuando estén vacías:
```bash
rmdir admin/ funcionario/ auth/  # si están vacías
```

Si no están vacías, identificar qué quedó adentro y moverlo.

---

## 6. Verificación

Lista de comprobación funcional:

| Acceso | Pantalla esperada | Funciona |
|--------|-------------------|----------|
| `/dashboard` | Dashboard | [ ] |
| `/login` | Login | [ ] |
| `/tramites` (cliente o func) | Lista de trámites | [ ] |
| `/tramites/bandeja` (func) | Bandeja | [ ] |
| `/tramites/abc123` | Detalle | [ ] |
| `/expediente/abc123` | Expediente | [ ] |
| `/usuarios` (admin) | CRUD usuarios | [ ] |
| `/departamentos` (admin) | CRUD departamentos | [ ] |
| `/politicas` (admin) | CRUD políticas | [ ] |
| `/diagramas` (admin) | Editor de diagramas | [ ] |
| `/metricas` (admin) | Métricas | [ ] |
| `/historial` (admin) | Historial | [ ] |

| Caso de seguridad | Esperado |
|-------------------|----------|
| Funcionario accede a `/usuarios` | Redirige a dashboard |
| Cliente accede a `/diagramas` | Redirige a dashboard |
| Sin login accede a `/tramites` | Redirige a login |

---

## 7. Commit sugerido (varios)

```bash
# Por cada feature movido:
git add .
git commit -m "refactor(angular): mover X a features/X"

# Al final:
git commit -m "refactor(angular): consolidar app.routes.ts con features/ y guards por rol"
```

---

## Próximo paso

Continuar con **`10_validacion_final.md`**.
