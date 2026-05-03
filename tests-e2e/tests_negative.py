"""Smoke tests · Nivel 2 — Caminos negativos.

Verifica que el backend **rechaza** lo que NO debería poder hacerse:
  - sin auth → 401 / 403
  - rol incorrecto → 403
  - ids inexistentes → 404 / 400
  - validaciones de DTO → 400
  - reglas de negocio → 400

Mapeo confirmado contra GlobalExceptionHandler.java:
  IllegalArgumentException     → 400
  IllegalStateException        → 500
  AccessDeniedException        → 403
  MethodArgumentNotValidException → 400
"""
from __future__ import annotations

import sys
import time

from _utils import (
    Ctx,
    banner_inicio,
    delete,
    expect_2xx,
    expect_error,
    get,
    imprimir_resumen,
    login_todos,
    patch,
    post,
    put,
    req,
    section,
    skip,
)


# ──────────────────────────────────────────────────────────────────────────────
# 1 · Autenticación: sin token + token inválido
# ──────────────────────────────────────────────────────────────────────────────

def test_sin_auth(ctx: Ctx) -> None:
    section("1 · Sin auth / token inválido")
    res = ctx.resultado

    # Sin token en endpoint protegido
    expect_error("GET /usuarios sin Authorization",
                 get("/usuarios"),
                 res, expected=(401, 403))
    expect_error("GET /tramites/mis-pendientes sin token",
                 get("/tramites/mis-pendientes"),
                 res, expected=(401, 403))
    expect_error("POST /politicas sin token",
                 post("/politicas", json={"nombre": "x", "categoria": "y"}),
                 res, expected=(401, 403))

    # Token con formato JWT pero inválido (firma rota)
    bad = "eyJhbGciOiJIUzUxMiJ9.bad.bad"
    expect_error("GET /usuarios/me con token inválido",
                 get("/usuarios/me", token=bad),
                 res, expected=(401, 403))


# ──────────────────────────────────────────────────────────────────────────────
# 2 · Autorización: rol incorrecto
# ──────────────────────────────────────────────────────────────────────────────

def test_rol_incorrecto(ctx: Ctx) -> None:
    section("2 · Rol incorrecto → 403")
    res = ctx.resultado

    # Cliente intentando endpoints de admin
    expect_error("Cliente → GET /usuarios",
                 get("/usuarios", token=ctx.token_cli),
                 res, expected=(403,))
    expect_error("Cliente → GET /permisos",
                 get("/permisos", token=ctx.token_cli),
                 res, expected=(403,))
    expect_error("Cliente → POST /politicas",
                 post("/politicas", token=ctx.token_cli,
                      json={"nombre": "_E2E_no", "categoria": "x"}),
                 res, expected=(403,))
    expect_error("Cliente → GET /alertas-anomalias",
                 get("/alertas-anomalias", token=ctx.token_cli),
                 res, expected=(403,))
    expect_error("Cliente → POST /reportes/generar",
                 post("/reportes/generar", token=ctx.token_cli,
                      json={"tipo": "tramites", "formato": "CSV", "filtros": {}}),
                 res, expected=(403,))

    # Funcionario intentando configuración de admin
    expect_error("Funcionario → POST /politicas",
                 post("/politicas", token=ctx.token_func,
                      json={"nombre": "_E2E_no", "categoria": "x"}),
                 res, expected=(403,))
    expect_error("Funcionario → PUT /actividades/{id}/permiso-documental",
                 put("/actividades/abc/permiso-documental",
                     token=ctx.token_func,
                     json={"politicaId": "x", "actividadId": "abc", "nivelAcceso": "SOLO_LECTURA"}),
                 res, expected=(403,))
    expect_error("Funcionario → POST /reportes/consulta-natural",
                 post("/reportes/consulta-natural", token=ctx.token_func,
                      json={"consulta": "algo"}),
                 res, expected=(403,))
    if ctx.documento_archivo_id:
        expect_error("Funcionario → GET /documentos/{id}/auditoria",
                     get(f"/documentos/{ctx.documento_archivo_id}/auditoria",
                         token=ctx.token_func),
                     res, expected=(403,))


# ──────────────────────────────────────────────────────────────────────────────
# 3 · Recursos inexistentes
# ──────────────────────────────────────────────────────────────────────────────

def test_id_inexistente(ctx: Ctx) -> None:
    section("3 · IDs inexistentes → 404 ó 400 (según implementación)")
    res = ctx.resultado
    bogus = "000000000000000000000000"   # ObjectId con formato pero inexistente

    # Controllers que usan ResponseEntity.notFound() → 404
    expect_error(f"GET /diagramas/{bogus}",
                 get(f"/diagramas/{bogus}", token=ctx.token_admin),
                 res, expected=(404, 400))
    expect_error(f"GET /documentos/{bogus}",
                 get(f"/documentos/{bogus}", token=ctx.token_admin),
                 res, expected=(404, 400))
    expect_error(f"GET /nodos/{bogus}",
                 get(f"/nodos/{bogus}", token=ctx.token_admin),
                 res, expected=(404, 400))

    # Controllers que delegan al service y lanzan IllegalArgumentException → 400
    expect_error(f"GET /politicas/{bogus}",
                 get(f"/politicas/{bogus}", token=ctx.token_admin),
                 res, expected=(404, 400))
    expect_error(f"GET /tramites/{bogus}/estado",
                 get(f"/tramites/{bogus}/estado", token=ctx.token_admin),
                 res, expected=(404, 400))
    expect_error(f"GET /documentos/{bogus}/preview",
                 get(f"/documentos/{bogus}/preview", token=ctx.token_admin),
                 res, expected=(404, 400))
    expect_error(f"GET /repositorios/{bogus}",
                 get(f"/repositorios/{bogus}", token=ctx.token_admin),
                 res, expected=(404, 400))


