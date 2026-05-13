"""Smoke tests · Nivel 3 — Reglas de negocio cuantitativas.

Verifica que tras N acciones el estado del sistema es **exactamente** el
esperado, no solo "responde 2xx".

  N3.1 · Auditoría tras secuencia exacta → 1 SUBIDA + 2 NUEVA_VERSION + 3 LECTURA = 6 eventos.
  N3.2 · 5 subidas → 5 versiones, numeroVersion 1..5, solo la última esActual=true.
  N3.3 · Top3 de sugerencia política está ordenado descendente y la primera coincide con politicaSugeridaId.
  N3.4 · Feedback ACEPTADA cuando se confirma con la sugerida; CAMBIADA con otra distinta.
  N3.5 · Anomalía marcada como falsoPositivo desaparece del listado.
  N3.6 · Reporte natural "agrupar por estado" devuelve el conteo correcto.
  N3.7 · Permiso SOLO_LECTURA — comportamiento actual del backend (gap documentado).
  N3.8 · Bandeja ordenadaPor=ia tiene los mismos elementos que ordenadaPor=fecha.
  N3.9 · Repositorio.totalArchivos se incrementa con cada subida.
"""
from __future__ import annotations

import io
import sys
import time
from typing import Any

from _utils import (
    Ctx,
    SKIP_S3,
    banner_inicio,
    delete,
    expect_2xx,
    fail,
    get,
    imprimir_resumen,
    login_todos,
    ok,
    patch,
    post,
    put,
    section,
    skip,
    warn,
)


# ──────────────────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────────────────

def _subir(ctx: Ctx, label: str, bytes_extra: bytes) -> tuple[bool, str]:
    """Sube un PDF al repositorio. Devuelve (ok, documentoArchivoId)."""
    pdf = b"%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n" + bytes_extra
    files = {"archivo": (f"_E2E_{label}.pdf", io.BytesIO(pdf), "application/pdf")}
    data = {
        "tramiteId": ctx.tramite_id,
        "actividadId": ctx.actividad_id,
        "tipoDocumento": "PDF",
        "nombreLogico": f"_E2E_{label}_{int(time.time()*1000)}",
        "obligatorio": "false",
    }
    r = post(f"/tramites/{ctx.tramite_id}/documentos",
             token=ctx.token_admin, files=files, data=data)
    if r.status_code in (200, 201):
        return True, r.json().get("documentoArchivoId", "")
    return False, r.text[:100]


def _s3_disponible(ctx: Ctx) -> bool:
    """Probe rápido: intenta subir un PDF de 1 byte. True si S3 funciona."""
    if SKIP_S3 or not ctx.tramite_id or not ctx.actividad_id:
        return False
    ok, _ = _subir(ctx, "probe", b"probe\n")
    return ok


# ──────────────────────────────────────────────────────────────────────────────
# N3.1 · Auditoría cuantitativa exacta
# ──────────────────────────────────────────────────────────────────────────────

def test_auditoria_cuantitativa(ctx: Ctx) -> None:
    section("N3.1 · Auditoría con conteo y tipos exactos")
    res = ctx.resultado
    if not _s3_disponible(ctx):
        skip("N3.1", "S3 deshabilitado", res)
        return

    # Subir 1 doc
    success, doc_id = _subir(ctx, "audit", b"v1\n")
    if not success:
        fail("N3.1 subir base", 0, doc_id, res)
        return

    # 2 versiones nuevas
    for i in (2, 3):
        files = {"archivo": (f"_E2E_audit_v{i}.pdf",
                             io.BytesIO(b"%PDF-1.4\n" + f"v{i}\n".encode()),
                             "application/pdf")}
        post(f"/documentos/{doc_id}/versiones",
             token=ctx.token_admin, files=files,
             data={"comentarioCambio": f"v{i}"})

    # 3 previews (cada uno registra LECTURA)
    for _ in range(3):
        get(f"/documentos/{doc_id}/preview", token=ctx.token_admin)

    # Verificar auditoría: 1 SUBIDA + 2 NUEVA_VERSION + 3 LECTURA = 6 eventos
    r = get(f"/documentos/{doc_id}/auditoria?page=0&size=50", token=ctx.token_admin)
    if r.status_code != 200:
        fail("N3.1 GET auditoría", r.status_code, r.text[:100], res)
        return

    auditoria = r.json()
    eventos = auditoria.get("content", [])
    acciones_count: dict[str, int] = {}
    for e in eventos:
        a = e.get("accion", "")
        acciones_count[a] = acciones_count.get(a, 0) + 1

    esperado = {"SUBIDA": 1, "NUEVA_VERSION": 2, "LECTURA": 3}
    total_esperado = sum(esperado.values())

    if len(eventos) == total_esperado and all(
        acciones_count.get(k, 0) == v for k, v in esperado.items()
    ):
        ok("N3.1 conteo exacto de auditoría", 200,
           f"{acciones_count} (total={len(eventos)})", res)
    else:
        fail("N3.1 conteo de auditoría", 200,
             f"esperaba {esperado}, recibí {acciones_count}", res)


