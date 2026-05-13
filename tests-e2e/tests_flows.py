"""Smoke tests · Nivel 1 — Flujos felices end-to-end.

A · Política: crear borrador → no se puede activar sin diagrama →
   crear diagrama vacío → agregar inicio/actividad/fin + 2 transiciones →
   publicar diagrama → activar política → archivar política → eliminar política → eliminar diagrama.

B · Trámite (no destructivo del seed): cliente inicia trámite con política activa →
   verifica avance al primer nodo actividad → funcionario completa el nodo →
   verifica nuevo nodo actual / cierre → cliente cancela el trámite (si sigue abierto).

C · Documental: subir doc → versión 2 (bytes distintos) → versión 3 →
   listar versiones (debe ser 3 con esActual en la última) → preview →
   auditoría tiene 4+ eventos (SUBIDA + 2 NUEVA_VERSION + LECTURA).
   Solo corre si S3 está habilitado en el backend.
"""
from __future__ import annotations

import io
import sys
import time
from typing import Any, Optional

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
# Flujo A · Ciclo de vida de Política
# ──────────────────────────────────────────────────────────────────────────────

def flujo_a_politica_lifecycle(ctx: Ctx) -> None:
    section("Flujo A · Política lifecycle (borrador → activa → archivada → delete)")
    res = ctx.resultado
    ts = int(time.time())
    nombre_pol = f"_E2E_pol_{ts}"
    nombre_diag = f"_E2E_diag_{ts}"

    politica_id: Optional[str] = None
    diagrama_id: Optional[str] = None
    nodos_creados: list[str] = []

    try:
        # A.1 · Crear política borrador
        r = post("/politicas", token=ctx.token_admin, json={
            "nombre": nombre_pol,
            "descripcion": "Test E2E lifecycle",
            "categoria": "_E2E_",
        })
        body = expect_2xx("A.1 POST /politicas (crear borrador)", r, res,
                          lambda b: f"id={b.get('id','?')} estado={b.get('estado','?')}",
                          expected=(200, 201))
        if not body:
            return
        politica_id = body.get("id", "")
        # A.1b · El repositorio documental ahora es 1:1 por TRÁMITE (se crea al
        # iniciar el trámite), no por política. Se valida en el flujo C / CU-32.
        skip("A.1b CU-32 repositorio (ahora por trámite, no por política)",
             "se verifica al iniciar el trámite", res)

        # A.2 · Intentar activar sin diagrama → debe fallar 400
        r = patch(f"/politicas/{politica_id}/estado", token=ctx.token_admin,
                  json={"estado": "activa"})
        if r.status_code == 400:
            ok("A.2 activar sin diagrama → rechazado", r.status_code,
               "validación de negocio correcta", res)
        else:
            fail("A.2 activar sin diagrama", r.status_code,
                 f"esperaba 400, recibí {r.status_code}: {r.text[:100]}", res)

        # A.3 · Crear diagrama vinculado a la política
        r = post("/diagramas", token=ctx.token_admin, json={
            "nombre": nombre_diag,
            "politicaId": politica_id,
            "swimlanes": ["ATC"],
        })
        body = expect_2xx("A.3 POST /diagramas (vinculado a política)", r, res,
                          lambda b: f"id={b.get('id','?')} estado={b.get('estado','?')}",
                          expected=(200, 201))
        if not body:
            return
        diagrama_id = body.get("id", "")

        # A.4 · Agregar nodos inicio + actividad + fin
        depto_id = ctx.departamento_id or ""
        act_id = ctx.actividad_id or ""

        for nodo_tipo, orden, nombre in [
            ("inicio", 1, "Inicio"),
            ("actividad", 2, "Actividad E2E"),
            ("fin", 3, "Fin"),
        ]:
            payload: dict[str, Any] = {"nombre": nombre, "tipo": nodo_tipo, "orden": orden}
            if nodo_tipo == "actividad":
                payload["departamentoId"] = depto_id
                payload["actividadId"] = act_id
                payload["swimlane"] = "ATC"
            r = post(f"/diagramas/{diagrama_id}/nodos",
                     token=ctx.token_admin, json=payload)
            body = expect_2xx(f"A.4 POST nodos · {nodo_tipo}", r, res,
                              lambda b: f"id={b.get('id','?')}",
                              expected=(200, 201))
            if body:
                nodos_creados.append(body.get("id", ""))

        if len(nodos_creados) != 3:
            fail("A.4 nodos creados", 0, "no se crearon los 3 nodos", res)
            return

        # A.5 · Transiciones inicio → actividad → fin
        for origen, destino in [(nodos_creados[0], nodos_creados[1]),
                                (nodos_creados[1], nodos_creados[2])]:
            r = post(f"/diagramas/{diagrama_id}/transiciones",
                     token=ctx.token_admin, json={
                         "nodoOrigenId": origen,
                         "nodoDestinoId": destino,
                         "tipo": "secuencial",
                     })
            expect_2xx("A.5 POST transiciones", r, res,
                       lambda b: f"id={b.get('id','?')}",
                       expected=(200, 201))

        # A.6 · Publicar diagrama
        r = patch(f"/diagramas/{diagrama_id}/estado", token=ctx.token_admin,
                  json={"estado": "publicado"})
        expect_2xx("A.6 PATCH /diagramas/{id}/estado → publicado", r, res,
                   lambda b: f"estado={b.get('estado','?')}")

        # A.7 · Activar política (ahora sí, tiene diagrama publicado)
        r = patch(f"/politicas/{politica_id}/estado", token=ctx.token_admin,
                  json={"estado": "activa"})
        expect_2xx("A.7 PATCH /politicas/{id}/estado → activa", r, res,
                   lambda b: f"estado={b.get('estado','?')} fechaActivacion={b.get('fechaActivacion','-')}")

        # A.8 · Archivar política
        r = patch(f"/politicas/{politica_id}/estado", token=ctx.token_admin,
                  json={"estado": "archivada"})
        expect_2xx("A.8 PATCH /politicas/{id}/estado → archivada", r, res,
                   lambda b: f"estado={b.get('estado','?')}")

        # A.9 · Eliminar política archivada
        r = delete(f"/politicas/{politica_id}", token=ctx.token_admin)
        if r.status_code in (200, 204):
            ok("A.9 DELETE /politicas/{id}", r.status_code, "política eliminada", res)
            politica_id = None
        else:
            fail("A.9 DELETE /politicas/{id}", r.status_code, r.text[:120], res)

    finally:
        # Cleanup defensivo (por si algo falló a mitad)
        if diagrama_id:
            # 1) Si está publicado, archivarlo
            patch(f"/diagramas/{diagrama_id}/estado", token=ctx.token_admin,
                  json={"estado": "archivado"})
            # 2) Eliminar
            r = delete(f"/diagramas/{diagrama_id}", token=ctx.token_admin)
            if r.status_code in (200, 204):
                ok("A.cleanup DELETE /diagramas/{id}", r.status_code,
                   "diagrama eliminado", res)
        if politica_id:
            # Forzar archivada antes de eliminar
            patch(f"/politicas/{politica_id}/estado", token=ctx.token_admin,
                  json={"estado": "archivada"})
            delete(f"/politicas/{politica_id}", token=ctx.token_admin)


