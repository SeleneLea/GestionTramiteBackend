"""Smoke tests · Parte 1 — todos los endpoints del backend pre-Parte 2."""
from __future__ import annotations

import sys
import time
from typing import Any

import requests

from _utils import (
    Ctx,
    banner_inicio,
    expect_2xx,
    fail,
    get,
    head_or_empty,
    imprimir_resumen,
    login_todos,
    ok,
    patch,
    post,
    put,
    delete,
    section,
    skip,
    warn,
)


# ──────────────────────────────────────────────────────────────────────────────
# 1. Auth + perfil
# ──────────────────────────────────────────────────────────────────────────────

def test_auth(ctx: Ctx) -> None:
    section("Auth + perfil")
    if not login_todos(ctx):
        return
    res = ctx.resultado

    r = get("/usuarios/me", token=ctx.token_admin)
    expect_2xx("GET /usuarios/me (admin)", r, res, lambda b: f"email={b.get('email','?')}")

    r = get("/usuarios/me", token=ctx.token_func)
    expect_2xx("GET /usuarios/me (funcionario)", r, res, lambda b: f"rol={b.get('rolId','?')}")

    r = get("/health", token=ctx.token_admin)
    expect_2xx("GET /health", r, res, lambda b: f"status={b.get('status','?')}")


# ──────────────────────────────────────────────────────────────────────────────
# 2. Catálogos (lecturas)
# ──────────────────────────────────────────────────────────────────────────────

def test_catalogos(ctx: Ctx) -> None:
    section("Catálogos: usuarios, roles, permisos, departamentos, actividades, documentos")
    res = ctx.resultado

    r = get("/usuarios", token=ctx.token_admin)
    body = expect_2xx("GET /usuarios", r, res, lambda b: f"{len(b)} usuarios")

    r = get("/usuarios/funcionarios", token=ctx.token_admin)
    expect_2xx("GET /usuarios/funcionarios", r, res, lambda b: f"{len(b)} funcionarios")

    r = get("/roles", token=ctx.token_admin)
    body = expect_2xx("GET /roles", r, res, lambda b: f"{len(b)} roles")
    ctx.rol_id = head_or_empty(body)

    r = get("/permisos", token=ctx.token_admin)
    expect_2xx("GET /permisos", r, res, lambda b: f"{len(b)} permisos")

    r = get("/departamentos", token=ctx.token_admin)
    body = expect_2xx("GET /departamentos", r, res, lambda b: f"{len(b)} departamentos")
    ctx.departamento_id = head_or_empty(body)

    r = get("/actividades", token=ctx.token_admin)
    body = expect_2xx("GET /actividades", r, res, lambda b: f"{len(b)} actividades")
    ctx.actividad_id = head_or_empty(body)

    r = get("/documentos", token=ctx.token_admin)
    body = expect_2xx("GET /documentos (catálogo legacy)", r, res, lambda b: f"{len(b)} docs")
    ctx.documento_catalogo_id = head_or_empty(body)


# ──────────────────────────────────────────────────────────────────────────────
# 3. Roles + Permisos (lecturas detalle)
# ──────────────────────────────────────────────────────────────────────────────

def test_roles_detalle(ctx: Ctx) -> None:
    section("Roles y permisos · detalle")
    res = ctx.resultado
    if not ctx.rol_id:
        skip("GET /roles/{id}", "no hay rol descubierto", res)
        return
    r = get(f"/roles/{ctx.rol_id}", token=ctx.token_admin)
    expect_2xx(f"GET /roles/{ctx.rol_id}", r, res,
               lambda b: f"nombre={b.get('nombre','?')}")


# ──────────────────────────────────────────────────────────────────────────────
# 4. Políticas
# ──────────────────────────────────────────────────────────────────────────────