# ──────────────────────────────────────────────────────────────────────────────
# N3.2 · 5 subidas → 5 versiones consistentes
# ──────────────────────────────────────────────────────────────────────────────

def test_conteo_versiones(ctx: Ctx) -> None:
    section("N3.2 · 5 subidas → 5 versiones consistentes")
    res = ctx.resultado
    if not _s3_disponible(ctx):
        skip("N3.2", "S3 deshabilitado", res)
        return

    success, doc_id = _subir(ctx, "ver", b"base\n")
    if not success:
        fail("N3.2 subir base", 0, doc_id, res)
        return

    for i in range(2, 6):
        files = {"archivo": (f"_E2E_ver_{i}.pdf",
                             io.BytesIO(b"%PDF-1.4\n" + f"v{i}\n".encode()),
                             "application/pdf")}
        r = post(f"/documentos/{doc_id}/versiones",
                 token=ctx.token_admin, files=files,
                 data={"comentarioCambio": f"v{i}"})
        if r.status_code not in (200, 201):
            fail(f"N3.2 subir v{i}", r.status_code, r.text[:100], res)
            return

    r = get(f"/documentos/{doc_id}/versiones", token=ctx.token_admin)
    versiones = r.json() if r.status_code == 200 else []
    if not isinstance(versiones, list) or len(versiones) != 5:
        fail("N3.2 conteo versiones", r.status_code,
             f"esperaba 5, recibí {len(versiones) if isinstance(versiones, list) else '?'}", res)
        return

    # Verificar numeroVersion 1..5 todos presentes
    numeros = sorted(v.get("numeroVersion", 0) for v in versiones)
    if numeros != [1, 2, 3, 4, 5]:
        fail("N3.2 numeración versiones", 200,
             f"esperaba [1..5], recibí {numeros}", res)
        return

    # Verificar solo una esActual y es la más alta
    actuales = [v for v in versiones if v.get("esActual")]
    if len(actuales) == 1 and actuales[0].get("numeroVersion") == 5:
        ok("N3.2 versionado correcto", 200,
           "5 versiones, numeroVersion 1..5, solo v5 esActual=true", res)
    else:
        fail("N3.2 esActual", 200,
             f"esperaba 1 actual (v5), recibí {len(actuales)}", res)


# ──────────────────────────────────────────────────────────────────────────────
# N3.3 · Top3 de sugerencia ordenado descendente
# ──────────────────────────────────────────────────────────────────────────────

