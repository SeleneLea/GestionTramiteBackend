# Fase 2.10 · Validación final de la fase 2

> Cerrar la fase 2 verificando que el frontend Angular tiene un UI kit coherente y la organización por features funciona end-to-end.

---

## 1. Checklist de estructura

### 1.1 Carpetas existentes

- [ ] `src/app/core/` — guards, interceptors, models, services, **utils** (nuevo)
- [ ] `src/app/ui-kit/` — con todos los componentes:
  - [ ] `badge-estado/`
  - [ ] `progress-bar/`
  - [ ] `card-tramite/`
  - [ ] `data-table/`
  - [ ] `form-dinamico/`
  - [ ] `timeline/`
  - [ ] `modal/` (movido desde shared)
  - [ ] `empty-state/` (movido desde shared)
  - [ ] `index.ts` (barrel)
- [ ] `src/app/layout/` — navbar, sidebar, shell
- [ ] `src/app/features/` — un feature por dominio:
  - [ ] auth, dashboard, tramites, expediente, diagramas, politicas, departamentos, actividades, usuarios, metricas, historial

### 1.2 Carpetas eliminadas

- [ ] `src/app/admin/` — eliminada o vacía
- [ ] `src/app/funcionario/` — eliminada o vacía
- [ ] `src/app/shared/` — eliminada o vacía (lo que quedaba se movió a layout/ o ui-kit/)

---

## 2. Checklist de no-duplicación

Buscar duplicación residual:

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/WEB_angular/src/app"

# Lógica de color de estado fuera de core/utils
grep -rn "case 'Aprobado'" --include="*.ts" | grep -v core/utils
grep -rn "case 'Rechazado'" --include="*.ts" | grep -v core/utils

# Tablas hechas a mano fuera del componente
grep -rln "<table" --include="*.html" | grep -v ui-kit

# Cards de trámite hechos a mano
grep -rln "tramite.codigo" --include="*.html" | xargs grep -l "<div class=\"card" 2>/dev/null
```

Esperado: cantidades **mínimas**, idealmente cero. Cualquier residuo se anota como deuda.

---

## 3. Test funcional E2E

Ejecutar manualmente:

### Como Cliente
- [ ] Login
- [ ] Ver mis trámites en `/tramites`
- [ ] Click en una card → abre detalle con timeline + progreso correcto
- [ ] Trámites cerrados muestran 100 % y badge verde/rojo
- [ ] Logout

### Como Funcionario
- [ ] Login
- [ ] Ver `/tramites/bandeja` con cards de pendientes
- [ ] Abrir detalle → ver expediente con form-dinamico
- [ ] Completar sección → trámite avanza
- [ ] Logout

### Como Administrador
- [ ] Login
- [ ] CRUD usuarios usando `<app-data-table>`
- [ ] CRUD departamentos usando `<app-data-table>`
- [ ] Editor de diagramas (`/diagramas`) funciona
- [ ] Generar diagrama por prompt funciona
- [ ] Ver `/historial` con timeline
- [ ] Logout

---

## 4. Test de accesos

Verificar que los guards de rol funcionan:

| Como rol | Accede a | Esperado |
|----------|----------|----------|
| Cliente | `/usuarios` | Redirigido a dashboard |
| Cliente | `/diagramas` | Redirigido a dashboard |
| Funcionario | `/usuarios` | Redirigido a dashboard |
| Funcionario | `/diagramas` | Redirigido a dashboard |
| Funcionario | `/tramites/bandeja` | OK |
| Admin | cualquier ruta | OK |
| Sin sesión | cualquier ruta protegida | Redirigido a login |

---

## 5. Verificación visual

Que el look se mantiene **igual o mejor** que antes:

- [ ] Login se ve correcto
- [ ] Dashboard se ve correcto
- [ ] Listas de trámites con cards alineados, badges con color correcto
- [ ] Detalle de trámite con timeline correcto
- [ ] Tablas de admin con paginación funcionando
- [ ] Modales (al borrar / confirmar) funcionan
- [ ] Toasts / notificaciones internas funcionan

---

## 6. Lighthouse / build de producción

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/WEB_angular"
npm run build
```

Debe terminar sin errores. Verificar tamaño del bundle:

```bash
# Si el build de Angular emite reportes:
ls dist/
```

> **Nota:** después de la fase 2, el bundle puede ser un poco menor por la deduplicación o un poco mayor por componentes nuevos. Cualquiera de los dos está bien si todo funciona.

---

## 7. Resumen ejecutivo

Crear `fase2/RESUMEN_EJECUTIVO.md` con:

```markdown
# Fase 2 · Resumen ejecutivo

## Componentes UI Kit creados / consolidados
1. app-badge-estado
2. app-progress-bar
3. app-card-tramite
4. app-data-table
5. app-form-dinamico
6. app-timeline
7. app-modal (movido)
8. app-empty-state (movido)
9. app-input-text / input-select (movido / refinado)

## Reorganización
- Eliminadas: admin/, funcionario/, shared/
- Creadas: features/ (11 features), ui-kit/, layout/, core/utils/

## Métricas
- Líneas de duplicación eliminadas: ~XXX
- Pantallas migradas: __ / __
- Componentes UI Kit consumidos por feature: ej. ui-kit/badge-estado usado en 7 pantallas

## Deudas pendientes
- (lista corta)
```

---

## 8. Tag final de fase 2

```bash
git tag fin-fase2
git push origin refactor/componentes-frontend
git push origin fin-fase2
```

---

## 9. Decisión de merge

Igual que en fase 1: si todo está verde, mergear `refactor/componentes-frontend` a `main`.

---

## 10. Siguientes pasos

- ✅ Fase 1 backend completa
- ✅ Fase 2 frontend Angular completa
- ⏭️ Fase 3 — Flutter widgets reutilizables (`guia_reorganizacion/fase3/`)
- ⏭️ Fase 4 — Validación arquitectónica (UML, ArchUnit, despliegue)
