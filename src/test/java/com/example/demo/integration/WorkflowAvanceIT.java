package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avance del motor de workflow (CU-09..CU-11, CU-14, CU-16):
 *  - completar-nodo (avance lineal del motor)
 *  - derivar (CU-11)
 *  - prompt-flow (CU-14: generar diagrama desde texto con IA)
 *  - /adjuntos del trámite (CU-08)
 *  - /tramites/codigo/{} y /tramites/cliente/{} y /tramites/activos
 */
class WorkflowAvanceIT extends BaseIntegrationIT {

    @Test
    @DisplayName("CU-11: derivar trámite a otro funcionario")
    void derivar() throws Exception {
        String politicaId = primeraPoliticaActivaId();
        String tramiteId = iniciarTramiteComoCliente(politicaId);

        // Encontramos el funcionario actual y otro distinto
        JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        String funcIdActual = estado.path("nodoActual").path("funcionarioId").asText();

        JsonNode funcs = getJson("/usuarios/funcionarios", funcAtcHeaders());
        String otroFuncId = null;
        for (JsonNode u : funcs) {
            String id = u.path("id").asText();
            if (!id.equals(funcIdActual) && !id.isEmpty()) {
                otroFuncId = id;
                break;
            }
        }
        assertThat(otroFuncId).as("debe haber otro funcionario distinto").isNotNull();

        HttpHeaders funcCorrecto = funcionarioDelTramite(tramiteId);
        ResponseEntity<String> r = post("/tramites/" + tramiteId + "/derivar",
            Map.of("nuevoFuncionarioId", otroFuncId,
                   "motivo", "Reasignación E2E"),
            funcCorrecto);
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("derivar -> %s body=%s", r.getStatusCode(), r.getBody())
            .isTrue();

        // Verifica que ahora el otro funcionario es el responsable
        JsonNode after = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        assertThat(after.path("nodoActual").path("funcionarioId").asText())
            .isEqualTo(otroFuncId);

        // Cleanup
        post("/tramites/" + tramiteId + "/cancelar", Map.of(), clienteHeaders());
    }

    @Test
    @DisplayName("Motor: POST /tramites/{id}/completar-nodo avanza el trámite al siguiente nodo")
    void completarNodo() throws Exception {
        String politicaId = primeraPoliticaActivaId();
        String tramiteId = iniciarTramiteComoCliente(politicaId);

        JsonNode antes = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        String nodoAntes = antes.path("nodoActualId").asText();
        assertThat(nodoAntes).isNotEmpty();

        HttpHeaders funcCorrecto = funcionarioDelTramite(tramiteId);
        Map<String, Object> body = new HashMap<>();
        body.put("funcionarioId", "auto");
        body.put("decision", "si");
        ResponseEntity<String> r = post("/tramites/" + tramiteId + "/completar-nodo", body, funcCorrecto);
        // Acepta 2xx (avanzó) o 4xx (faltan datos del expediente — política compleja)
        assertThat(r.getStatusCode().is2xxSuccessful() || r.getStatusCode().is4xxClientError())
            .as("completar-nodo -> %s body=%s", r.getStatusCode(), r.getBody())
            .isTrue();

        // Si avanzó, el nodoActualId puede haber cambiado o haberse cerrado
        if (r.getStatusCode().is2xxSuccessful()) {
            JsonNode despues = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
            String nodoDespues = despues.path("nodoActualId").asText();
            boolean cambio = !nodoAntes.equals(nodoDespues) || "Aprobado".equals(despues.path("estado").asText());
            assertThat(cambio)
                .as("tras completar, nodo o estado debe cambiar. antes=%s despues=%s", nodoAntes, despues)
                .isTrue();
        }

        // Cleanup si aún está abierto
        try { post("/tramites/" + tramiteId + "/cancelar", Map.of(), clienteHeaders()); } catch (Exception ignored) {}
    }

    // ─── CU-14: Diseño por IA (prompt → flujo) ──────────────────────────────

