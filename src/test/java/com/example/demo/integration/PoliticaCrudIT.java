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
 * CRUD + transición de estados de Políticas de Negocio.
 * Estados: borrador → (activa | archivada) → eliminar (solo borrador/archivada).
 * Regla de negocio: una política sólo puede activarse si tiene diagrama de flujo.
 */
class PoliticaCrudIT extends BaseIntegrationIT {

    @Test
    @DisplayName("CRUD + transición borrador → archivada → eliminar")
    void crudYTransiciones() throws Exception {
        String nombre = "TestPol-" + UUID.randomUUID().toString().substring(0, 6);
        var headers = adminHeaders();

        ResponseEntity<String> created = post("/politicas",
            Map.of("nombre", nombre, "descripcion", "test", "categoria", "test"),
            headers);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode bodyCreated = mapper.readTree(created.getBody());
        String id = bodyCreated.path("id").asText();
        assertThat(id).isNotEmpty();
        assertThat(bodyCreated.path("estado").asText()).isEqualTo("borrador");

        // UPDATE en borrador
        ResponseEntity<String> updated = put("/politicas/" + id,
            Map.of("nombre", nombre, "descripcion", "actualizada", "categoria", "x"),
            headers);
        assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(getJson("/politicas/" + id, headers).path("descripcion").asText())
            .isEqualTo("actualizada");

        // ARCHIVAR (sin pasar por activa porque eso requiere diagrama)
        ResponseEntity<String> archivada = rest.exchange(
            BASE_URL + "/politicas/" + id + "/estado",
            org.springframework.http.HttpMethod.PATCH,
            new org.springframework.http.HttpEntity<>(Map.of("estado", "archivada"), headers),
            String.class);
        assertThat(archivada.getStatusCode().is2xxSuccessful())
            .as("PATCH archivar -> %s body=%s", archivada.getStatusCode(), archivada.getBody())
            .isTrue();
        assertThat(getJson("/politicas/" + id, headers).path("estado").asText())
            .isEqualTo("archivada");

        // DELETE permitido en archivada
        ResponseEntity<String> del = delete("/politicas/" + id, headers);
        assertThat(del.getStatusCode().is2xxSuccessful())
            .as("DELETE -> %s body=%s", del.getStatusCode(), del.getBody())
            .isTrue();

        // 404 después de delete (hard-delete en este caso)
        ResponseEntity<String> after = rest.exchange(BASE_URL + "/politicas/" + id,
            org.springframework.http.HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(headers), String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Activar política sin diagrama → 400 con mensaje sobre diagrama (regla de negocio)")
    void activarSinDiagrama() throws Exception {
        String nombre = "TestPolNoDiag-" + UUID.randomUUID().toString().substring(0, 6);
        var headers = adminHeaders();

        ResponseEntity<String> created = post("/politicas",
            Map.of("nombre", nombre, "descripcion", "test", "categoria", "test"),
            headers);
        String id = mapper.readTree(created.getBody()).path("id").asText();

        ResponseEntity<String> activar = rest.exchange(
            BASE_URL + "/politicas/" + id + "/estado",
            org.springframework.http.HttpMethod.PATCH,
            new org.springframework.http.HttpEntity<>(Map.of("estado", "activa"), headers),
            String.class);
        assertThat(activar.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(activar.getBody()).containsIgnoringCase("diagrama");

        // Limpieza: archivar y borrar
        rest.exchange(BASE_URL + "/politicas/" + id + "/estado",
            org.springframework.http.HttpMethod.PATCH,
            new org.springframework.http.HttpEntity<>(Map.of("estado", "archivada"), headers),
            String.class);
        delete("/politicas/" + id, headers);
    }

    @Test
    @DisplayName("Cliente no puede crear políticas (403)")
    void clienteNoPuedeCrear() {
        ResponseEntity<String> r = post("/politicas",
            Map.of("nombre", "hack-" + UUID.randomUUID()), clienteHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("?soloActivas=true devuelve sólo políticas activas")
    void filtroSoloActivas() throws Exception {
        JsonNode arr = getJson("/politicas?soloActivas=true", adminHeaders());
        for (JsonNode p : arr) {
            assertThat(p.path("estado").asText()).isEqualTo("activa");
        }
    }
}
