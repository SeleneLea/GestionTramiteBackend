package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CU-15 colaboración en diagramas · CU-10 ver expediente · CU-16 completar sección.
 * Más Formulario/CampoPlantilla del diagrama (CU-13).
 */
class ColaboracionExpedienteIT extends BaseIntegrationIT {

    private static String politicaIdTemp;
    private static String diagramaIdTemp;
    private static String formularioIdTemp;
    private static String campoIdTemp;

    // ─── CU-15: Colaboración en diagrama ────────────────────────────────────

    @Test
    @DisplayName("CU-15: Admin crea diagrama, invita a funcionario, funcionario responde ACEPTAR")
    void colaboracionInvitarYResponder() throws Exception {
        var admin = adminHeaders();

        // Crear política y diagrama temporal para invitar
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        ResponseEntity<String> p = post("/politicas",
            Map.of("nombre", "PolColab " + suffix, "descripcion", "test"), admin);
        assertThat(p.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        politicaIdTemp = mapper.readTree(p.getBody()).path("id").asText();

        ResponseEntity<String> d = post("/diagramas",
            Map.of("nombre", "DiagColab " + suffix, "politicaId", politicaIdTemp), admin);
        assertThat(d.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        diagramaIdTemp = mapper.readTree(d.getBody()).path("id").asText();

        // Obtener id del funcionario a invitar (func_atc)
        JsonNode usuarios = getJson("/usuarios?tipo=funcionario", admin);
        String funcAtcId = null;
        for (JsonNode u : usuarios) {
            if ("func_atc@cre.bo".equals(u.path("email").asText())) {
                funcAtcId = u.path("id").asText();
                break;
            }
        }
        assertThat(funcAtcId).as("funcionario ATC debe existir").isNotNull();

        // INVITAR
        ResponseEntity<String> invit = post("/colaboracion/diagrama/" + diagramaIdTemp + "/invitar",
            Map.of("usuarioInvitadoId", funcAtcId, "permisos", "editor"), admin);
        assertThat(invit.getStatusCode().is2xxSuccessful())
            .as("invitar -> %s body=%s", invit.getStatusCode(), invit.getBody())
            .isTrue();
        String colaboracionId = mapper.readTree(invit.getBody()).path("id").asText();
        assertThat(colaboracionId).isNotEmpty();

        // RESPONDER aceptar (como funcionario invitado)
        ResponseEntity<String> resp = post("/colaboracion/" + colaboracionId + "/responder",
            Map.of("decision", "ACEPTAR"), funcAtcHeaders());
        assertThat(resp.getStatusCode().is2xxSuccessful())
            .as("responder -> %s body=%s", resp.getStatusCode(), resp.getBody())
            .isTrue();
    }

    @Test
    @DisplayName("CU-15: Cliente NO puede invitar colaboradores (403)")
    void colaboracionClienteForbidden() {
        ResponseEntity<String> r = post("/colaboracion/diagrama/xxx/invitar",
            Map.of("usuarioInvitadoId", "y", "permisos", "editor"), clienteHeaders());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── CU-13: Formulario + Campos del nodo ────────────────────────────────

    @Test
    @DisplayName("CU-13: Admin crea formulario para un nodo y agrega/reordena campos")
    void formularioYCampos() throws Exception {
        var admin = adminHeaders();

        // Crear diagrama + nodo actividad para asociar el formulario
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        ResponseEntity<String> d = post("/diagramas",
            Map.of("nombre", "DiagForm " + suffix), admin);
        String diagId = mapper.readTree(d.getBody()).path("id").asText();

        String deptoId = primerDeptoId(admin);
        ResponseEntity<String> n = post("/diagramas/" + diagId + "/nodos",
            Map.of("nombre", "Actividad form", "tipo", "actividad",
                   "departamentoId", deptoId, "orden", 0), admin);
        String nodoId = mapper.readTree(n.getBody()).path("id").asText();

        // CREATE formulario
        ResponseEntity<String> fr = post("/nodos/" + nodoId + "/formulario",
            Map.of("nombre", "Form E2E", "permiteDictadoVoz", true), admin);
        assertThat(fr.getStatusCode())
            .as("crear formulario -> %s body=%s", fr.getStatusCode(), fr.getBody())
            .isEqualTo(HttpStatus.CREATED);
        formularioIdTemp = mapper.readTree(fr.getBody()).path("id").asText();

        // GET por nodo
        JsonNode f = getJson("/nodos/" + nodoId + "/formulario", admin);
        assertThat(f.path("id").asText()).isEqualTo(formularioIdTemp);

        // CREATE campo (requiere `nombre` técnico + `etiqueta` visible)
        Map<String, Object> campoBody = new HashMap<>();
        campoBody.put("nombre", "nombre_completo");
        campoBody.put("etiqueta", "Nombre completo");
        campoBody.put("tipo", "texto");
        campoBody.put("obligatorio", true);
        campoBody.put("orden", 1);
        ResponseEntity<String> cr = post("/formularios-plantilla/" + formularioIdTemp + "/campos",
            campoBody, admin);
        assertThat(cr.getStatusCode())
            .as("crear campo -> %s body=%s", cr.getStatusCode(), cr.getBody())
            .isEqualTo(HttpStatus.CREATED);
        campoIdTemp = mapper.readTree(cr.getBody()).path("id").asText();

        // Agregar 2do campo y reordenar
        Map<String, Object> campo2 = new HashMap<>();
        campo2.put("nombre", "ci");
        campo2.put("etiqueta", "CI");
        campo2.put("tipo", "texto");
        campo2.put("obligatorio", true);
        campo2.put("orden", 2);
        ResponseEntity<String> cr2 = post("/formularios-plantilla/" + formularioIdTemp + "/campos",
            campo2, admin);
        String campoId2 = mapper.readTree(cr2.getBody()).path("id").asText();

        // REORDENAR (invertir)
        ResponseEntity<String> reord = rest.exchange(
            BASE_URL + "/formularios-plantilla/" + formularioIdTemp + "/campos/reordenar",
            HttpMethod.PATCH,
            new org.springframework.http.HttpEntity<>(List.of(campoId2, campoIdTemp), admin),
            String.class);
        assertThat(reord.getStatusCode().is2xxSuccessful())
            .as("reordenar -> %s body=%s", reord.getStatusCode(), reord.getBody())
            .isTrue();

        // LISTAR
        JsonNode campos = getJson("/formularios-plantilla/" + formularioIdTemp + "/campos", admin);
        assertThat(campos.size()).isGreaterThanOrEqualTo(2);

        // UPDATE campo
        campoBody.put("etiqueta", "Nombre actualizado");
        ResponseEntity<String> upd = put("/campos-plantilla/" + campoIdTemp, campoBody, admin);
        assertThat(upd.getStatusCode().is2xxSuccessful()).isTrue();

        // DELETE campo
        ResponseEntity<String> del = delete("/campos-plantilla/" + campoId2, admin);
        assertThat(del.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ─── CU-10: Expediente desde la API ─────────────────────────────────────

    @Test
    @DisplayName("CU-10: Cliente/Funcionario pueden GET expediente de un trámite con expedienteId")
    void verExpediente() throws Exception {
        // Inicia un trámite como cliente y consulta su expediente
        String politicaId = primeraPoliticaActivaId();
        String tramiteId = iniciarTramiteComoCliente(politicaId);

        JsonNode exp = getJson("/expedientes/tramite/" + tramiteId, clienteHeaders());
        assertThat(exp.isObject()).isTrue();
        // El expediente debe tener al menos campo "secciones"
        assertThat(exp.has("secciones") || exp.has("expediente") || exp.has("tramiteId"))
            .as("expediente debe incluir secciones/expediente/tramiteId: %s", exp)
            .isTrue();

        // Cleanup
        post("/tramites/" + tramiteId + "/cancelar", Map.of(), clienteHeaders());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private String primerDeptoId(HttpHeaders h) throws Exception {
        JsonNode arr = getJson("/departamentos", h);
        for (JsonNode d : arr) {
            if ("ATC".equals(d.path("codigo").asText())) return d.path("id").asText();
        }
        return arr.get(0).path("id").asText();
    }

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
        return mapper.readTree(r.getBody()).path("id").asText();
    }

    @AfterAll
    static void limpiar() {
        try {
            var h = staticAdminHeaders();
            if (politicaIdTemp != null) {
                rest.exchange(BASE_URL + "/politicas/" + politicaIdTemp + "/estado",
                    HttpMethod.PATCH,
                    new org.springframework.http.HttpEntity<>(Map.of("estado", "archivada"), h),
                    String.class);
                rest.exchange(BASE_URL + "/politicas/" + politicaIdTemp,
                    HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(h), String.class);
            }
        } catch (Exception ignored) {}
    }

    private static HttpHeaders staticAdminHeaders() {
        var r = rest.postForEntity(BASE_URL + "/auth/login",
            Map.of("email", "admin@cre.bo", "password", "admin12345"), String.class);
        try {
            String token = mapper.readTree(r.getBody()).path("token").asText();
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(token);
            h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return h;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