def test_politicas(ctx: Ctx) -> None:
    section("Políticas de negocio")
    res = ctx.resultado

    r = get("/politicas", token=ctx.token_admin)
    body = expect_2xx("GET /politicas", r, res, lambda b: f"{len(b)} políticas")
    ctx.politica_id = head_or_empty(body)
    # Encontrar una activa
    if isinstance(body, list):
        activa = next((p for p in body if p.get("estado") == "activa"), None)
        if activa:
            ctx.politica_id_activa = activa.get("id", "")

    if ctx.politica_id:
        r = get(f"/politicas/{ctx.politica_id}", token=ctx.token_admin)
        expect_2xx(f"GET /politicas/{ctx.politica_id}", r, res,
                   lambda b: f"estado={b.get('estado','?')}")

    if ctx.politica_id:
        r = get(f"/politicas/{ctx.politica_id}/documentos-requeridos", token=ctx.token_admin)
        expect_2xx("GET /politicas/{id}/documentos-requeridos", r, res,
                   lambda b: f"{len(b) if isinstance(b, list) else 0} actividades con docs")


# ──────────────────────────────────────────────────────────────────────────────
# 5. Diagramas, nodos, transiciones
# ──────────────────────────────────────────────────────────────────────────────

def test_diagramas(ctx: Ctx) -> None:
    section("Diagramas, nodos y transiciones")
    res = ctx.resultado

    r = get("/diagramas", token=ctx.token_admin)
    body = expect_2xx("GET /diagramas", r, res, lambda b: f"{len(b)} diagramas")
    ctx.diagrama_id = head_or_empty(body)

    r = get("/diagramas?estado=publicado", token=ctx.token_admin)
    expect_2xx("GET /diagramas?estado=publicado", r, res,
               lambda b: f"{len(b)} publicados")

    r = get("/diagramas?sinPolitica=true", token=ctx.token_admin)
    expect_2xx("GET /diagramas?sinPolitica=true", r, res,
               lambda b: f"{len(b)} huérfanos")

    if ctx.diagrama_id:
        r = get(f"/diagramas/{ctx.diagrama_id}", token=ctx.token_admin)
        expect_2xx(f"GET /diagramas/{ctx.diagrama_id}", r, res,
                   lambda b: f"nombre={b.get('nombre','?')}")

        r = get(f"/diagramas/{ctx.diagrama_id}/nodos", token=ctx.token_admin)
        body = expect_2xx("GET /diagramas/{id}/nodos", r, res,
                          lambda b: f"{len(b)} nodos")
        ctx.nodo_id = head_or_empty(body)

        r = get(f"/diagramas/{ctx.diagrama_id}/transiciones", token=ctx.token_admin)
        expect_2xx("GET /diagramas/{id}/transiciones", r, res,
                   lambda b: f"{len(b)} transiciones")

    if ctx.nodo_id:
        r = get(f"/nodos/{ctx.nodo_id}", token=ctx.token_admin)
        expect_2xx(f"GET /nodos/{ctx.nodo_id}", r, res,
                   lambda b: f"tipo={b.get('tipo','?')}")


# ──────────────────────────────────────────────────────────────────────────────
# 6. Workflow / trámites — solo lecturas (no inicia trámites)
# ──────────────────────────────────────────────────────────────────────────────

def test_workflow_lecturas(ctx: Ctx) -> None:
    section("Workflow / trámites · lecturas")
    res = ctx.resultado

    r = get("/tramites/mis-tramites", token=ctx.token_cli)
    body = expect_2xx("GET /tramites/mis-tramites (cliente)", r, res,
                      lambda b: f"{len(b)} trámites")
    ctx.tramite_cliente_id = head_or_empty(body)

    r = get("/tramites/mis-pendientes", token=ctx.token_func)
    body = expect_2xx("GET /tramites/mis-pendientes (funcionario)", r, res,
                      lambda b: f"{len(b)} pendientes")
    if not ctx.tramite_id:
        ctx.tramite_id = head_or_empty(body)

    if ctx.tramite_id:
        r = get(f"/tramites/{ctx.tramite_id}/estado", token=ctx.token_admin)
        body = expect_2xx("GET /tramites/{id}/estado", r, res,
                          lambda b: f"estado={b.get('estado','?')} progreso={b.get('progreso','?')}%")
        ctx.expediente_id = body.get("expedienteId", "") if isinstance(body, dict) else ""

        r = get(f"/tramites/{ctx.tramite_id}/flujo-completo", token=ctx.token_admin)
        expect_2xx("GET /tramites/{id}/flujo-completo", r, res,
                   lambda b: f"{len(b.get('nodos', []))} nodos")