# ──────────────────────────────────────────────────────────────────────────────
# Flujo B · Trámite end-to-end
# ──────────────────────────────────────────────────────────────────────────────

def flujo_b_tramite(ctx: Ctx) -> None:
    section("Flujo B · Trámite (iniciar → completar nodo → cancelar)")
    res = ctx.resultado

    if not ctx.politica_id_activa:
        skip("Flujo B", "no hay política activa con diagrama publicado", res)
        return

    # B.1 · Cliente inicia el trámite (el backend sobrescribe clienteId con auth.getName())
    r = post("/tramites/iniciar", token=ctx.token_cli, json={
        "clienteId": "será reemplazado por auth",   # backend lo ignora para rol CLIENTE
        "politicaId": ctx.politica_id_activa,
        "prioridad": 3,
    })
    body = expect_2xx("B.1 POST /tramites/iniciar", r, res,
                      lambda b: f"codigo={b.get('codigo','?')} estado={b.get('estadoActual','?')}",
                      expected=(200, 201))
    if not body:
        return
    tramite_id = body.get("id", "")
    nodo_actual_inicial = body.get("nodoActualId", "")
    expediente_id = body.get("expedienteId", "")

    # B.2 · Verificar el estado del trámite recién creado
    r = get(f"/tramites/{tramite_id}/estado", token=ctx.token_cli)
    body = expect_2xx("B.2 GET /tramites/{id}/estado", r, res,
                      lambda b: f"estado={b.get('estado','?')} progreso={b.get('progreso','?')}%")
    if not body:
        flujo_b_cleanup(ctx, tramite_id)
        return

    # B.3 · Obtener el id del funcionario logueado (el motor permite que cualquier funcionario
    #        complete el nodo activo — el chequeo de depto está deshabilitado para demo).
    r = get("/usuarios/me", token=ctx.token_func)
    me = r.json() if r.status_code == 200 else {}
    funcionario_id = me.get("id", "")
    if not funcionario_id:
        fail("B.3 GET /usuarios/me (funcionario)", r.status_code,
             "no se pudo obtener id del funcionario", res)
        flujo_b_cleanup(ctx, tramite_id)
        return

    # B.4 · Completar el nodo actual
    r = post(f"/tramites/{tramite_id}/completar-nodo",
             token=ctx.token_func, json={
                 "funcionarioId": funcionario_id,
                 "decision": None,
                 "notas": "_E2E_ test completar nodo",
             })
    body = expect_2xx("B.4 POST /tramites/{id}/completar-nodo", r, res,
                      lambda b: f"estadoActual={b.get('estadoActual','?')} nodoActualId={b.get('nodoActualId','?')}",
                      expected=(200, 201))
    if not body:
        flujo_b_cleanup(ctx, tramite_id)
        return

    nodo_actual_post = body.get("nodoActualId")
    if nodo_actual_post and nodo_actual_post != nodo_actual_inicial:
        ok("B.4b motor avanzó al siguiente nodo", 200,
           f"{nodo_actual_inicial[-6:]} → {nodo_actual_post[-6:]}", res)
    elif body.get("estadoActual") in ("Aprobado", "Completado", "Rechazado"):
        ok("B.4b motor cerró el trámite", 200,
           f"estado final={body.get('estadoActual','?')}", res)
    elif body.get("nodosParalellosActivos"):
        ok("B.4b motor abrió fork (paralelo)", 200,
           f"{len(body['nodosParalellosActivos'])} ramas activas", res)
    else:
        warn("B.4b motor avance", 200,
             "no detecté avance — verifica el seed del diagrama", res)

    # B.5 · Cancelar el trámite si sigue abierto (limpieza no destructiva)
    flujo_b_cleanup(ctx, tramite_id)