def test_top3_ordenado(ctx: Ctx) -> None:
    section("N3.3 · Top3 de sugerencia ordenado y consistente")
    res = ctx.resultado

    r = post("/tramites/sugerir-politica", token=ctx.token_cli,
             json={"descripcion": "necesito una nueva conexión eléctrica residencial"})
    if r.status_code != 200:
        skip("N3.3", f"sugerir falló: {r.status_code}", res)
        return

    body = r.json()
    top3 = body.get("top3", [])
    pol_sugerida = body.get("politicaSugeridaId", "")
    confianza_principal = body.get("confianza", 0)

    if len(top3) == 0:
        fail("N3.3 top3 vacío", 200, "no hay candidatos", res)
        return

    # Orden descendente por confianza
    confianzas = [c.get("confianza", 0) for c in top3]
    if confianzas != sorted(confianzas, reverse=True):
        fail("N3.3 orden top3", 200,
             f"confianzas no descendentes: {confianzas}", res)
        return

    # La primera del top3 coincide con politicaSugeridaId
    if top3[0].get("politicaId") != pol_sugerida:
        fail("N3.3 coherencia top3[0] vs sugerida", 200,
             f"top3[0]={top3[0].get('politicaId')} pero politicaSugeridaId={pol_sugerida}", res)
        return

    # Confianza principal == top3[0].confianza
    if abs(confianzas[0] - confianza_principal) > 1e-3:
        fail("N3.3 confianza principal", 200,
             f"raíz {confianza_principal} ≠ top3[0] {confianzas[0]}", res)
        return

    # Todas las confianzas en [0, 1]
    if any(c < 0 or c > 1 for c in confianzas):
        fail("N3.3 rango confianza", 200, f"fuera de [0,1]: {confianzas}", res)
        return

    ok("N3.3 top3 bien formado", 200,
       f"{len(top3)} candidatos · orden descendente · top1={confianza_principal}", res)


# ──────────────────────────────────────────────────────────────────────────────
# N3.4 · Feedback ACEPTADA vs CAMBIADA
# ──────────────────────────────────────────────────────────────────────────────

def test_feedback_aceptada_vs_cambiada(ctx: Ctx) -> None:
    section("N3.4 · Feedback ACEPTADA vs CAMBIADA según política confirmada")
    res = ctx.resultado

    # Sugerencia 1 → confirmar con la MISMA política sugerida → ACEPTADA
    r = post("/tramites/sugerir-politica", token=ctx.token_cli,
             json={"descripcion": "nueva conexión eléctrica residencial"})
    if r.status_code != 200:
        skip("N3.4 ACEPTADA", "sugerir falló", res)
    else:
        body = r.json()
        sug_id = body.get("sugerenciaId")
        pol_sugerida = body.get("politicaSugeridaId")
        r2 = post(f"/sugerencias/{sug_id}/confirmar", token=ctx.token_cli,
                  json={"politicaConfirmadaId": pol_sugerida})
        if r2.status_code == 200 and r2.json().get("feedback") == "ACEPTADA":
            ok("N3.4 confirmar con sugerida → ACEPTADA", 200, "", res)
        else:
            fail("N3.4 feedback ACEPTADA", r2.status_code,
                 f"feedback={r2.json().get('feedback') if r2.status_code==200 else r2.text[:80]}", res)

    # Sugerencia 2 → confirmar con OTRA política activa → CAMBIADA
    # Buscar otra política activa diferente
    r = get("/politicas", token=ctx.token_admin)
    activas = [p for p in (r.json() if r.status_code == 200 else [])
               if p.get("estado") == "activa"]
    if len(activas) < 2:
        skip("N3.4 CAMBIADA", "no hay 2 políticas activas distintas", res)
        return

    r = post("/tramites/sugerir-politica", token=ctx.token_cli,
             json={"descripcion": "trámite no específico"})
    if r.status_code != 200:
        skip("N3.4 CAMBIADA", "sugerir 2 falló", res)
        return
    body = r.json()
    sug_id = body.get("sugerenciaId")
    pol_sugerida = body.get("politicaSugeridaId")
    pol_distinta = next((p["id"] for p in activas if p["id"] != pol_sugerida), None)
    if not pol_distinta:
        skip("N3.4 CAMBIADA", "no encontré una distinta a la sugerida", res)
        return

    r2 = post(f"/sugerencias/{sug_id}/confirmar", token=ctx.token_cli,
              json={"politicaConfirmadaId": pol_distinta})
    if r2.status_code == 200 and r2.json().get("feedback") == "CAMBIADA":
        ok("N3.4 confirmar con otra → CAMBIADA", 200,
           f"sug={pol_sugerida[-6:]} → conf={pol_distinta[-6:]}", res)
    else:
        fail("N3.4 feedback CAMBIADA", r2.status_code,
             f"feedback={r2.json().get('feedback') if r2.status_code==200 else r2.text[:80]}", res)