# ──────────────────────────────────────────────────────────────────────────────
# 7. Expediente digital
# ──────────────────────────────────────────────────────────────────────────────

def test_expediente(ctx: Ctx) -> None:
    section("Expediente digital")
    res = ctx.resultado
    if not ctx.tramite_id:
        skip("GET /expedientes/tramite/{id}", "no hay tramite_id", res)
        return

    r = get(f"/expedientes/tramite/{ctx.tramite_id}", token=ctx.token_admin)
    body = expect_2xx("GET /expedientes/tramite/{tramiteId}", r, res,
                      lambda b: f"{len(b.get('secciones', []))} secciones")
    # extraer una sección para tests siguientes
    if isinstance(body, dict):
        secs = body.get("secciones") or []
        if secs and isinstance(secs[0], dict):
            ctx.seccion_id = secs[0].get("infoSeccion", {}).get("id", "")


# ──────────────────────────────────────────────────────────────────────────────
# 8. Colaboración (diagramas)
# ──────────────────────────────────────────────────────────────────────────────

def test_colaboracion(ctx: Ctx) -> None:
    section("Colaboración en diagramas (CU-15)")
    res = ctx.resultado
    # Solo lectura — no hay endpoint público de listado, así que probamos el flujo
    # de invitar a un colaborador con un cuerpo mínimo (responde 400 si falta usuario,
    # 201 si todo OK). Tratamos 200/201 como OK, 400 como WARN (no-op por validación).
    if not ctx.diagrama_id:
        skip("POST /colaboracion/diagrama/{id}/invitar", "sin diagrama_id", res)
        return
    # Buscar otro funcionario para invitar
    r = get("/usuarios/funcionarios", token=ctx.token_admin)
    funcs: list[dict[str, Any]] = r.json() if r.status_code == 200 else []
    if not funcs:
        skip("POST /colaboracion/diagrama/{id}/invitar", "no hay funcionarios", res)
        return
    payload = {"usuarioInvitadoId": funcs[0]["id"], "permisos": "editor"}
    r = post(f"/colaboracion/diagrama/{ctx.diagrama_id}/invitar",
             token=ctx.token_admin, json=payload)
    if r.status_code in (200, 201):
        ok("POST /colaboracion/diagrama/{id}/invitar", r.status_code, "invitación creada", res)
    elif r.status_code == 400:
        warn("POST /colaboracion/diagrama/{id}/invitar", r.status_code,
             "ya existe o validación de negocio", res)
    else:
        fail("POST /colaboracion/diagrama/{id}/invitar", r.status_code,
             r.text[:100], res)


# ──────────────────────────────────────────────────────────────────────────────
# 9. Notificaciones (REST; SSE solo handshake)
# ──────────────────────────────────────────────────────────────────────────────

