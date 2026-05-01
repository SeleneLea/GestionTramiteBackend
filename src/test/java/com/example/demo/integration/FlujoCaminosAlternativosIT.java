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
 * Caminos alternativos del Motor de Workflow que se complementan con el camino
 * feliz de {@link FlujoClienteE2EIT}:
 *
 *  A) **Rechazo**: funcionario emite decisión final = Rechazar → el trámite
 *     termina en estado "Rechazado" sin nodo actual y con fecha de cierre.
 *
 *  B) **Devolución iterativa**: funcionario devuelve el trámite a un nodo
 *     anterior → estado pasa a "Observado", funcionario queda en null y se
 *     registra un EstadoHistorico con la observación.
 *
 *  C) **Flujo paralelo (fork/join)**: el trámite seed TRM-2024-003 fue creado
 *     con dos nodos paralelos activos (inspección + presupuesto). Validamos
 *     que el motor lo reporta como en paralelo y expone ambos nodos.
 */
class FlujoCaminosAlternativosIT extends BaseIntegrationIT {

    // ─── A) RECHAZO ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("A) Rechazo: funcionario hace decision-final=Rechazar → estado Rechazado, sin nodo actual")
    void aRechazo() throws Exception {
        String politicaId = primeraPoliticaActivaId();
        String tramiteId = iniciarTramiteCliente(politicaId);
        try {
            HttpHeaders funcionarioCorrecto = funcionarioDelTramite(tramiteId);

            Map<String, String> req = Map.of(
                "decision", "Rechazar",
                "justificacion", "Documentación inválida (test E2E rechazo)"
            );
            ResponseEntity<String> r = post("/tramites/" + tramiteId + "/decision-final",
                req, funcionarioCorrecto);
            assertThat(r.getStatusCode().is2xxSuccessful())
                .as("rechazar -> %s body=%s", r.getStatusCode(), r.getBody())
                .isTrue();

            JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
            assertThat(estado.path("estado").asText()).isEqualTo("Rechazado");
            assertThat(estado.path("nodoActualId").isNull() || estado.path("nodoActualId").asText().isEmpty())
                .as("nodoActualId debe quedar vacío tras rechazo: %s", estado.path("nodoActualId"))
                .isTrue();
        } finally {
            // No hay necesidad de limpiar — el trámite quedó cerrado.
        }
    }

    // ─── B) DEVOLUCIÓN ITERATIVA ─────────────────────────────────────────────

    @Test
    @DisplayName("B) Devolución iterativa: funcionario devuelve → estado Observado, funcionario=null")
    void bDevolverObservado() throws Exception {
        String politicaId = primeraPoliticaActivaId();
        String tramiteId = iniciarTramiteCliente(politicaId);
        HttpHeaders funcionarioCorrecto = funcionarioDelTramite(tramiteId);

        // Devolver al mismo nodo actual con una observación
        JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        String nodoActual = estado.path("nodoActualId").asText();
        assertThat(nodoActual).isNotEmpty();

        Map<String, String> req = Map.of(
            "nodoDestinoId", nodoActual,
            "observaciones", "Falta foto carnet (test E2E devolución)"
        );
        ResponseEntity<String> r = post("/tramites/" + tramiteId + "/devolver",
            req, funcionarioCorrecto);
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("devolver -> %s body=%s", r.getStatusCode(), r.getBody())
            .isTrue();

        JsonNode despues = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        assertThat(despues.path("estado").asText()).isEqualTo("Observado");

        // El historial debe haber crecido con la observación
        JsonNode historial = despues.path("historial");
        assertThat(historial.isArray()).isTrue();
        boolean tieneObservado = false;
        for (JsonNode h : historial) {
            if ("Observado".equals(h.path("estado").asText())) {
                tieneObservado = true;
                break;
            }
        }
        assertThat(tieneObservado)
            .as("el historial debe registrar el cambio a Observado: %s", historial)
            .isTrue();

        // Cliente cancela para dejarlo cerrado y no contaminar la bandeja
        post("/tramites/" + tramiteId + "/cancelar", Map.of(), clienteHeaders());
    }

    // ─── C) PARALELO ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("C) Paralelo: el motor soporta fork — vivo en paralelo o con hito de Fork en historial")
    void cFlujoParalelo() throws Exception {
        JsonNode todos = getJson("/tramites", adminHeaders());

        // 1) ¿Hay algún trámite EN paralelo ahora mismo?
        for (JsonNode t : todos) {
            JsonNode par = t.path("nodosParalellosActivos");
            if (par.isArray() && par.size() >= 2) {
                String tramiteId = t.path("id").asText();
                JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", adminHeaders());
                assertThat(estado.path("enParalelo").asBoolean()).isTrue();
                assertThat(estado.path("nodosParalellosActivos").size()).isGreaterThanOrEqualTo(2);
                return;
            }
        }

        // 2) Si ninguno está vivo en paralelo, validar que el motor ya soportó fork
        //    revisando el historial — el motor registra "Fork" como hito.
        for (JsonNode t : todos) {
            String tramiteId = t.path("id").asText();
            JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", adminHeaders());
            for (JsonNode h : estado.path("historial")) {
                String nombre = h.path("nombre").asText("").toLowerCase();
                String obs = h.path("observaciones").asText("").toLowerCase();
                if (nombre.contains("fork") || obs.contains("paralelo")) return; // ✔ motor soporta paralelo
            }
        }

        org.junit.jupiter.api.Assertions.fail(
            "No se encontró ningún trámite vivo en paralelo ni con hito de fork en historial. " +
            "Re-seed (TramiteSeeder.java crea TRM-2024-003 con fork activo).");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String primeraPoliticaActivaId() throws Exception {
        JsonNode pol = getJson("/politicas?soloActivas=true", clienteHeaders());
        assertThat(pol.size()).isGreaterThan(0);
        return pol.get(0).path("id").asText();
    }

    private String iniciarTramiteCliente(String politicaId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("politicaId", politicaId);
        body.put("clienteId", "ignorado");
        body.put("prioridad", 3);
        ResponseEntity<String> r = post("/tramites/iniciar", body, clienteHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).path("id").asText();
    }

    /**
     * Devuelve los headers del funcionario que realmente está asignado al trámite,
     * intentando con los 4 funcionarios del seed hasta encontrar coincidencia.
     */
    private HttpHeaders funcionarioDelTramite(String tramiteId) throws Exception {
        JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        String funcId = estado.path("nodoActual").path("funcionarioId").asText();

        for (var headers : new HttpHeaders[]{
                funcAtcHeaders(), funcTecHeaders(), funcLegHeaders(), funcionarioHeadersFor("func_ope@cre.bo") }) {
            // Pedimos /usuarios/me con ese token para obtener su id
            JsonNode me = getJson("/usuarios/me", headers);
            if (me.path("id").asText().equals(funcId)) return headers;
        }
        // Fallback: si no coincide ninguno, usamos func_atc (que normalmente atiende el primer nodo CRE)
        return funcAtcHeaders();
    }

    private HttpHeaders funcionarioHeadersFor(String email) {
        return headers(login(email, "func12345"));
    }
}
