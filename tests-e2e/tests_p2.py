"""Smoke tests · Parte 2 — gestión documental + IA proxy."""
from __future__ import annotations

import io
import os
import sys
import time
from typing import Any

import requests

from _utils import (
    Ctx,
    IA_URL,
    SKIP_S3,
    banner_inicio,
    delete,
    expect_2xx,
    fail,
    get,
    head_or_empty,
    imprimir_resumen,
    login_todos,
    ok,
    post,
    put,
    section,
    skip,
    warn,
)


# ──────────────────────────────────────────────────────────────────────────────
# Pre: descubrir IDs que necesitan los tests de Parte 2
# ──────────────────────────────────────────────────────────────────────────────

def descubrir(ctx: Ctx) -> None:
    """Carga IDs mínimos (política activa, trámite, sección) si no vienen del P1."""
    if not ctx.politica_id_activa:
        r = get("/politicas", token=ctx.token_admin)
        if r.status_code == 200:
            for p in r.json():
                if p.get("estado") == "activa":
                    ctx.politica_id_activa = p.get("id", "")
                    break
    if not ctx.politica_id:
        ctx.politica_id = ctx.politica_id_activa

    if not ctx.tramite_id:
        r = get("/tramites/mis-pendientes", token=ctx.token_func)
        ctx.tramite_id = head_or_empty(r.json() if r.status_code == 200 else [])

    if not ctx.seccion_id and ctx.tramite_id:
        r = get(f"/expedientes/tramite/{ctx.tramite_id}", token=ctx.token_admin)
        if r.status_code == 200:
            body = r.json()
            secs = body.get("secciones") if isinstance(body, dict) else []
            if secs and isinstance(secs[0], dict):
                info = secs[0].get("infoSeccion") or {}
                ctx.seccion_id = info.get("id", "")

    if not ctx.actividad_id:
        r = get("/actividades", token=ctx.token_admin)
        ctx.actividad_id = head_or_empty(r.json() if r.status_code == 200 else [])


# ──────────────────────────────────────────────────────────────────────────────
# Salud del microservicio Python
# ──────────────────────────────────────────────────────────────────────────────

def microservicio_arriba() -> bool:
    try:
        r = requests.get(f"{IA_URL}/healthz", timeout=3)
        return r.status_code == 200
    except requests.RequestException:
        return False


def test_microservicio_health(ctx: Ctx) -> bool:
    section("Microservicio IA (Python/FastAPI)")
    res = ctx.resultado
    try:
        r = requests.get(f"{IA_URL}/healthz", timeout=3)
        if r.status_code == 200:
            ok(f"GET {IA_URL}/healthz", r.status_code, r.json().get("status", "?"), res)
        else:
            warn(f"GET {IA_URL}/healthz", r.status_code, "no responde 200", res)
            return False
        r = requests.get(f"{IA_URL}/readyz", timeout=3)
        models = (r.json() or {}).get("models", {}) if r.status_code == 200 else {}
        if r.status_code == 200:
            stub_count = sum(1 for v in models.values() if v == "stub")
            ok(f"GET {IA_URL}/readyz", r.status_code,
               f"{stub_count}/{len(models)} en stub", res)
        return True
    except requests.RequestException as ex:
        skip("microservicio IA", f"no responde ({ex.__class__.__name__})", res)
        return False


# ──────────────────────────────────────────────────────────────────────────────
# CU-32 · Repositorio documental
# ──────────────────────────────────────────────────────────────────────────────

def test_cu32_repositorio(ctx: Ctx) -> None:
    section("CU-32 · Repositorio documental por trámite")
    res = ctx.resultado
    if not ctx.tramite_id:
        skip("CU-32", "no hay tramite_id", res)
        return

    r = get(f"/tramites/{ctx.tramite_id}/repositorio", token=ctx.token_admin)
    if r.status_code == 200:
        body = r.json()
        ctx.repositorio_id = body.get("id", "")
        ok("GET /tramites/{id}/repositorio", r.status_code,
           f"id={ctx.repositorio_id} archivos={body.get('totalArchivos','?')}", res)
    elif r.status_code in (400, 404):
        # El repositorio se crea al iniciar el trámite o de forma perezosa en la
        # primera subida; un trámite recién creado puede no tenerlo aún.
        skip("CU-32", "el trámite aún no tiene repositorio (se crea al subir)", res)
    else:
        fail("GET /tramites/{id}/repositorio", r.status_code, r.text[:120], res)


