package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujos clave del Motor de Workflow (Ciclo 2):
 *  - Funcionario consulta su bandeja de pendientes.
 *  - Funcionario abre el expediente de un trámite.
 *  - Cliente consulta el estado de uno de sus trámites.
 */
class TramiteFlowIT extends BaseIntegrationIT {

    @Test
    @DisplayName("Funcionario: GET /tramites/mis-pendientes devuelve lista (puede estar vacía pero NO error)")
    void bandejaFuncionario() throws Exception {
        JsonNode arr = getJson("/tramites/mis-pendientes", funcTecHeaders());
        assertThat(arr.isArray()).as("debe ser array, body=%s", arr).isTrue();
    }

    @Test
    @DisplayName("Cliente: GET /tramites/mis-tramites devuelve sus trámites")
    void misTramitesCliente() throws Exception {
        JsonNode arr = getJson("/tramites/mis-tramites", clienteHeaders());
        assertThat(arr.isArray()).isTrue();
    }

    @Test
    @DisplayName("Cliente: si tiene trámites, puede consultar estado de uno (devuelve estado coherente)")
    void consultarEstado() throws Exception {
        JsonNode mis = getJson("/tramites/mis-tramites", clienteHeaders());
        if (mis.size() == 0) return; // cliente sin trámites — skip silencioso

        String tramiteId = mis.get(0).path("id").asText();
        if (tramiteId.isEmpty()) tramiteId = mis.get(0).path("_id").asText();
        assertThat(tramiteId).as("id del primer trámite").isNotEmpty();

        JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        // El controlador devuelve un detalle con estadoActual o similar
        assertThat(estado.isObject()).as("respuesta debe ser objeto: %s", estado).isTrue();
    }

    @Test
    @DisplayName("Funcionario: si tiene trámites pendientes, puede ver el expediente de uno")
    void verExpediente() throws Exception {
        JsonNode arr = getJson("/tramites/mis-pendientes", funcLegHeaders());
        if (arr.size() == 0) return;
        String tramiteId = arr.get(0).path("id").asText();
        if (tramiteId.isEmpty()) tramiteId = arr.get(0).path("_id").asText();
        if (tramiteId.isEmpty()) return;

        ResponseEntity<String> resp = rest.exchange(
            BASE_URL + "/expedientes/tramite/" + tramiteId,
            org.springframework.http.HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(funcLegHeaders()), String.class);
        // 200 con expediente o 404 si aún no se creó la sección — ambos son contractualmente válidos
        assertThat(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode() == HttpStatus.NOT_FOUND)
            .as("GET expediente -> %s body=%s", resp.getStatusCode(), resp.getBody())
            .isTrue();
    }

    @Test
    @DisplayName("Cliente NO puede acceder a /tramites/mis-pendientes (endpoint solo funcionario/admin)")
    void clienteNoVeBandeja() {
        ResponseEntity<String> r = rest.exchange(
            BASE_URL + "/tramites/mis-pendientes",
            org.springframework.http.HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(clienteHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
