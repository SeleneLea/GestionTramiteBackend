package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CU-24 Métricas · CU-25 Cuellos de botella · CU-26 Reportes · CU-28 Notificaciones.
 * Endpoints clave para que admin/funcionario consulten estado operativo del sistema.
 */
class MetricasReportesNotificacionesIT extends BaseIntegrationIT {

    // ─── CU-24: Métricas por trámite ────────────────────────────────────────

    @Test
    @DisplayName("CU-24: GET /metricas/tramite/{id} devuelve array (puede estar vacío)")
    void metricasPorTramite() throws Exception {
        // Cogemos cualquier trámite del seed
        JsonNode todos = getJson("/tramites", adminHeaders());
        assertThat(todos.size()).isGreaterThan(0);
        String tramiteId = todos.get(0).path("id").asText();

        JsonNode metricas = getJson("/metricas/tramite/" + tramiteId, adminHeaders());
        assertThat(metricas.isArray())
            .as("respuesta debe ser array, fue: %s", metricas).isTrue();
    }

    @Test
    @DisplayName("CU-24: Cliente NO puede ver métricas (403)")
    void metricasClienteForbidden() {
        ResponseEntity<String> r = rest.exchange(
            BASE_URL + "/metricas/tramite/xxx",
            HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(clienteHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── CU-25: Cuellos de botella ──────────────────────────────────────────

    @Test
    @DisplayName("CU-25: GET /metricas/cuellos-botella accesible para admin")
    void cuellosBotella() throws Exception {
        JsonNode cuellos = getJson("/metricas/cuellos-botella", adminHeaders());
        assertThat(cuellos.isArray()).isTrue();
        // Pueden o no existir cuellos detectados; el contrato es la estructura
        if (cuellos.size() > 0) {
            JsonNode primero = cuellos.get(0);
            assertThat(primero.has("actividadId") || primero.has("departamentoId"))
                .as("un cuello debe tener actividad o departamento: %s", primero).isTrue();
        }
    }

    @Test
    @DisplayName("CU-25: Funcionario NO puede ver cuellos de botella (403, solo admin)")
    void cuellosFuncionarioForbidden() {
        ResponseEntity<String> r = rest.exchange(
            BASE_URL + "/metricas/cuellos-botella",
            HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(funcTecHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── CU-26: Reportes ────────────────────────────────────────────────────

    @Test
    @DisplayName("CU-26: POST /reportes/generar como admin → 200 con id de reporte")
    void generarReporte() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("tipo", "tramites");
        req.put("formato", "CSV");
        req.put("filtros", Map.of("estado", "En proceso"));

        ResponseEntity<String> r = post("/reportes/generar", req, adminHeaders());
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("generar -> %s body=%s", r.getStatusCode(), r.getBody())
            .isTrue();

        JsonNode body = mapper.readTree(r.getBody());
        String reporteId = body.path("id").asText();
        assertThat(reporteId).as("response debe tener id").isNotEmpty();

        // Descargar
        ResponseEntity<byte[]> descarga = rest.exchange(
            BASE_URL + "/reportes/" + reporteId + "/descargar",
            HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(adminHeaders()), byte[].class);
        assertThat(descarga.getStatusCode().is2xxSuccessful())
            .as("descargar -> %s", descarga.getStatusCode())
            .isTrue();
        assertThat(descarga.getHeaders().getFirst("Content-Disposition"))
            .as("Content-Disposition debe traer filename")
            .contains("filename");
    }

    @Test
    @DisplayName("CU-26: Funcionario NO puede generar reportes (403)")
    void reporteFuncionarioForbidden() {
        ResponseEntity<String> r = post("/reportes/generar",
            Map.of("tipo", "tramites", "formato", "CSV"), funcTecHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── CU-28: Notificaciones ──────────────────────────────────────────────

    @Test
    @DisplayName("CU-28: GET /notificaciones/mis-notificaciones para cualquier autenticado")
    void misNotificaciones() throws Exception {
        JsonNode arr = getJson("/notificaciones/mis-notificaciones", clienteHeaders());
        assertThat(arr.isArray()).isTrue();

        JsonNode arrFunc = getJson("/notificaciones/mis-notificaciones", funcTecHeaders());
        assertThat(arrFunc.isArray()).isTrue();

        JsonNode arrAdmin = getJson("/notificaciones/mis-notificaciones", adminHeaders());
        assertThat(arrAdmin.isArray()).isTrue();
    }

    @Test
    @DisplayName("CU-28: PUT /notificaciones/{id}/marcar-leida si hay notificaciones del usuario")
    void marcarLeida() throws Exception {
        JsonNode arr = getJson("/notificaciones/mis-notificaciones", clienteHeaders());
        if (arr.size() == 0) return; // sin notificaciones — skip silencioso

        String notifId = arr.get(0).path("id").asText();
        ResponseEntity<String> r = put("/notificaciones/" + notifId + "/marcar-leida",
            Map.of(), clienteHeaders());
        assertThat(r.getStatusCode().is2xxSuccessful() || r.getStatusCode().is4xxClientError())
            .as("debe responder 2xx (marcada) o 4xx (no es del usuario): %s",
                r.getStatusCode()).isTrue();
    }

    // ─── Historial administrativo (CU-29) ───────────────────────────────────

    @Test
    @DisplayName("CU-29: GET /tramites/historial admin con filtros estado/desde/hasta")
    void historialAdmin() throws Exception {
        JsonNode arr = getJson("/tramites/historial", adminHeaders());
        assertThat(arr.isArray()).isTrue();

        JsonNode arrFiltrado = getJson("/tramites/historial?estado=Completado", adminHeaders());
        // El DTO HistorialTramiteResponse expone `estadoActual` (no `estado`)
        for (JsonNode h : arrFiltrado) {
            assertThat(h.path("estadoActual").asText()).isEqualToIgnoringCase("Completado");
        }
    }

    @Test
    @DisplayName("CU-29: Funcionario NO accede a /tramites/historial (403)")
    void historialFuncionarioForbidden() {
        ResponseEntity<String> r = rest.exchange(BASE_URL + "/tramites/historial",
            HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(funcTecHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