# ──────────────────────────────────────────────────────────────────────────────
# CU-33/34/35 · Subir + preview + nueva versión (requiere S3)
# ──────────────────────────────────────────────────────────────────────────────

def test_cu33_34_35_s3(ctx: Ctx) -> None:
    section("CU-33/34/35 · Subir documento + preview + nueva versión")
    res = ctx.resultado

    if SKIP_S3:
        skip("CU-33/34/35", "SKIP_S3=1", res)
        return
    if not ctx.tramite_id or not ctx.actividad_id:
        skip("CU-33/34/35", "sin tramite_id/actividad_id", res)
        return

    # Construir un PDF mínimo válido (4-byte header + body — suficiente para Content-Type)
    pdf_bytes = (
        b"%PDF-1.4\n"
        b"1 0 obj<<>>endobj\n"
        b"trailer<<>>\n"
        b"%%EOF\n"
    )

    files = {"archivo": ("_E2E_doc.pdf", io.BytesIO(pdf_bytes), "application/pdf")}
    data = {
        "tramiteId": ctx.tramite_id,
        "actividadId": ctx.actividad_id,
        "tipoDocumento": "PDF",
        "nombreLogico": f"_E2E_{int(time.time())}",
        "obligatorio": "false",
    }
    r = post(f"/tramites/{ctx.tramite_id}/documentos",
             token=ctx.token_admin, files=files, data=data)
    # S3 deshabilitado: backend lanza IllegalStateException → 500 con mensaje claro.
    # No es un bug, es config — reportar como WARN.
    if r.status_code == 500 and "S3" in (r.text or ""):
        warn("CU-33 POST /tramites/{id}/documentos", r.status_code,
             "S3 deshabilitado (aws.enabled=false)", res)
        return
    if r.status_code == 503:
        warn("CU-33 POST /tramites/{id}/documentos", r.status_code,
             "S3 caído o credenciales inválidas", res)
        return
    if r.status_code not in (200, 201):
        fail("CU-33 POST /tramites/{id}/documentos", r.status_code, r.text[:120], res)
        return
    body = r.json()
    ctx.documento_archivo_id = body.get("documentoArchivoId", "")
    ok("CU-33 subir documento", r.status_code,
       f"docId={ctx.documento_archivo_id} v{body.get('numeroVersion','?')}", res)

    # CU-34 · preview
    r = get(f"/documentos/{ctx.documento_archivo_id}/preview", token=ctx.token_admin)
    if r.status_code == 200:
        body = r.json()
        url = body.get("urlPreview", "")
        ok("CU-34 GET /documentos/{id}/preview", r.status_code,
           f"url={url[:60]}...", res)
    else:
        fail("CU-34 preview", r.status_code, r.text[:120], res)

    # CU-35 · nueva versión
    pdf_v2 = pdf_bytes + b"% v2\n"   # bytes distintos para evitar 409 por hash duplicado
    files = {"archivo": ("_E2E_doc_v2.pdf", io.BytesIO(pdf_v2), "application/pdf")}
    r = post(f"/documentos/{ctx.documento_archivo_id}/versiones",
             token=ctx.token_admin, files=files,
             data={"comentarioCambio": "_E2E_ test"})
    if r.status_code in (200, 201):
        body = r.json()
        ok("CU-35 POST /documentos/{id}/versiones", r.status_code,
           f"v{body.get('numeroVersion','?')}", res)
    else:
        fail("CU-35 nueva versión", r.status_code, r.text[:120], res)

    # listar versiones
    r = get(f"/documentos/{ctx.documento_archivo_id}/versiones", token=ctx.token_admin)
    expect_2xx("CU-35 GET /documentos/{id}/versiones", r, res,
               lambda b: f"{len(b)} versiones")


