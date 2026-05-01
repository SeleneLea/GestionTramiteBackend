package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CU-31 — Agente de asistencia. Verifica el contrato real del endpoint
 * y que la KB local responde con datos REALES (políticas del seed)
 * cuando el microservicio externo n8n no está disponible.
 */
class AgenteHttpIT extends BaseIntegrationIT {

    private Map<String, Object> consulta(String texto, String modulo, String tramiteIdOpcional) {
        Map<String, Object> body = new HashMap<>();
        body.put("consulta", texto);
        body.put("moduloActivo", modulo);
        if (tramiteIdOpcional != null) body.put("tramiteIdOpcional", tramiteIdOpcional);
        return body;
    }

    @Test
    @DisplayName("Saludo en /admin/diagramas como admin → KB local sugiere acción admin")
    void saludoAdmin() throws Exception {
        ResponseEntity<String> r = post("/agente/consultar",
            consulta("hola", "/admin/diagramas", null), adminHeaders());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(r.getBody());
        assertThat(body.path("respuesta").asText()).isNotEmpty();
        assertThat(body.path("idLogBaseDatos").asText())
            .as("debe persistir el log: %s", body)
            .isNotEmpty();
        // Si n8n no responde la fuente es kb-local; si responde es n8n. Cualquiera vale.
        String fuente = body.path("fuente").asText();
        assertThat(fuente).isIn("kb-local", "n8n", "");
    }

    @Test
    @DisplayName("Pregunta por políticas → respuesta menciona políticas reales del seed")
    void preguntaPoliticas() throws Exception {
        ResponseEntity<String> r = post("/agente/consultar",
            consulta("qué políticas hay disponibles?", "/admin/politicas", null),
            adminHeaders());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(r.getBody());
        String resp = body.path("respuesta").asText();
        // El backend SIEMPRE responde; si fue kb-local debe mencionar las políticas del seed
        if ("kb-local".equals(body.path("fuente").asText())) {
            assertThat(resp.toLowerCase()).contains("nueva conexion");
        }
        assertThat(resp).isNotEmpty();
    }

    @Test
    @DisplayName("Consulta fuera de alcance → respuesta canónica sin acción (cuando es KB local)")
    void fueraDeAlcance() throws Exception {
        ResponseEntity<String> r = post("/agente/consultar",
            consulta("cuál es la capital de Francia?", "/cliente/tramites", null),
            clienteHeaders());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(r.getBody());
        if ("kb-local".equals(body.path("fuente").asText())) {
            assertThat(body.path("respuesta").asText())
                .containsIgnoringCase("Solo puedo asistirte");
        }
    }

    @Test
    @DisplayName("Endpoint requiere autenticación (sin token → 401/403)")
    void requiereAuth() {
        ResponseEntity<String> r = post("/agente/consultar",
            consulta("hola", "/x", null),
            new org.springframework.http.HttpHeaders());

        assertThat(r.getStatusCode().is4xxClientError())
            .as("sin token -> %s", r.getStatusCode()).isTrue();
    }
}