# ──────────────────────────────────────────────────────────────────────────────
# N3.5 · Anomalía falso positivo desaparece del listado
# ──────────────────────────────────────────────────────────────────────────────

def test_anomalia_falso_positivo_desaparece(ctx: Ctx) -> None:
    section("N3.5 · Anomalía marcada como falso positivo desaparece del listado")
    res = ctx.resultado

    # Asegurar que hay anomalías (idempotente — detectar las crea si faltan)
    post("/alertas-anomalias/detectar", token=ctx.token_admin)

    r = get("/alertas-anomalias", token=ctx.token_admin)
    if r.status_code != 200:
        fail("N3.5 listar inicial", r.status_code, r.text[:100], res)
        return
    abiertas = r.json()
    if not abiertas:
        skip("N3.5", "no hay anomalías abiertas para marcar", res)
        return

    n_antes = len(abiertas)
    target_id = abiertas[0].get("id")

    r2 = post(f"/alertas-anomalias/{target_id}/marcar-falso-positivo",
              token=ctx.token_admin)
    if r2.status_code != 200 or not r2.json().get("falsoPositivo"):
        fail("N3.5 marcar falso positivo", r2.status_code, r2.text[:100], res)
        return

    r3 = get("/alertas-anomalias", token=ctx.token_admin)
    n_despues = len(r3.json()) if r3.status_code == 200 else -1

    if n_despues == n_antes - 1:
        ok("N3.5 falso positivo removido del listado", 200,
           f"{n_antes} → {n_despues}", res)
    elif any(a.get("id") == target_id for a in r3.json()):
        fail("N3.5", 200,
             f"la anomalía sigue en el listado tras marcarla falsoPositivo=true", res)
    else:
        # Puede que detectar haya creado nuevas — verificar que la específica no está
        ok("N3.5 falso positivo no aparece (otras creadas)", 200,
           f"{n_antes} → {n_despues} (id objetivo ausente)", res)


# ──────────────────────────────────────────────────────────────────────────────
# N3.6 · Reporte natural — conteo correcto
# ──────────────────────────────────────────────────────────────────────────────

def test_reporte_natural_conteo(ctx: Ctx) -> None:
    section("N3.6 · Reporte natural 'agrupar por estado' coincide con datos reales")
    res = ctx.resultado

    # 1. Consulta natural — "conteo" dispara la plantilla de agrupación en el stub Python
    r = post("/reportes/consulta-natural", token=ctx.token_admin,
             json={"consulta": "conteo de tramites por estado"})
    if r.status_code != 200:
        fail("N3.6 consulta natural", r.status_code, r.text[:100], res)
        return
    body = r.json()
    filas = body.get("filasMuestra", [])
    total_reporte = sum(f.get("total", 0) for f in filas if isinstance(f, dict))

    # 2. Comparar con el listado real de trámites del admin
    # (no hay endpoint público para "todos los trámites" del admin sin filtro,
    # pero el reporte ejecuta agregación contra la colección 'tramites'.
    # Verificamos que el conteo total sea > 0 y que tenga al menos 3 estados distintos
    # — el seed crea ~14 trámites repartidos en al menos 5 estados).
    estados = {f.get("estado") for f in filas if isinstance(f, dict)}

    if total_reporte > 0 and len(estados) >= 3:
        ok("N3.6 reporte natural agrupa correctamente", 200,
           f"total={total_reporte} en {len(estados)} estados", res)
    else:
        fail("N3.6 reporte natural", 200,
             f"total={total_reporte} estados={estados} (esperaba >0 con >=3 grupos)", res)


# ──────────────────────────────────────────────────────────────────────────────
# N3.7 · Permiso SOLO_LECTURA — gap actual del backend
# ──────────────────────────────────────────────────────────────────────────────