# ──────────────────────────────────────────────────────────────────────────────
# CU-36 · Permisos por punto de atención
# ──────────────────────────────────────────────────────────────────────────────

def test_cu36_permisos(ctx: Ctx) -> None:
    section("CU-36 · Permisos por punto de atención")
    res = ctx.resultado
    if not ctx.politica_id_activa or not ctx.actividad_id:
        skip("CU-36", "sin política activa o actividad", res)
        return

    payload = {
        "politicaId": ctx.politica_id_activa,
        "actividadId": ctx.actividad_id,
        "nivelAcceso": "LECTURA_Y_EDICION",
        "tiposDocumentoVisibles": ["PDF", "IMAGEN"],
    }
    r = put(f"/actividades/{ctx.actividad_id}/permiso-documental",
            token=ctx.token_admin, json=payload)
    expect_2xx("PUT /actividades/{id}/permiso-documental", r, res,
               lambda b: f"nivel={b.get('nivelAcceso','?')}")

    r = get(f"/politicas/{ctx.politica_id_activa}/permisos-documentales",
            token=ctx.token_admin)
    expect_2xx("GET /politicas/{id}/permisos-documentales", r, res,
               lambda b: f"{len(b) if isinstance(b, list) else 0} permisos")

    r = get(f"/politicas/{ctx.politica_id_activa}/actividades/{ctx.actividad_id}/permiso-documental",
            token=ctx.token_admin)
    expect_2xx("GET permiso por (política,actividad)", r, res,
               lambda b: f"nivel={b.get('nivelAcceso','?')}")


# ──────────────────────────────────────────────────────────────────────────────
# CU-37 · Auditoría
# ──────────────────────────────────────────────────────────────────────────────

def test_cu37_auditoria(ctx: Ctx) -> None:
    section("CU-37 · Auditoría de documento")
    res = ctx.resultado
    if not ctx.documento_archivo_id:
        skip("CU-37", "sin documento_archivo_id (CU-33 falló o S3 skip)", res)
        return

    r = get(f"/documentos/{ctx.documento_archivo_id}/auditoria?page=0&size=20",
            token=ctx.token_admin)
    expect_2xx("GET /documentos/{id}/auditoria", r, res,
               lambda b: f"total={b.get('totalElements','?')}")


# ──────────────────────────────────────────────────────────────────────────────
# CU-38 · Edición colaborativa (solo REST de roster — STOMP requiere cliente WS)
# ──────────────────────────────────────────────────────────────────────────────

def test_cu38_roster(ctx: Ctx) -> None:
    section("CU-38 · Roster de edición colaborativa (REST)")
    res = ctx.resultado
    if not ctx.documento_archivo_id:
        skip("CU-38", "sin documento_archivo_id", res)
        return
    r = get(f"/documentos/{ctx.documento_archivo_id}/sesion-edicion",
            token=ctx.token_func)
    expect_2xx("GET /documentos/{id}/sesion-edicion", r, res,
               lambda b: f"{len(b) if isinstance(b, list) else 0} participantes activos")


# ──────────────────────────────────────────────────────────────────────────────
# CU-39 · Dictar formulario (audio dummy)
# ──────────────────────────────────────────────────────────────────────────────

