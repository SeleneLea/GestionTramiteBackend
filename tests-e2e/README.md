# tests-e2e — Smoke tests HTTP contra el backend en vivo

Suite Python que verifica **todos los endpoints REST** del backend Spring Boot
(Parte 1 + Parte 2) contra una instancia corriendo en local. No usa Mongo
mockeado ni Spring TestContext — pega contra el servidor real.

## Qué cubre

**Parte 1**
- Auth (login, perfil)
- Catálogos: usuarios, roles, permisos, departamentos, actividades, documentos
- Políticas, diagramas, nodos, transiciones
- Workflow: listar trámites del cliente, bandeja del funcionario, estado, flujo completo
- Expediente digital
- Notificaciones
- Métricas y cuellos de botella
- Reportes clásicos
- Agente IA (CU-31)
- Historial y trazabilidad

**Parte 2**
- Documental: repositorio (CU-32), subir (CU-33), preview (CU-34), versionar (CU-35),
  permisos por punto de atención (CU-36), auditoría (CU-37), edición colaborativa REST (CU-38)
- IA: dictar (CU-39), sugerir política (CU-40), reporte natural (CU-41),
  ruta óptima (CU-42), trámites en riesgo (CU-43), bandeja IA (CU-44), anomalías (CU-45)

## Requisitos

- Python 3.10+ con `pip install -r requirements.txt`
- Backend Spring corriendo en `http://localhost:8080` (default)
- Microservicio Python opcional en `http://localhost:8001` (los tests IA se marcan SKIP si está caído)
- Los seeders del backend deben haber corrido (usuarios admin/cliente/funcionario)

## Cómo ejecutar

```powershell
cd Backend\tests-e2e
pip install -r requirements.txt

# Toda la suite
python run_all.py

# Solo Parte 1
python tests_p1.py

# Solo Parte 2
python tests_p2.py
```

## Variables de entorno (opcionales)

| Var | Default | Uso |
|-----|---------|-----|
| `BACKEND_URL` | `http://localhost:8080/api` | Base URL del Spring |
| `IA_URL` | `http://localhost:8001` | Microservicio Python |
| `EMAIL_ADMIN` | `admin@cre.bo` | Credenciales admin |
| `PASS_ADMIN` | `admin12345` | |
| `EMAIL_FUNC` | `funcionario@cre.bo` | Credenciales funcionario |
| `PASS_FUNC` | `func12345` | |
| `EMAIL_CLI` | `cliente@cre.bo` | Credenciales cliente |
| `PASS_CLI` | `cliente12345` | |
| `SKIP_S3` | (vacío) | Si está definido, salta los tests que requieren S3 (CU-33/34/35) |

## Códigos de resultado

- ✅ **OK** — respuesta 2xx esperada
- ⏭️ **SKIP** — dependencia no disponible (S3, microservicio, etc.)
- ⚠️ **WARN** — endpoint respondió pero con un código inesperado tolerable
- ❌ **FAIL** — endpoint roto o respuesta incorrecta

Al final imprime un resumen con la suma de cada categoría y el código de salida
es `0` si no hubo FAILs.

## Notas

- La suite es **no destructiva**: las entidades temporales que crea llevan
  prefijo `_E2E_` y se eliminan al final.
- Los tests de workflow NO inician trámites nuevos para no consumir códigos
  del seed; solo leen los existentes.
- El test de SSE (`/api/notificaciones/stream`) solo abre y cierra la
  conexión para verificar el upgrade — no espera eventos.
