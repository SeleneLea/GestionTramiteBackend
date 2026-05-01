# Fase 3.9 · Validación final de la fase 3

> Cerrar la fase 3 verificando que la app Flutter tiene un widget kit coherente y la organización por features funciona end-to-end.

---

## 1. Checklist de estructura

### 1.1 Carpetas

- [ ] `lib/core/env/environment.dart`
- [ ] `lib/core/http/http_client_service.dart`
- [ ] `lib/core/storage/storage_service.dart`
- [ ] `lib/core/utils/estado_utils.dart`
- [ ] `lib/core/utils/fecha_utils.dart`
- [ ] `lib/widgets/estado_badge.dart`
- [ ] `lib/widgets/progreso_bar.dart`
- [ ] `lib/widgets/tramite_card.dart` + `tramite_card_data.dart`
- [ ] `lib/widgets/form_dinamico.dart` + `form_dinamico_types.dart`
- [ ] `lib/widgets/timeline_custom.dart` + `timeline_custom_types.dart`
- [ ] `lib/features/auth/{pages,services}/`
- [ ] `lib/features/tramites/{pages,services}/`
- [ ] `lib/features/comunicacion/{pages,services}/`
- [ ] `lib/features/dashboard/pages/`
- [ ] `lib/features/home/pages/`
- [ ] `lib/features/profile/pages/`

### 1.2 Carpetas eliminadas / vacías

- [ ] `lib/screens/` — eliminada
- [ ] `lib/services/` — vacía o eliminada (excepto si queda algo no migrado)
- [ ] `lib/config/` — eliminada (movida a `core/env/`)

---

## 2. Checklist de no-duplicación

```bash
cd "c:/Users/Isael Ortiz/Documents/1PSW1/mobile/lib"

# Lógica de color de estado fuera de core/utils (debe ser cero)
grep -rn "case 'Aprobado'" --include="*.dart" | grep -v core/utils

# LinearProgressIndicator inline en pantallas (debe ser cero, todo va por ProgresoBar)
grep -rn "LinearProgressIndicator" --include="*.dart" | grep -v widgets/

# _formatoFecha residual (debe ser cero, todo va por fecha_utils.dart)
grep -rn "_formatoFecha" --include="*.dart" | grep -v core/utils

# Cards de trámite hechos a mano
grep -rln "tramite.codigo" --include="*.dart" lib/features/tramites/pages | xargs grep -l "Card("
```

Las búsquedas deben tener el menor número posible de coincidencias. Cualquier residuo se anota como deuda.

---

## 3. `flutter analyze`

```bash
flutter analyze
```
Debe pasar sin errores ni warnings nuevos.

---

## 4. Tests funcionales E2E (en emulador o dispositivo)

### Como cliente
- [ ] Login con `cliente@cre.bo`
- [ ] `Mis trámites` muestra cards con badge + progreso correctos
- [ ] Trámites cerrados muestran 100 % y badge verde/rojo
- [ ] Tap en card → detalle con estado, etapa, secciones, historial
- [ ] Pestaña Historial usa `TimelineCustom`
- [ ] Notificaciones cargan
- [ ] Logout

### Como funcionario (si la app lo soporta)
- [ ] Login con funcionario
- [ ] Bandeja de pendientes
- [ ] Detalle, completar sección con `FormDinamico`

---

## 5. Resumen ejecutivo

Crear `fase3/RESUMEN_EJECUTIVO.md`:

```markdown
# Fase 3 · Resumen ejecutivo

## Widgets reutilizables creados / consolidados
1. EstadoBadge
2. ProgresoBar
3. TramiteCard (+ TramiteCardData)
4. FormDinamico (+ tipos)
5. TimelineCustom (+ tipos)
6. ChatAgenteIa (preexistente)

## Reorganización
- screens/ → features/<dominio>/pages/
- services/ → features/<dominio>/services/ + core/{http,storage,env}/
- Lógica de UI consolidada en core/utils/

## Métricas
- Líneas de duplicación eliminadas: ~XXX
- Pantallas migradas: 14 / 14
- Widgets propios consumidos por feature: tramites usa 5 widgets propios

## Deudas pendientes
- (lista corta, si la hay)
```

---

## 6. Tag final

```bash
git tag fin-fase3
git push origin refactor/componentes-flutter
git push origin fin-fase3
```

---

## 7. Decisión de merge

Si todo verde: mergear `refactor/componentes-flutter` a `main`.

---

## 8. Estado del proyecto al cerrar fase 3

- ✅ **Backend Spring Boot** componentizado por bounded contexts con Ports (fase 1)
- ✅ **Frontend Angular** con UI kit propio + features por dominio (fase 2)
- ✅ **App Flutter** con widget kit propio + features por dominio (fase 3)
- ⏭️ **Fase 4 — Validación arquitectónica:** UML de componentes, despliegue, ArchUnit, ADRs

Las tres capas tienen la **misma estructura mental**: features por dominio + componentes/widgets/Ports reutilizables + utilidades transversales.

---

## 9. Próximo paso

Continuar con **fase 4** — la validación arquitectónica formal con diagramas UML y tests de arquitectura. Esa es la fase que el profesor evaluará más fuerte porque es donde se demuestra que la "arquitectura basada en componentes" es real y verificable.