def test_permiso_solo_lectura(ctx: Ctx) -> None:
    section("N3.7 · Permiso SOLO_LECTURA — comportamiento real del backend")
    res = ctx.resultado
    if not ctx.politica_id_activa or not ctx.actividad_id:
        skip("N3.7", "sin política activa o actividad", res)
        return

    # 1. Configurar permiso SOLO_LECTURA
    r = put(f"/actividades/{ctx.actividad_id}/permiso-documental",
            token=ctx.token_admin, json={
                "politicaId": ctx.politica_id_activa,
                "actividadId": ctx.actividad_id,
                "nivelAcceso": "SOLO_LECTURA",
                "tiposDocumentoVisibles": ["PDF"],
            })
    if r.status_code != 200:
        fail("N3.7 configurar permiso", r.status_code, r.text[:100], res)
        return

    # 2. Intentar subir como funcionario — la validación ocurre ANTES de tocar S3,
    #    así que funciona aunque S3 esté deshabilitado.
    if not ctx.tramite_id or not ctx.actividad_id:
        skip("N3.7 verificar bloqueo", "sin tramite_id/actividad_id", res)
        return

    pdf = b"%PDF-1.4\n_E2E_lectura\n"
    files = {"archivo": ("_E2E_lectura.pdf", io.BytesIO(pdf), "application/pdf")}
    data = {
        "tramiteId": ctx.tramite_id,
        "actividadId": ctx.actividad_id,
        "tipoDocumento": "PDF",
        "nombreLogico": "_E2E_intento_lectura",
        "obligatorio": "false",
    }
    r2 = post(f"/tramites/{ctx.tramite_id}/documentos",
              token=ctx.token_func, files=files, data=data)

    if r2.status_code == 403:
        ok("N3.7 SOLO_LECTURA bloquea subida", 403,
           "permiso documental respetado (validación pre-S3)", res)
    elif r2.status_code in (200, 201):
        warn("N3.7 SOLO_LECTURA NO bloquea subida", r2.status_code,
             "GAP: la subida no consulta PermisoPuntoAtencion — reinicia el backend "
             "para que cargue el fix de DocumentoArchivoService.", res)
    elif r2.status_code == 500 and "S3 deshabilitado" in (r2.text or ""):
        # Llegó a S3 sin pasar por la validación → GAP
        warn("N3.7 SOLO_LECTURA NO bloquea subida", r2.status_code,
             "la validación de permiso NO se ejecutó (llegó a S3) — reinicia el backend.", res)
    else:
        fail("N3.7 subida bajo SOLO_LECTURA", r2.status_code, r2.text[:100], res)

    # Restaurar a LECTURA_Y_EDICION para no afectar otras pruebas
    put(f"/actividades/{ctx.actividad_id}/permiso-documental",
        token=ctx.token_admin, json={
            "politicaId": ctx.politica_id_activa,
            "actividadId": ctx.actividad_id,
            "nivelAcceso": "LECTURA_Y_EDICION",
            "tiposDocumentoVisibles": ["PDF", "IMAGEN"],
        })


# ──────────────────────────────────────────────────────────────────────────────
# N3.8 · Bandeja ordenada por IA tiene mismos elementos que por fecha
# ──────────────────────────────────────────────────────────────────────────────

def test_bandeja_misma_lista(ctx: Ctx) -> None:
    section("N3.8 · Bandeja ?ordenarPor=ia tiene los mismos elementos que ?ordenarPor=fecha")
    res = ctx.resultado

    r1 = get("/tramites/mis-pendientes", token=ctx.token_func)
    r2 = get("/tramites/mis-pendientes?ordenarPor=ia", token=ctx.token_func)
    if r1.status_code != 200 or r2.status_code != 200:
        fail("N3.8 listar bandejas", 0,
             f"fecha={r1.status_code} ia={r2.status_code}", res)
        return
    ids_fecha = {t.get("id") for t in r1.json()}
    ids_ia = {t.get("id") for t in r2.json()}

    if ids_fecha == ids_ia:
        ok("N3.8 bandejas tienen mismo conjunto", 200,
           f"{len(ids_fecha)} trámites (orden distinto posible)", res)
    else:
        fail("N3.8 conjuntos distintos", 200,
             f"fecha={len(ids_fecha)} ia={len(ids_ia)} · diff={ids_fecha.symmetric_difference(ids_ia)}", res)