def flujo_b_cleanup(ctx: Ctx, tramite_id: str) -> None:
    """Cancela el trámite si sigue abierto, para no contaminar el seed."""
    if not tramite_id:
        return
    res = ctx.resultado
    r = post(f"/tramites/{tramite_id}/cancelar", token=ctx.token_cli)
    if r.status_code in (200, 204):
        ok("B.5 POST /tramites/{id}/cancelar", r.status_code,
           "cliente canceló el trámite", res)
    elif r.status_code in (400, 409):
        ok("B.5 cancelar (ya cerrado)", r.status_code,
           "el trámite ya estaba cerrado, no se canceló", res)
    else:
        warn("B.5 POST /tramites/{id}/cancelar", r.status_code,
             r.text[:120], res)


# ──────────────────────────────────────────────────────────────────────────────
# Flujo C · Documental end-to-end (requiere S3)
# ──────────────────────────────────────────────────────────────────────────────

def flujo_c_documental(ctx: Ctx) -> None:
    section("Flujo C · Documento end-to-end (subir → v2 → v3 → auditoría)")
    res = ctx.resultado

    if SKIP_S3:
        skip("Flujo C", "SKIP_S3=1", res)
        return
    if not ctx.tramite_id or not ctx.actividad_id:
        skip("Flujo C", "sin tramite_id/actividad_id", res)
        return

    # C.1 · Subir v1
    pdf_v1 = b"%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\nE2E v1\n"
    files = {"archivo": ("_E2E_doc.pdf", io.BytesIO(pdf_v1), "application/pdf")}
    data = {
        "tramiteId": ctx.tramite_id,
        "actividadId": ctx.actividad_id,
        "tipoDocumento": "PDF",
        "nombreLogico": f"_E2E_doc_{int(time.time())}",
        "obligatorio": "false",
    }
    r = post(f"/tramites/{ctx.tramite_id}/documentos",
             token=ctx.token_admin, files=files, data=data)
    if r.status_code == 500 and "S3" in (r.text or ""):
        warn("C.1 subir v1", r.status_code, "S3 deshabilitado — saltando flujo C", res)
        return
    body = expect_2xx("C.1 subir documento v1", r, res,
                      lambda b: f"docId={b.get('documentoArchivoId','?')[-6:]} v{b.get('numeroVersion','?')}",
                      expected=(200, 201))
    if not body:
        return
    doc_id = body.get("documentoArchivoId", "")

    # C.2 · Subir v2 (bytes distintos para que el hash NO coincida)
    files = {"archivo": ("_E2E_doc_v2.pdf",
                         io.BytesIO(pdf_v1 + b"v2\n"),
                         "application/pdf")}
    r = post(f"/documentos/{doc_id}/versiones",
             token=ctx.token_admin, files=files,
             data={"comentarioCambio": "_E2E_ cambio v2"})
    expect_2xx("C.2 nueva versión v2", r, res,
               lambda b: f"v{b.get('numeroVersion','?')}",
               expected=(200, 201))

    # C.3 · Subir v3
    files = {"archivo": ("_E2E_doc_v3.pdf",
                         io.BytesIO(pdf_v1 + b"v3\n"),
                         "application/pdf")}
    r = post(f"/documentos/{doc_id}/versiones",
             token=ctx.token_admin, files=files,
             data={"comentarioCambio": "_E2E_ cambio v3"})
    expect_2xx("C.3 nueva versión v3", r, res,
               lambda b: f"v{b.get('numeroVersion','?')}",
               expected=(200, 201))

    # C.4 · Listar versiones — deben ser 3, la v3 esActual=true
    r = get(f"/documentos/{doc_id}/versiones", token=ctx.token_admin)
    versiones = r.json() if r.status_code == 200 else []
    if isinstance(versiones, list) and len(versiones) == 3:
        v_actual = next((v for v in versiones if v.get("esActual")), None)
        if v_actual and v_actual.get("numeroVersion") == 3:
            ok("C.4 listar versiones", 200,
               "3 versiones, v3 marcada como actual", res)
        else:
            fail("C.4 listar versiones", 200,
                 f"versiones={len(versiones)} pero actual no es v3", res)
    else:
        fail("C.4 listar versiones", r.status_code,
             f"esperaba 3 versiones, recibí {len(versiones) if isinstance(versiones, list) else '?'}", res)

    # C.5 · Preview (registra audit LECTURA)
    r = get(f"/documentos/{doc_id}/preview", token=ctx.token_admin)
    expect_2xx("C.5 GET /documentos/{id}/preview", r, res,
               lambda b: f"mime={b.get('mimeType','?')}")

    # C.6 · Verificar auditoría: 1 SUBIDA + 2 NUEVA_VERSION + 1 LECTURA = 4+
    r = get(f"/documentos/{doc_id}/auditoria?page=0&size=20", token=ctx.token_admin)
    auditoria = r.json() if r.status_code == 200 else {}
    eventos = auditoria.get("content", []) if isinstance(auditoria, dict) else []
    acciones = {e.get("accion") for e in eventos}
    esperadas = {"SUBIDA", "NUEVA_VERSION", "LECTURA"}
    if esperadas.issubset(acciones) and len(eventos) >= 4:
        ok("C.6 auditoría completa", 200,
           f"{len(eventos)} eventos · acciones={sorted(acciones)}", res)
    else:
        fail("C.6 auditoría incompleta", 200,
             f"{len(eventos)} eventos · acciones={sorted(acciones)} (esperaba SUBIDA+NUEVA_VERSION+LECTURA)", res)