# ──────────────────────────────────────────────────────────────────────────────
# 4 · Validación de DTOs (Bean Validation)
# ──────────────────────────────────────────────────────────────────────────────

def test_validaciones_dto(ctx: Ctx) -> None:
    section("4 · Validaciones de DTO → 400")
    res = ctx.resultado

    # PoliticaNegocioRequest.nombre @NotBlank
    expect_error("POST /politicas sin nombre",
                 post("/politicas", token=ctx.token_admin,
                      json={"descripcion": "sin nombre", "categoria": "x"}),
                 res, expected=(400,))

    # IniciarTramiteRequest.politicaId @NotBlank
    expect_error("POST /tramites/iniciar sin politicaId",
                 post("/tramites/iniciar", token=ctx.token_cli,
                      json={"clienteId": "x", "prioridad": 3}),
                 res, expected=(400,))

    # DiagramaWorkflowRequest.nombre @NotBlank
    expect_error("POST /diagramas sin nombre",
                 post("/diagramas", token=ctx.token_admin,
                      json={"swimlanes": []}),
                 res, expected=(400,))

    # PoliticaEstadoRequest.estado @Pattern(borrador|activa|archivada)
    if ctx.politica_id:
        expect_error("PATCH /politicas/{id}/estado con valor inválido",
                     patch(f"/politicas/{ctx.politica_id}/estado",
                           token=ctx.token_admin, json={"estado": "_E2E_invalido"}),
                     res, expected=(400,))

    # DiagramaEstadoRequest.estado @Pattern(publicado|archivado)
    # NOTA: 'borrador' NO está permitido aquí (el estado inicial se setea al crear).
    if ctx.diagrama_id:
        expect_error("PATCH /diagramas/{id}/estado con 'borrador' (no permitido)",
                     patch(f"/diagramas/{ctx.diagrama_id}/estado",
                           token=ctx.token_admin, json={"estado": "borrador"}),
                     res, expected=(400,))

    # NodoDiagramaRequest.tipo @Pattern(inicio|actividad|decision|fork|join|fin)
    if ctx.diagrama_id:
        expect_error("POST nodos con tipo inválido",
                     post(f"/diagramas/{ctx.diagrama_id}/nodos",
                          token=ctx.token_admin,
                          json={"nombre": "x", "tipo": "_E2E_invalido", "orden": 99}),
                     res, expected=(400,))

    # FlujoTransicionRequest.tipo @Pattern
    if ctx.diagrama_id:
        expect_error("POST transiciones con tipo inválido",
                     post(f"/diagramas/{ctx.diagrama_id}/transiciones",
                          token=ctx.token_admin,
                          json={"nodoOrigenId": "a", "nodoDestinoId": "b",
                                "tipo": "_E2E_invalido"}),
                     res, expected=(400,))

    # PromptFlujoRequest.prompt @Size(min=20)
    expect_error("POST /workflow-design/from-prompt con prompt corto",
                 post("/workflow-design/from-prompt", token=ctx.token_admin,
                      json={"prompt": "muy corto", "nombreDiagrama": "x"}),
                 res, expected=(400,))


# ──────────────────────────────────────────────────────────────────────────────
# 5 · Reglas de negocio
# ──────────────────────────────────────────────────────────────────────────────

