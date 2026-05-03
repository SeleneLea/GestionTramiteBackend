"""Utilidades comunes para la suite de smoke tests HTTP."""
from __future__ import annotations

import os
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

import requests
from colorama import Fore, Style, init as colorama_init

colorama_init()

# ── Configuración por variables de entorno ──────────────────────────────────

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080/api")
IA_URL = os.environ.get("IA_URL", "http://localhost:8001")

CREDENCIALES = {
    "admin": (
        os.environ.get("EMAIL_ADMIN", "admin@cre.bo"),
        os.environ.get("PASS_ADMIN", "admin12345"),
    ),
    "funcionario": (
        os.environ.get("EMAIL_FUNC", "funcionario@cre.bo"),
        os.environ.get("PASS_FUNC", "func12345"),
    ),
    "cliente": (
        os.environ.get("EMAIL_CLI", "cliente@cre.bo"),
        os.environ.get("PASS_CLI", "cliente12345"),
    ),
}

SKIP_S3 = bool(os.environ.get("SKIP_S3"))


# ── Resumen agregado ─────────────────────────────────────────────────────────

@dataclass
class Resultado:
    ok: int = 0
    skip: int = 0
    warn: int = 0
    fail: int = 0
    fails: list[str] = field(default_factory=list)

    def total(self) -> int:
        return self.ok + self.skip + self.warn + self.fail


# ── Sesión / contexto ────────────────────────────────────────────────────────

@dataclass
class Ctx:
    """Estado compartido entre tests: tokens, IDs descubiertos, resumen."""
    token_admin: str = ""
    token_func: str = ""
    token_cli: str = ""
    resultado: Resultado = field(default_factory=Resultado)
    # IDs descubiertos al vuelo
    rol_id: str = ""
    politica_id: str = ""
    politica_id_activa: str = ""
    diagrama_id: str = ""
    nodo_id: str = ""
    actividad_id: str = ""
    departamento_id: str = ""
    documento_catalogo_id: str = ""
    tramite_id: str = ""
    tramite_cliente_id: str = ""
    seccion_id: str = ""
    expediente_id: str = ""
    repositorio_id: str = ""
    documento_archivo_id: str = ""
    sugerencia_id: str = ""

    def headers(self, rol: str = "admin") -> dict:
        tok = {"admin": self.token_admin, "funcionario": self.token_func, "cliente": self.token_cli}[rol]
        return {"Authorization": f"Bearer {tok}"} if tok else {}


# ── Helpers de output ────────────────────────────────────────────────────────

def section(titulo: str) -> None:
    print()
    print(Fore.CYAN + "═" * 78 + Style.RESET_ALL)
    print(Fore.CYAN + f"  {titulo}" + Style.RESET_ALL)
    print(Fore.CYAN + "═" * 78 + Style.RESET_ALL)


def _format_status(status_code: int) -> str:
    if 200 <= status_code < 300:
        return Fore.GREEN + f"{status_code}" + Style.RESET_ALL
    if status_code in (404, 410):
        return Fore.YELLOW + f"{status_code}" + Style.RESET_ALL
    return Fore.RED + f"{status_code}" + Style.RESET_ALL


def _print_line(tag: str, label: str, status: str, extra: str = "") -> None:
    print(f"  {tag}  {label:<55} {status}  {extra}")


def ok(label: str, status_code: int, extra: str = "", res: Optional[Resultado] = None) -> None:
    _print_line(Fore.GREEN + "✅" + Style.RESET_ALL, label, _format_status(status_code), extra)
    if res:
        res.ok += 1


def skip(label: str, motivo: str, res: Optional[Resultado] = None) -> None:
    _print_line(Fore.YELLOW + "⏭️ " + Style.RESET_ALL, label, "SKIP", motivo)
    if res:
        res.skip += 1


def warn(label: str, status_code: int, motivo: str, res: Optional[Resultado] = None) -> None:
    _print_line(Fore.YELLOW + "⚠️ " + Style.RESET_ALL, label, _format_status(status_code), motivo)
    if res:
        res.warn += 1


def fail(label: str, status_code: int, motivo: str, res: Optional[Resultado] = None) -> None:
    _print_line(Fore.RED + "❌" + Style.RESET_ALL, label, _format_status(status_code), motivo)
    if res:
        res.fail += 1
        res.fails.append(f"{label} [{status_code}] {motivo}")


# ── Llamadas HTTP ────────────────────────────────────────────────────────────

def req(
    method: str,
    path: str,
    *,
    base: str = BACKEND_URL,
    token: str = "",
    json: Optional[dict] = None,
    params: Optional[dict] = None,
    files: Optional[dict] = None,
    data: Optional[dict] = None,
    timeout: int = 15,
) -> requests.Response:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    url = f"{base}{path}" if path.startswith("/") else f"{base}/{path}"
    return requests.request(
        method=method,
        url=url,
        headers=headers,
        json=json,
        params=params,
        files=files,
        data=data,
        timeout=timeout,
    )


def get(path: str, token: str = "", **kw) -> requests.Response:
    return req("GET", path, token=token, **kw)


def post(path: str, token: str = "", **kw) -> requests.Response:
    return req("POST", path, token=token, **kw)


def put(path: str, token: str = "", **kw) -> requests.Response:
    return req("PUT", path, token=token, **kw)