def test_notificaciones(ctx: Ctx) -> None:
    section("Notificaciones")
    res = ctx.resultado

    r = get("/notificaciones/mis-notificaciones", token=ctx.token_cli)
    expect_2xx("GET /notificaciones/mis-notificaciones (cliente)", r, res,
               lambda b: f"{len(b)} notificaciones")

    r = get("/notificaciones/mis-notificaciones", token=ctx.token_func)
    expect_2xx("GET /notificaciones/mis-notificaciones (funcionario)", r, res,
               lambda b: f"{len(b)} notificaciones")

    # SSE — abrir el stream y cerrarlo después de leer 1 chunk o de timeout.
    from _utils import BACKEND_URL
    try:
        with requests.get(
            f"{BACKEND_URL}/notificaciones/stream",
            headers=ctx.headers("cliente"),
            stream=True,
            timeout=3,
        ) as r:
            if r.status_code == 200:
                ok("GET /notificaciones/stream (SSE handshake)", r.status_code,
                   "stream abierto", res)
            else:
                warn("GET /notificaciones/stream (SSE handshake)", r.status_code,
                     r.text[:80], res)
    except requests.exceptions.RequestException as ex:
        warn("GET /notificaciones/stream (SSE handshake)", 0,
             f"timeout/sin respuesta: {ex.__class__.__name__}", res)


# ──────────────────────────────────────────────────────────────────────────────
# 10. Métricas y cuellos de botella
# ──────────────────────────────────────────────────────────────────────────────

def test_metricas(ctx: Ctx) -> None:
    section("Métricas y cuellos de botella")
    res = ctx.resultado

    r = get("/metricas/cuellos-botella", token=ctx.token_admin)
    expect_2xx("GET /metricas/cuellos-botella", r, res,
               lambda b: f"{len(b) if isinstance(b, list) else 'OK'} cuellos")

    if ctx.tramite_id:
        r = get(f"/metricas/tramite/{ctx.tramite_id}", token=ctx.token_admin)
        expect_2xx("GET /metricas/tramite/{id}", r, res,
                   lambda b: f"keys={list(b.keys())[:3] if isinstance(b, dict) else '?'}")
    else:
        skip("GET /metricas/tramite/{id}", "sin tramite_id", res)


# ──────────────────────────────────────────────────────────────────────────────
# 11. Reportes "clásicos" (CSV)
# ──────────────────────────────────────────────────────────────────────────────

def test_reportes_clasicos(ctx: Ctx) -> None:
    section("Reportes clásicos (CU-26)")
    res = ctx.resultado

    payload = {"tipo": "tramites", "formato": "CSV", "filtros": {}}
    r = post("/reportes/generar", token=ctx.token_admin, json=payload)
    body = expect_2xx("POST /reportes/generar", r, res,
                      lambda b: f"id={b.get('id','?')} formato={b.get('formato','?')}")
    if body and body.get("id"):
        r = get(f"/reportes/{body['id']}/descargar", token=ctx.token_admin)
        if r.status_code == 200:
            ok("GET /reportes/{id}/descargar", r.status_code,
               f"{len(r.content)} bytes", res)
        else:
            fail("GET /reportes/{id}/descargar", r.status_code, r.text[:100], res)


# ──────────────────────────────────────────────────────────────────────────────
# 12. Agente IA (CU-31)
# ──────────────────────────────────────────────────────────────────────────────

def test_agente(ctx: Ctx) -> None:
    section("Agente IA conversacional (CU-31)")
    res = ctx.resultado

    payload = {"consulta": "¿Cuál es el estado de mi trámite?", "moduloActivo": "mis-tramites"}
    r = post("/agente/consultar", token=ctx.token_cli, json=payload)
    if r.status_code == 200:
        ok("POST /agente/consultar (cliente)", r.status_code, "respuesta IA local", res)
    elif r.status_code == 503:
        warn("POST /agente/consultar (cliente)", r.status_code,
             "n8n caído (fallback debería responder igual)", res)
    else:
        fail("POST /agente/consultar (cliente)", r.status_code, r.text[:100], res)


# ──────────────────────────────────────────────────────────────────────────────
# 13. Workflow design por prompt (CU-14)
# ──────────────────────────────────────────────────────────────────────────────

