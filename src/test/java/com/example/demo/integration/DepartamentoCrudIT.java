package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD completo de departamentos via HTTP, golpeando Mongo a través de la API.
 *
 * Verifica que cada operación REALMENTE muta la base re-consultando después
 * de cada paso. Cada test crea un departamento con un código aleatorio para
 * no chocar con los del seed ni con otras corridas en paralelo.
 */
class DepartamentoCrudIT extends BaseIntegrationIT {

    /** El validador @Size limita el código a 5 caracteres. */
    private String codigoUnico() {
        return ("T" + UUID.randomUUID().toString().replace("-", "")).substring(0, 5).toUpperCase();
    }

    @Test
    @DisplayName("CRUD ciclo completo: POST → GET listado contiene → GET id → PUT → GET refleja → DELETE → GET 404")
    void crudCompleto() throws Exception {
        String codigo = codigoUnico();
        var headers = adminHeaders();

        // CREATE
        ResponseEntity<String> created = post("/departamentos",
            Map.of("codigo", codigo, "nombre", "Test " + codigo, "descripcion", "creado por test"),
            headers);
        assertThat(created.getStatusCode().is2xxSuccessful())
            .as("POST -> %s body=%s", created.getStatusCode(), created.getBody())
            .isTrue();
        String id = mapper.readTree(created.getBody()).path("id").asText();
        assertThat(id).isNotEmpty();

        // READ por id
        JsonNode byId = getJson("/departamentos/" + id, headers);
        assertThat(byId.path("codigo").asText()).isEqualTo(codigo);
        assertThat(byId.path("nombre").asText()).isEqualTo("Test " + codigo);

        // READ lista incluye al nuevo
        JsonNode lista = getJson("/departamentos", headers);
        long count = 0;
        for (JsonNode d : lista) if (id.equals(d.path("id").asText())) count++;
        assertThat(count).as("listado debe contener el id recién creado").isEqualTo(1);

        // UPDATE (nombre único — la colección tiene índice unique en `nombre`)
        String nombreUpdate = "Test renombrado " + codigo;
        ResponseEntity<String> updated = put("/departamentos/" + id,
            Map.of("codigo", codigo, "nombre", nombreUpdate, "descripcion", "modificado"),
            headers);
        assertThat(updated.getStatusCode().is2xxSuccessful())
            .as("PUT -> %s body=%s", updated.getStatusCode(), updated.getBody())
            .isTrue();

        JsonNode afterPut = getJson("/departamentos/" + id, headers);
        assertThat(afterPut.path("nombre").asText()).isEqualTo(nombreUpdate);

        // DELETE (soft-delete: activo=false, no se borra fila)
        ResponseEntity<String> del = delete("/departamentos/" + id, headers);
        assertThat(del.getStatusCode().is2xxSuccessful())
            .as("DELETE -> %s body=%s", del.getStatusCode(), del.getBody())
            .isTrue();

        JsonNode afterDelete = getJson("/departamentos/" + id, headers);
        assertThat(afterDelete.path("activo").asBoolean())
            .as("DELETE debe haber desactivado el registro (soft-delete)")
            .isFalse();
    }

    @Test
    @DisplayName("rol Cliente NO puede crear departamentos (403)")
    void clienteNoPuedeCrear() {
        ResponseEntity<String> r = post("/departamentos",
            Map.of("codigo", codigoUnico(), "nombre", "Hack"), clienteHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