def test_cu39_dictar(ctx: Ctx) -> None:
    section("CU-39 · Dictar formulario por voz")
    res = ctx.resultado
    if not ctx.seccion_id:
        skip("CU-39", "sin seccion_id", res)
        return

    # WAV mínimo de 44+4 bytes
    wav = (
        b"RIFF\x28\x00\x00\x00WAVEfmt \x10\x00\x00\x00"
        b"\x01\x00\x01\x00\x40\x1f\x00\x00\x40\x1f\x00\x00"
        b"\x01\x00\x08\x00data\x04\x00\x00\x00\x00\x00\x00\x00"
    )
    files = {"audio": ("_E2E_audio.wav", io.BytesIO(wav), "audio/wav")}
    r = post(f"/expedientes/secciones/{ctx.seccion_id}/dictar",
             token=ctx.token_func, files=files)
    if r.status_code == 200:
        body = r.json()
        ok("POST /expedientes/secciones/{id}/dictar", r.status_code,
           f"campos={len(body.get('campos', []))}", res)
    elif r.status_code == 503:
        warn("CU-39 dictar", r.status_code, "microservicio IA caído", res)
    else:
        fail("CU-39 dictar", r.status_code, r.text[:120], res)


# ──────────────────────────────────────────────────────────────────────────────
# CU-40 · Sugerir política + confirmar
# ──────────────────────────────────────────────────────────────────────────────

def test_cu40_sugerencia(ctx: Ctx) -> None:
    section("CU-40 · Sugerir política")
    res = ctx.resultado

    payload = {"descripcion": "Necesito una nueva conexión eléctrica residencial"}
    r = post("/tramites/sugerir-politica", token=ctx.token_cli, json=payload)
    if r.status_code != 200:
        if r.status_code == 503:
            warn("POST /tramites/sugerir-politica", r.status_code,
                 "microservicio IA caído", res)
        else:
            fail("POST /tramites/sugerir-politica", r.status_code, r.text[:120], res)
        return

    body = r.json()
    ctx.sugerencia_id = body.get("sugerenciaId", "")
    politica_sug = body.get("politicaSugeridaId", "")
    ok("POST /tramites/sugerir-politica", r.status_code,
       f"sug={ctx.sugerencia_id} conf={body.get('confianza','?')}", res)

    if ctx.sugerencia_id and politica_sug:
        r = post(f"/sugerencias/{ctx.sugerencia_id}/confirmar",
                 token=ctx.token_cli,
                 json={"politicaConfirmadaId": politica_sug})
        expect_2xx("POST /sugerencias/{id}/confirmar", r, res,
                   lambda b: f"feedback={b.get('feedback','?')}")


# ──────────────────────────────────────────────────────────────────────────────
# CU-41 · Reporte por consulta natural
# ──────────────────────────────────────────────────────────────────────────────

def test_cu41_reporte_natural(ctx: Ctx) -> None:
    section("CU-41 · Reporte por consulta natural")
    res = ctx.resultado

    payload = {"consulta": "cuántos tramites agrupados por estado"}
    r = post("/reportes/consulta-natural", token=ctx.token_admin, json=payload)
    if r.status_code == 200:
        body = r.json()
        ok("POST /reportes/consulta-natural", r.status_code,
           f"collection={body.get('collection','?')} filas={body.get('totalFilas','?')}", res)
    elif r.status_code == 503:
        warn("CU-41 reporte natural", r.status_code, "microservicio IA caído", res)
    else:
        fail("CU-41 reporte natural", r.status_code, r.text[:120], res)


# ──────────────────────────────────────────────────────────────────────────────
# CU-42 · Ruta óptima
# ──────────────────────────────────────────────────────────────────────────────

def test_cu42_ruta_optima(ctx: Ctx) -> None:
    section("CU-42 · Ruta óptima del trámite")
    res = ctx.resultado
    if not ctx.tramite_id:
        skip("CU-42", "sin tramite_id", res)
        return
    r = post(f"/tramites/{ctx.tramite_id}/ruta-optima", token=ctx.token_func)
    if r.status_code == 200:
        body = r.json()
        ok("POST /tramites/{id}/ruta-optima", r.status_code,
           f"{len(body.get('rutaSugerida', []))} nodos · confianza={body.get('confianza','?')}", res)
    elif r.status_code == 503:
        warn("CU-42 ruta-óptima", r.status_code, "microservicio IA caído", res)
    else:
        fail("CU-42 ruta-óptima", r.status_code, r.text[:120], res)


# ──────────────────────────────────────────────────────────────────────────────
# CU-43 · Trámites en riesgo
# ──────────────────────────────────────────────────────────────────────────────