# ──────────────────────────────────────────────────────────────────────────────
# Orquestador
# ──────────────────────────────────────────────────────────────────────────────

def run(ctx: Ctx | None = None) -> Ctx:
    nuevo = ctx is None
    if nuevo:
        ctx = Ctx()
        banner_inicio("Smoke tests · NIVEL 1 — Flujos felices")

    if not (ctx.token_admin and ctx.token_func and ctx.token_cli):
        if not login_todos(ctx):
            return ctx

    # Pre-descubrimiento (similar al de tests_p2)
    if not ctx.politica_id_activa:
        r = get("/politicas", token=ctx.token_admin)
        if r.status_code == 200:
            for p in r.json():
                if p.get("estado") == "activa":
                    ctx.politica_id_activa = p.get("id", "")
                    break
    if not ctx.departamento_id:
        r = get("/departamentos", token=ctx.token_admin)
        if r.status_code == 200 and r.json():
            ctx.departamento_id = r.json()[0].get("id", "")
    if not ctx.actividad_id:
        r = get("/actividades", token=ctx.token_admin)
        if r.status_code == 200 and r.json():
            ctx.actividad_id = r.json()[0].get("id", "")
    if not ctx.tramite_id:
        r = get("/tramites/mis-pendientes", token=ctx.token_func)
        if r.status_code == 200 and r.json():
            ctx.tramite_id = r.json()[0].get("id", "")

    flujo_a_politica_lifecycle(ctx)
    flujo_b_tramite(ctx)
    flujo_c_documental(ctx)
    return ctx


if __name__ == "__main__":
    t0 = banner_inicio("Smoke tests · NIVEL 1 — Flujos felices")
    ctx = run(Ctx())
    sys.exit(imprimir_resumen(ctx.resultado, t0))