def test_workflow_design(ctx: Ctx) -> None:
    section("Diseño de diagrama por prompt (CU-14)")
    res = ctx.resultado
    # PromptFlujoRequest exige: prompt (NotBlank, 20..2000 chars) + nombreDiagrama (NotBlank).
    payload = {
        "prompt": ("_E2E_ flujo de prueba con recepción de solicitud, "
                   "luego revisión técnica, después revisión legal y por último cierre."),
        "nombreDiagrama": "_E2E_Diagrama",
        "politicaId": None,
    }
    r = post("/workflow-design/from-prompt", token=ctx.token_admin, json=payload)
    if r.status_code in (200, 201):
        body = r.json() if r.content else {}
        diag_creado = body.get("diagramaId") or body.get("id") or ""
        ok("POST /workflow-design/from-prompt", r.status_code,
           f"diagramaId={diag_creado}", res)
        # cleanup
        if diag_creado:
            delete(f"/diagramas/{diag_creado}", token=ctx.token_admin)
    elif r.status_code in (503, 502, 504):
        warn("POST /workflow-design/from-prompt", r.status_code,
             "servicio IA externo caído", res)
    else:
        fail("POST /workflow-design/from-prompt", r.status_code, r.text[:100], res)


# ──────────────────────────────────────────────────────────────────────────────
# 14. Historial y trazabilidad
# ──────────────────────────────────────────────────────────────────────────────

def test_historial(ctx: Ctx) -> None:
    section("Historial de trámites")
    res = ctx.resultado
    # Ruta real: GET /api/tramites/historial (bajo TramitesController @RequestMapping)
    # No existe /api/trazabilidad/** expuesto — el servicio existe pero no hay controller.
    if ctx.tramite_id:
        r = get(f"/tramites/historial?tramiteId={ctx.tramite_id}", token=ctx.token_admin)
    else:
        r = get("/tramites/historial", token=ctx.token_admin)
    expect_2xx("GET /tramites/historial", r, res,
               lambda b: f"{len(b) if isinstance(b, list) else 'OK'} entradas")


# ──────────────────────────────────────────────────────────────────────────────
# 15. Adjuntos legacy (filesystem)
# ──────────────────────────────────────────────────────────────────────────────

def test_adjuntos_legacy(ctx: Ctx) -> None:
    section("Documentos del trámite (lista)")
    res = ctx.resultado
    if not ctx.tramite_id:
        skip("GET /tramites/{id}/documentos", "no hay tramite_id", res)
        return
    r = get(f"/tramites/{ctx.tramite_id}/documentos", token=ctx.token_admin)
    expect_2xx("GET /tramites/{id}/documentos", r, res,
               lambda b: f"{len(b) if isinstance(b, list) else 0} documentos")


# ──────────────────────────────────────────────────────────────────────────────
# Orquestador Parte 1
# ──────────────────────────────────────────────────────────────────────────────

def run(ctx: Ctx | None = None) -> Ctx:
    if ctx is None:
        ctx = Ctx()
        t0 = banner_inicio("Smoke tests · BACKEND PARTE 1")
    else:
        t0 = time.time()

    test_auth(ctx)
    if not (ctx.token_admin and ctx.token_func and ctx.token_cli):
        # sin login no podemos continuar
        return ctx

    test_catalogos(ctx)
    test_roles_detalle(ctx)
    test_politicas(ctx)
    test_diagramas(ctx)
    test_workflow_lecturas(ctx)
    test_expediente(ctx)
    test_colaboracion(ctx)
    test_notificaciones(ctx)
    test_metricas(ctx)
    test_reportes_clasicos(ctx)
    test_agente(ctx)
    test_workflow_design(ctx)
    test_historial(ctx)
    test_adjuntos_legacy(ctx)
    return ctx


if __name__ == "__main__":
    t0 = banner_inicio("Smoke tests · BACKEND PARTE 1")
    ctx = run()
    sys.exit(imprimir_resumen(ctx.resultado, t0))