def test_cu43_riesgo(ctx: Ctx) -> None:
    section("CU-43 · Trámites en riesgo de demora")
    res = ctx.resultado
    r = get("/tramites/en-riesgo", token=ctx.token_func)
    if r.status_code == 200:
        body = r.json()
        ok("GET /tramites/en-riesgo", r.status_code,
           f"{len(body)} trámites analizados", res)
    elif r.status_code == 503:
        warn("CU-43 riesgo", r.status_code, "microservicio IA caído", res)
    else:
        fail("CU-43 riesgo", r.status_code, r.text[:120], res)


# ──────────────────────────────────────────────────────────────────────────────
# CU-44 · Bandeja ordenada por IA
# ──────────────────────────────────────────────────────────────────────────────

def test_cu44_bandeja_ia(ctx: Ctx) -> None:
    section("CU-44 · Bandeja ordenada por IA")
    res = ctx.resultado
    r = get("/tramites/mis-pendientes?ordenarPor=ia", token=ctx.token_func)
    expect_2xx("GET /tramites/mis-pendientes?ordenarPor=ia", r, res,
               lambda b: f"{len(b)} tramites (fallback a fecha si IA cae)")


# ──────────────────────────────────────────────────────────────────────────────
# CU-45 · Anomalías
# ──────────────────────────────────────────────────────────────────────────────

def test_cu45_anomalias(ctx: Ctx) -> None:
    section("CU-45 · Anomalías")
    res = ctx.resultado

    r = post("/alertas-anomalias/detectar", token=ctx.token_admin)
    if r.status_code == 200:
        body = r.json()
        ok("POST /alertas-anomalias/detectar", r.status_code,
           f"{len(body)} anomalías detectadas", res)
    elif r.status_code == 503:
        warn("CU-45 detectar", r.status_code, "microservicio IA caído", res)
    else:
        fail("CU-45 detectar", r.status_code, r.text[:120], res)

    r = get("/alertas-anomalias", token=ctx.token_admin)
    body = expect_2xx("GET /alertas-anomalias", r, res,
                      lambda b: f"{len(b)} alertas abiertas")
    if isinstance(body, list) and body:
        alerta_id = body[0].get("id", "")
        if alerta_id:
            r = post(f"/alertas-anomalias/{alerta_id}/marcar-falso-positivo",
                     token=ctx.token_admin)
            expect_2xx("POST /alertas-anomalias/{id}/marcar-falso-positivo", r, res,
                       lambda b: f"falsoPositivo={b.get('falsoPositivo','?')}")


# ──────────────────────────────────────────────────────────────────────────────
# Orquestador Parte 2
# ──────────────────────────────────────────────────────────────────────────────

def run(ctx: Ctx | None = None) -> Ctx:
    nuevo_ctx = ctx is None
    if nuevo_ctx:
        ctx = Ctx()
        banner_inicio("Smoke tests · BACKEND PARTE 2")

    if not (ctx.token_admin and ctx.token_func and ctx.token_cli):
        if not login_todos(ctx):
            return ctx

    descubrir(ctx)
    micro_ok = test_microservicio_health(ctx)

    # Documental
    test_cu32_repositorio(ctx)
    test_cu33_34_35_s3(ctx)
    test_cu36_permisos(ctx)
    test_cu37_auditoria(ctx)
    test_cu38_roster(ctx)

    # IA proxy — si el micro está caído, cada test reporta WARN
    test_cu39_dictar(ctx)
    test_cu40_sugerencia(ctx)
    test_cu41_reporte_natural(ctx)
    test_cu42_ruta_optima(ctx)
    test_cu43_riesgo(ctx)
    test_cu44_bandeja_ia(ctx)
    test_cu45_anomalias(ctx)
    return ctx


if __name__ == "__main__":
    t0 = banner_inicio("Smoke tests · BACKEND PARTE 2")
    ctx = run(Ctx())
    sys.exit(imprimir_resumen(ctx.resultado, t0))