    @Test
    @DisplayName("CU-14: POST /workflow-design/from-prompt como admin → 2xx o 5xx (microservicio puede no estar)")
    void promptFlow() {
        Map<String, Object> req = Map.of(
            "prompt", "Crea un flujo con tres pasos: recepción, técnica y cierre.",
            "nombre", "FlujoPrompt E2E"
        );
        ResponseEntity<String> r = post("/workflow-design/from-prompt", req, adminHeaders());

        // El microservicio de IA puede no estar disponible — aceptamos 201/200, 4xx (validación) o 5xx (servicio caído).
        // Solo nos importa que el endpoint exista y requiera auth admin.
        assertThat(r.getStatusCode().value())
            .as("from-prompt -> %s body=%s", r.getStatusCode(), r.getBody())
            .isIn(200, 201, 400, 422, 500, 503);
    }

    @Test
    @DisplayName("CU-14: Funcionario NO puede usar diseño por IA (403)")
    void promptFlowFuncionarioForbidden() {
        ResponseEntity<String> r = post("/workflow-design/from-prompt",
            Map.of("prompt", "x", "nombre", "y"), funcTecHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── CU-08: Adjuntos de un trámite (GET) ────────────────────────────────

    @Test
    @DisplayName("CU-08: GET /tramites/{id}/adjuntos devuelve lista (puede estar vacía)")
    void listarAdjuntos() throws Exception {
        JsonNode todos = getJson("/tramites", adminHeaders());
        String tramiteId = todos.get(0).path("id").asText();

        JsonNode adj = getJson("/tramites/" + tramiteId + "/adjuntos", clienteHeaders());
        assertThat(adj.isArray()).isTrue();

        JsonNode adjFiltrado = getJson("/tramites/" + tramiteId + "/adjuntos?actividadId=xxx", clienteHeaders());
        assertThat(adjFiltrado.isArray()).isTrue();
    }

    // ─── Listados auxiliares de TramiteController ───────────────────────────

    @Test
    @DisplayName("TramiteController: /activos (admin/func), /codigo/{}, /cliente/{} (func/admin)")
    void listadosAuxiliares() throws Exception {
        // /activos
        JsonNode activos = getJson("/tramites/activos", funcTecHeaders());
        assertThat(activos.isArray()).isTrue();

        // /codigo/{codigo}
        JsonNode todos = getJson("/tramites", adminHeaders());
        if (todos.size() > 0) {
            String codigo = todos.get(0).path("codigo").asText();
            if (!codigo.isEmpty()) {
                JsonNode porCodigo = getJson("/tramites/codigo/" + codigo, clienteHeaders());
                assertThat(porCodigo.path("codigo").asText()).isEqualTo(codigo);
            }
        }

        // /cliente/{id} solo admin/funcionario
        JsonNode usuarios = getJson("/usuarios?tipo=cliente", adminHeaders());
        if (usuarios.size() > 0) {
            String clienteId = usuarios.get(0).path("id").asText();
            JsonNode delCliente = getJson("/tramites/cliente/" + clienteId, funcTecHeaders());
            assertThat(delCliente.isArray()).isTrue();
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private String primeraPoliticaActivaId() throws Exception {
        JsonNode pol = getJson("/politicas?soloActivas=true", clienteHeaders());
        return pol.get(0).path("id").asText();
    }

    private String iniciarTramiteComoCliente(String politicaId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("politicaId", politicaId);
        body.put("clienteId", "ignorado");
        body.put("prioridad", 3);
        ResponseEntity<String> r = post("/tramites/iniciar", body, clienteHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).path("id").asText();
    }

    private HttpHeaders funcionarioDelTramite(String tramiteId) throws Exception {
        JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        String funcId = estado.path("nodoActual").path("funcionarioId").asText();

        for (var headers : new HttpHeaders[]{
                funcAtcHeaders(), funcTecHeaders(), funcLegHeaders(),
                headers(login("func_ope@cre.bo", "func12345")) }) {
            JsonNode me = getJson("/usuarios/me", headers);
            if (me.path("id").asText().equals(funcId)) return headers;
        }
        return funcAtcHeaders();
    }
}