def test_reglas_negocio(ctx: Ctx) -> None:
    section("5 · Reglas de negocio → 400")
    res = ctx.resultado
    ts = int(time.time())

    # 5.1 · Nombre de política duplicado (creamos uno y lo borramos al final)
    nombre_pol = f"_E2E_dupe_{ts}"
    r1 = post("/politicas", token=ctx.token_admin, json={
        "nombre": nombre_pol, "descripcion": "primera", "categoria": "_E2E_"
    })
    if r1.status_code not in (200, 201):
        skip("5.1 nombre duplicado", "no se pudo crear política base", res)
    else:
        pol_id = r1.json().get("id", "")
        # Intentar crear otra con el mismo nombre → 400
        r2 = post("/politicas", token=ctx.token_admin, json={
            "nombre": nombre_pol, "descripcion": "duplicada", "categoria": "_E2E_"
        })
        expect_error("5.1 POST /politicas con nombre duplicado",
                     r2, res, expected=(400,))
        # cleanup
        patch(f"/politicas/{pol_id}/estado", token=ctx.token_admin,
              json={"estado": "archivada"})
        delete(f"/politicas/{pol_id}", token=ctx.token_admin)

    # 5.2 · Activar política sin diagrama → 400
    nombre_pol2 = f"_E2E_sinDiag_{ts}"
    r1 = post("/politicas", token=ctx.token_admin, json={
        "nombre": nombre_pol2, "descripcion": "x", "categoria": "_E2E_"
    })
    if r1.status_code in (200, 201):
        pol_id = r1.json().get("id", "")
        r2 = patch(f"/politicas/{pol_id}/estado", token=ctx.token_admin,
                   json={"estado": "activa"})
        expect_error("5.2 activar política sin diagramaId",
                     r2, res, expected=(400,))
        # cleanup
        delete(f"/politicas/{pol_id}", token=ctx.token_admin)

    # 5.3 · Eliminar política activa → 400 (no permitido)
    if ctx.politica_id_activa:
        # Intentar eliminar (no se borrará porque está activa)
        r = delete(f"/politicas/{ctx.politica_id_activa}", token=ctx.token_admin)
        expect_error("5.3 DELETE política activa",
                     r, res, expected=(400,))

    # 5.4 · Eliminar diagrama publicado → 400
    if ctx.politica_id_activa:
        # Obtener el diagrama de la política activa
        r = get(f"/politicas/{ctx.politica_id_activa}", token=ctx.token_admin)
        diag_id = r.json().get("diagramaId", "") if r.status_code == 200 else ""
        if diag_id:
            r2 = delete(f"/diagramas/{diag_id}", token=ctx.token_admin)
            expect_error("5.4 DELETE diagrama publicado",
                         r2, res, expected=(400,))
        else:
            skip("5.4 diagrama publicado", "política activa sin diagramaId", res)

    # 5.5 · Publicar diagrama sin nodos → 400 ("el diagrama no tiene nodos")
    r = post("/diagramas", token=ctx.token_admin, json={
        "nombre": f"_E2E_vacio_{ts}", "swimlanes": []
    })
    if r.status_code in (200, 201):
        diag_id = r.json().get("id", "")
        r2 = patch(f"/diagramas/{diag_id}/estado", token=ctx.token_admin,
                   json={"estado": "publicado"})
        expect_error("5.5 publicar diagrama sin nodos",
                     r2, res, expected=(400,))
        # cleanup
        delete(f"/diagramas/{diag_id}", token=ctx.token_admin)

    # 5.6 · PATCH política con misma transición (estado actual == nuevo) → 400
    if ctx.politica_id_activa:
        r = patch(f"/politicas/{ctx.politica_id_activa}/estado",
                  token=ctx.token_admin, json={"estado": "activa"})
        expect_error("5.6 PATCH política a su mismo estado",
                     r, res, expected=(400,))


# ──────────────────────────────────────────────────────────────────────────────
# 6 · Permisos documentales (CU-36) — el negativo
# ──────────────────────────────────────────────────────────────────────────────

def test_permisos_doc_invalido(ctx: Ctx) -> None:
    section("6 · Validaciones de permiso documental")
    res = ctx.resultado
    if not ctx.politica_id_activa or not ctx.actividad_id:
        skip("permisos doc", "sin política activa o actividad", res)
        return

    # 6.1 · nivelAcceso fuera del enum
    r = put(f"/actividades/{ctx.actividad_id}/permiso-documental",
            token=ctx.token_admin, json={
                "politicaId": ctx.politica_id_activa,
                "actividadId": ctx.actividad_id,
                "nivelAcceso": "_E2E_invalido",
                "tiposDocumentoVisibles": [],
            })
    expect_error("6.1 nivelAcceso inválido",
                 r, res, expected=(400,))


# ──────────────────────────────────────────────────────────────────────────────
# Orquestador
# ──────────────────────────────────────────────────────────────────────────────

def run(ctx: Ctx | None = None) -> Ctx:
    nuevo = ctx is None
    if nuevo:
        ctx = Ctx()
        banner_inicio("Smoke tests · NIVEL 2 — Caminos negativos")

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
            if not ctx.politica_id:
                ctx.politica_id = ctx.politica_id_activa
    if not ctx.actividad_id:
        r = get("/actividades", token=ctx.token_admin)
        if r.status_code == 200 and r.json():
            ctx.actividad_id = r.json()[0].get("id", "")
    if not ctx.diagrama_id:
        r = get("/diagramas", token=ctx.token_admin)
        if r.status_code == 200 and r.json():
            ctx.diagrama_id = r.json()[0].get("id", "")

    test_sin_auth(ctx)
    test_rol_incorrecto(ctx)
    test_id_inexistente(ctx)
    test_validaciones_dto(ctx)
    test_reglas_negocio(ctx)
    test_permisos_doc_invalido(ctx)
    return ctx


if __name__ == "__main__":
    t0 = banner_inicio("Smoke tests · NIVEL 2 — Caminos negativos")
    ctx = run(Ctx())
    sys.exit(imprimir_resumen(ctx.resultado, t0))
