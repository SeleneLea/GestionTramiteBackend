package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo end-to-end desde la **vista del administrador**:
 *
 *   1. Crea un departamento nuevo.
 *   2. Crea una política nueva (borrador).
 *   3. Crea un diagrama de workflow asociado a esa política.
 *   4. Agrega nodos: inicio → actividad (en el depto nuevo) → fin.
 *   5. Conecta los nodos con dos transiciones secuenciales.
 *   6. Publica el diagrama.
 *   7. Activa la política (que ya tiene diagrama publicado).
 *
 * Al final libera lo que pueda (delete archivada) — el seed queda intacto.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlujoAdminE2EIT extends BaseIntegrationIT {

    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 4);
    private static final String DEPTO_CODIGO = ("E" + SUFFIX).toUpperCase();
    private static final String DEPTO_NOMBRE = "Depto E2E " + SUFFIX;
    private static final String POLITICA_NOMBRE = "Política E2E " + SUFFIX;
    private static final String DIAGRAMA_NOMBRE = "Diagrama E2E " + SUFFIX;

    private static String deptoId;
    private static String politicaId;
    private static String diagramaId;
    private static String nodoInicioId;
    private static String nodoActividadId;
    private static String nodoFinId;

    @Test
    @Order(1)
    @DisplayName("Paso 1 — Admin crea un departamento nuevo")
    void paso1_crearDepto() throws Exception {
        ResponseEntity<String> r = post("/departamentos",
            Map.of("codigo", DEPTO_CODIGO, "nombre", DEPTO_NOMBRE,
                   "descripcion", "departamento e2e"),
            adminHeaders());
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("crear depto -> %s body=%s", r.getStatusCode(), r.getBody()).isTrue();
        deptoId = mapper.readTree(r.getBody()).path("id").asText();
        assertThat(deptoId).isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("Paso 2 — Admin crea una política (borrador)")
    void paso2_crearPolitica() throws Exception {
        ResponseEntity<String> r = post("/politicas",
            Map.of("nombre", POLITICA_NOMBRE, "descripcion", "e2e", "categoria", "e2e"),
            adminHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = mapper.readTree(r.getBody());
        politicaId = body.path("id").asText();
        assertThat(politicaId).isNotEmpty();
        assertThat(body.path("estado").asText()).isEqualTo("borrador");
    }

    @Test
    @Order(3)
    @DisplayName("Paso 3 — Admin crea diagrama asociado a la política")
    void paso3_crearDiagrama() throws Exception {
        ResponseEntity<String> r = post("/diagramas",
            Map.of("nombre", DIAGRAMA_NOMBRE, "politicaId", politicaId),
            adminHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        diagramaId = mapper.readTree(r.getBody()).path("id").asText();
        assertThat(diagramaId).isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("Paso 4 — Admin agrega 3 nodos al diagrama (inicio, actividad, fin)")
    void paso4_agregarNodos() throws Exception {
        ResponseEntity<String> rInicio = post("/diagramas/" + diagramaId + "/nodos",
            Map.of("nombre", "Inicio", "tipo", "inicio", "orden", 0),
            adminHeaders());
        assertThat(rInicio.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        nodoInicioId = mapper.readTree(rInicio.getBody()).path("id").asText();

        ResponseEntity<String> rActividad = post("/diagramas/" + diagramaId + "/nodos",
            Map.of("nombre", "Tarea E2E", "tipo", "actividad",
                   "departamentoId", deptoId, "orden", 1),
            adminHeaders());
        assertThat(rActividad.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        nodoActividadId = mapper.readTree(rActividad.getBody()).path("id").asText();

        ResponseEntity<String> rFin = post("/diagramas/" + diagramaId + "/nodos",
            Map.of("nombre", "Fin", "tipo", "fin", "orden", 2),
            adminHeaders());
        assertThat(rFin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        nodoFinId = mapper.readTree(rFin.getBody()).path("id").asText();

        // Verifica que los 3 nodos están en el diagrama
        JsonNode nodos = getJson("/diagramas/" + diagramaId + "/nodos", adminHeaders());
        assertThat(nodos.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(5)
    @DisplayName("Paso 5 — Admin conecta los nodos con transiciones secuenciales")
    void paso5_agregarTransiciones() throws Exception {
        ResponseEntity<String> r1 = post("/diagramas/" + diagramaId + "/transiciones",
            Map.of("nodoOrigenId", nodoInicioId, "nodoDestinoId", nodoActividadId,
                   "tipo", "secuencial"),
            adminHeaders());
        assertThat(r1.getStatusCode())
            .as("conectar inicio->actividad -> %s body=%s", r1.getStatusCode(), r1.getBody())
            .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> r2 = post("/diagramas/" + diagramaId + "/transiciones",
            Map.of("nodoOrigenId", nodoActividadId, "nodoDestinoId", nodoFinId,
                   "tipo", "secuencial"),
            adminHeaders());
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode trans = getJson("/diagramas/" + diagramaId + "/transiciones", adminHeaders());
        assertThat(trans.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(6)
    @DisplayName("Paso 6 — Admin publica el diagrama")
    void paso6_publicarDiagrama() throws Exception {
        ResponseEntity<String> r = rest.exchange(
            BASE_URL + "/diagramas/" + diagramaId + "/estado",
            HttpMethod.PATCH,
            new org.springframework.http.HttpEntity<>(Map.of("estado", "publicado"), adminHeaders()),
            String.class);
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("publicar diagrama -> %s body=%s", r.getStatusCode(), r.getBody())
            .isTrue();
        assertThat(mapper.readTree(r.getBody()).path("estado").asText()).isEqualTo("publicado");
    }

    @Test
    @Order(7)
    @DisplayName("Paso 7 — Admin activa la política (ya tiene diagrama publicado)")
    void paso7_activarPolitica() throws Exception {
        ResponseEntity<String> r = rest.exchange(
            BASE_URL + "/politicas/" + politicaId + "/estado",
            HttpMethod.PATCH,
            new org.springframework.http.HttpEntity<>(Map.of("estado", "activa"), adminHeaders()),
            String.class);
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("activar política -> %s body=%s", r.getStatusCode(), r.getBody())
            .isTrue();
        assertThat(mapper.readTree(r.getBody()).path("estado").asText()).isEqualTo("activa");
    }

    @AfterAll
    static void limpiar() {
        // Archiva y borra la política creada; el depto queda como soft-delete.
        try {
            if (politicaId != null) {
                rest.exchange(BASE_URL + "/politicas/" + politicaId + "/estado",
                    HttpMethod.PATCH,
                    new org.springframework.http.HttpEntity<>(
                        Map.of("estado", "archivada"), authHeadersStatic()),
                    String.class);
                rest.exchange(BASE_URL + "/politicas/" + politicaId,
                    HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(authHeadersStatic()),
                    String.class);
            }
            if (deptoId != null) {
                rest.exchange(BASE_URL + "/departamentos/" + deptoId,
                    HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(authHeadersStatic()),
                    String.class);
            }
        } catch (Exception ignored) { /* mejor effort */ }
    }

    private static org.springframework.http.HttpHeaders authHeadersStatic() {
        // Login admin para la limpieza (sin instanciar la subclase)
        var body = Map.of("email", "admin@cre.bo", "password", "admin12345");
        var resp = rest.postForEntity(BASE_URL + "/auth/login", body, String.class);
        try {
            String token = mapper.readTree(resp.getBody()).path("token").asText();
            var h = new org.springframework.http.HttpHeaders();
            h.setBearerAuth(token);
            h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return h;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
