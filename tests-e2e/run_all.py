"""Orquestador — corre Parte 1 + Parte 2 con un contexto compartido."""
from __future__ import annotations

import sys

from _utils import Ctx, banner_inicio, imprimir_resumen
import tests_p1
import tests_p2
import tests_flows
import tests_negative
import tests_quantitative


def main() -> int:
    t0 = banner_inicio(
        "Smoke tests · BACKEND COMPLETO (P1 + P2 + N1 + N2 + N3)"
    )
    ctx = Ctx()
    tests_p1.run(ctx)
    if ctx.token_admin and ctx.token_func and ctx.token_cli:
        tests_p2.run(ctx)
        tests_flows.run(ctx)
        tests_negative.run(ctx)
        tests_quantitative.run(ctx)
    return imprimir_resumen(ctx.resultado, t0)


if __name__ == "__main__":
    sys.exit(main())