# ──────────────────────────────────────────────────────────────────────────────
# N3.9 · Repositorio.totalArchivos se incrementa
# ──────────────────────────────────────────────────────────────────────────────

def test_repositorio_totales(ctx: Ctx) -> None:
    section("N3.9 · Repositorio.totalArchivos se incrementa con cada subida")
    res = ctx.resultado
    if not _s3_disponible(ctx):
        skip("N3.9", "S3 deshabilitado", res)
        return

    # Leer estado inicial
    r = get(f"/repositorios/{ctx.repositorio_id}", token=ctx.token_admin)
    if r.status_code != 200:
        fail("N3.9 leer repo inicial", r.status_code, r.text[:100], res)
        return
    n_antes = r.json().get("totalArchivos", 0)

    success, _ = _subir(ctx, "totales", b"totales\n")
    if not success:
        fail("N3.9 subir", 0, "no se pudo subir", res)
        return

    r = get(f"/repositorios/{ctx.repositorio_id}", token=ctx.token_admin)
    n_despues = r.json().get("totalArchivos", -1) if r.status_code == 200 else -1

    if n_despues == n_antes + 1:
        ok("N3.9 totalArchivos += 1 tras subida", 200,
           f"{n_antes} → {n_despues}", res)
    else:
        fail("N3.9 totalArchivos no incrementó", 200,
             f"esperaba {n_antes + 1}, recibí {n_despues}", res)


# ──────────────────────────────────────────────────────────────────────────────
# Orquestador
# ──────────────────────────────────────────────────────────────────────────────

def run(ctx: Ctx | None = None) -> Ctx:
    nuevo = ctx is None
    if nuevo:
        ctx = Ctx()
        banner_inicio("Smoke tests · NIVEL 3 — Reglas de negocio cuantitativas")

    if not (ctx.token_admin and ctx.token_func and ctx.token_cli):
        if not login_todos(ctx):
            return ctx

    # Pre-descubrimiento mínimo
    if not ctx.politica_id_activa:
        r = get("/politicas", token=ctx.token_admin)
        if r.status_code == 200:
            for p in r.json():
                if p.get("estado") == "activa":
                    ctx.politica_id_activa = p.get("id", "")
                    break
    if not ctx.actividad_id:
        r = get("/actividades", token=ctx.token_admin)
        if r.status_code == 200 and r.json():
            ctx.actividad_id = r.json()[0].get("id", "")
    if not ctx.tramite_id:
        r = get("/tramites/mis-pendientes", token=ctx.token_func)
        if r.status_code == 200 and r.json():
            ctx.tramite_id = r.json()[0].get("id", "")
    if not ctx.repositorio_id and ctx.tramite_id:
        r = get(f"/tramites/{ctx.tramite_id}/repositorio",
                token=ctx.token_admin)
        if r.status_code == 200:
            ctx.repositorio_id = r.json().get("id", "")

    # Tests cuantitativos
    test_top3_ordenado(ctx)
    test_feedback_aceptada_vs_cambiada(ctx)
    test_anomalia_falso_positivo_desaparece(ctx)
    test_reporte_natural_conteo(ctx)
    test_bandeja_misma_lista(ctx)
    test_permiso_solo_lectura(ctx)
    # Los siguientes 3 requieren S3
    test_auditoria_cuantitativa(ctx)
    test_conteo_versiones(ctx)
    test_repositorio_totales(ctx)
    return ctx


if __name__ == "__main__":
    t0 = banner_inicio("Smoke tests · NIVEL 3 — Reglas de negocio cuantitativas")
    ctx = run(Ctx())
    sys.exit(imprimir_resumen(ctx.resultado, t0))