def patch(path: str, token: str = "", **kw) -> requests.Response:
    return req("PATCH", path, token=token, **kw)


def delete(path: str, token: str = "", **kw) -> requests.Response:
    return req("DELETE", path, token=token, **kw)


# ── Wrapper de "espera 2xx, sino reporta y sigue" ─────────────────────────────

def expect_2xx(
    label: str,
    response: requests.Response,
    res: Resultado,
    extra_fn: Optional[Callable[[Any], str]] = None,
    expected: tuple[int, ...] = (200, 201, 204),
) -> Optional[Any]:
    """Reporta el resultado y devuelve el body parseado si es 2xx, sino None."""
    sc = response.status_code
    if sc in expected:
        body = None
        try:
            if response.content and "json" in response.headers.get("Content-Type", ""):
                body = response.json()
        except Exception:
            body = None
        extra = extra_fn(body) if (extra_fn and body is not None) else ""
        ok(label, sc, extra, res)
        return body
    # 503 con IA_NO_DISPONIBLE → WARN (no FAIL)
    if sc == 503 and "IA_NO_DISPONIBLE" in (response.text or ""):
        warn(label, sc, "microservicio IA caído (esperado en degradación)", res)
        return None
    motivo = _resumen_error(response)
    fail(label, sc, motivo, res)
    return None


def expect_error(
    label: str,
    response: requests.Response,
    res: Resultado,
    *,
    expected: tuple[int, ...],
    extra: str = "",
) -> bool:
    """Tests negativos: espera **uno** de los códigos en {expected}.

    Algunos controllers responden 404 (notFound), otros 400 (IllegalArgumentException).
    Aceptar varias opciones evita tests frágiles ante la implementación específica.
    """
    sc = response.status_code
    if sc in expected:
        ok(label, sc, extra or f"esperaba uno de {expected}", res)
        return True
    motivo = _resumen_error(response)
    fail(label, sc, f"esperaba {expected}, recibí {sc}: {motivo}", res)
    return False


def _resumen_error(response: requests.Response) -> str:
    try:
        j = response.json()
        if isinstance(j, dict):
            return j.get("message") or j.get("error") or j.get("code") or response.text[:100]
    except Exception:
        pass
    return (response.text or "")[:120]


# ── Login ────────────────────────────────────────────────────────────────────

def login_todos(ctx: Ctx) -> bool:
    """Loguea los 3 usuarios. Devuelve True si los 3 funcionaron."""
    res = ctx.resultado
    for rol, (email, password) in CREDENCIALES.items():
        try:
            r = post("/auth/login", json={"email": email, "password": password})
        except requests.RequestException as ex:
            fail(f"login {rol}", 0, f"sin conexión al backend: {ex}", res)
            return False

        body = expect_2xx(f"login {rol} ({email})", r, res,
                          extra_fn=lambda b: f"rol={b.get('rol', '?')}")
        if not body:
            return False
        token = body.get("token", "")
        if rol == "admin":
            ctx.token_admin = token
        elif rol == "funcionario":
            ctx.token_func = token
        else:
            ctx.token_cli = token
    return True


# ── Resumen final ────────────────────────────────────────────────────────────

def imprimir_resumen(res: Resultado, t_inicio: float) -> int:
    """Imprime el resumen final. Devuelve el código de salida (0 si no hay FAIL)."""
    duracion = time.time() - t_inicio
    print()
    print(Fore.CYAN + "─" * 78 + Style.RESET_ALL)
    color_ok = Fore.GREEN if res.ok else ""
    color_skip = Fore.YELLOW if res.skip else ""
    color_warn = Fore.YELLOW if res.warn else ""
    color_fail = Fore.RED if res.fail else ""
    print(
        f"  {color_ok}✅ {res.ok} OK{Style.RESET_ALL}   "
        f"{color_skip}⏭️  {res.skip} skip{Style.RESET_ALL}   "
        f"{color_warn}⚠️  {res.warn} warn{Style.RESET_ALL}   "
        f"{color_fail}❌ {res.fail} fail{Style.RESET_ALL}"
        f"   ({res.total()} total · {duracion:.1f}s)"
    )
    if res.fails:
        print()
        print(Fore.RED + "  Fallidos:" + Style.RESET_ALL)
        for f in res.fails:
            print(f"    • {f}")
    print(Fore.CYAN + "─" * 78 + Style.RESET_ALL)
    return 0 if res.fail == 0 else 1


# ── Misc ─────────────────────────────────────────────────────────────────────

def head_or_empty(items: Any, key: str = "id") -> str:
    """Devuelve items[0][key] o '' si la lista está vacía / formato raro."""
    if isinstance(items, list) and items:
        first = items[0]
        if isinstance(first, dict):
            return str(first.get(key, ""))
    return ""


def banner_inicio(titulo: str) -> float:
    print()
    print(Fore.MAGENTA + Style.BRIGHT + f"  {titulo}" + Style.RESET_ALL)
    print(Fore.MAGENTA + f"  Backend: {BACKEND_URL}" + Style.RESET_ALL)
    print(Fore.MAGENTA + f"  IA:      {IA_URL}" + Style.RESET_ALL)
    if SKIP_S3:
        print(Fore.YELLOW + "  S3 deshabilitado por SKIP_S3=1" + Style.RESET_ALL)
    return time.time()


# Asegura stdout UTF-8 en Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
    except Exception:
        pass
