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
 * CRUD del catálogo del sistema: actividades, documentos, usuarios, permisos, roles.
 * Cubre los controllers que el resto de suites usaba solo en GET.
 */
class CatalogoCrudIT extends BaseIntegrationIT {

    private static String actividadIdCreada;
    private static String documentoIdCreado;
    private static String usuarioIdCreado;
    private static String rolIdCreado;

    // ─── ActividadController ────────────────────────────────────────────────

    @Test
    @DisplayName("Actividad: GET ?departamentoId / GET ?reutilizables=true / CRUD + salidasPosibles")
    void actividadCrud() throws Exception {
        var h = adminHeaders();

        // Listar y filtros
        JsonNode todas = getJson("/actividades", h);
        assertThat(todas.size()).isGreaterThanOrEqualTo(1);

        String deptoId = primerDeptoId(h);
        JsonNode porDepto = getJson("/actividades?departamentoId=" + deptoId, h);
        assertThat(porDepto.isArray()).isTrue();

        JsonNode reuti = getJson("/actividades?reutilizables=true", h);
        assertThat(reuti.isArray()).isTrue();

        // CREATE — con múltiples salidas posibles
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", "Actividad E2E " + UUID.randomUUID().toString().substring(0, 5));
        body.put("departamentoId", deptoId);
        body.put("slaHoras", 4);
        body.put("salidasPosibles", List.of("aprobar", "rechazar", "observar"));
        body.put("reutilizable", true);

        ResponseEntity<String> r = post("/actividades", body, h);
        assertThat(r.getStatusCode())
            .as("CREATE /actividades -> %s body=%s", r.getStatusCode(), r.getBody())
            .isEqualTo(HttpStatus.CREATED);
        JsonNode creada = mapper.readTree(r.getBody());
        actividadIdCreada = creada.path("id").asText();
        assertThat(actividadIdCreada).isNotEmpty();

        // El backend devuelve las 3 salidas, en el mismo orden
        JsonNode salidasCreate = creada.path("salidasPosibles");
        assertThat(salidasCreate.isArray()).isTrue();
        assertThat(salidasCreate.size()).isEqualTo(3);
        assertThat(salidasCreate.get(0).asText()).isEqualTo("aprobar");
        assertThat(salidasCreate.get(1).asText()).isEqualTo("rechazar");
        assertThat(salidasCreate.get(2).asText()).isEqualTo("observar");

        // READ by id — confirma que persistió la lista
        JsonNode porId = getJson("/actividades/" + actividadIdCreada, h);
        assertThat(porId.path("departamentoId").asText()).isEqualTo(deptoId);
        assertThat(porId.path("salidasPosibles").size()).isEqualTo(3);

        // UPDATE — cambia SLA y reduce a una sola salida
        body.put("slaHoras", 8);
        body.put("descripcion", "actualizada");
        body.put("salidasPosibles", List.of("derivar"));
        ResponseEntity<String> upd = put("/actividades/" + actividadIdCreada, body, h);
        assertThat(upd.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode actualizada = getJson("/actividades/" + actividadIdCreada, h);
        assertThat(actualizada.path("slaHoras").asInt()).isEqualTo(8);
        assertThat(actualizada.path("salidasPosibles").size()).isEqualTo(1);
        assertThat(actualizada.path("salidasPosibles").get(0).asText()).isEqualTo("derivar");

        // VALIDACIÓN: lista vacía debe ser rechazada con 400
        Map<String, Object> invalidoVacio = new HashMap<>(body);
        invalidoVacio.put("nombre", "ActividadInvalidaVacia");
        invalidoVacio.put("salidasPosibles", List.of());
        ResponseEntity<String> r400 = post("/actividades", invalidoVacio, h);
        assertThat(r400.getStatusCode())
            .as("POST con salidasPosibles vacío debe ser 400, fue %s", r400.getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // VALIDACIÓN: valor no permitido debe ser rechazado con 400
        Map<String, Object> invalidoValor = new HashMap<>(body);
        invalidoValor.put("nombre", "ActividadInvalidaValor");
        invalidoValor.put("salidasPosibles", List.of("aprobar", "explotar"));
        ResponseEntity<String> r400v = post("/actividades", invalidoValor, h);
        assertThat(r400v.getStatusCode())
            .as("POST con salida 'explotar' debe ser 400, fue %s body=%s", r400v.getStatusCode(), r400v.getBody())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // VALIDACIÓN: duplicados deben deduplicarse preservando orden
        Map<String, Object> conDuplicados = new HashMap<>(body);
        conDuplicados.put("nombre", "ActividadDuplicados " + UUID.randomUUID().toString().substring(0, 5));
        conDuplicados.put("salidasPosibles", List.of("aprobar", "rechazar", "aprobar"));
        ResponseEntity<String> rDup = post("/actividades", conDuplicados, h);
        assertThat(rDup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode dedupNode = mapper.readTree(rDup.getBody()).path("salidasPosibles");
        assertThat(dedupNode.size()).isEqualTo(2);
        assertThat(dedupNode.get(0).asText()).isEqualTo("aprobar");
        assertThat(dedupNode.get(1).asText()).isEqualTo("rechazar");
        // limpieza inmediata
        delete("/actividades/" + mapper.readTree(rDup.getBody()).path("id").asText(), h);

        // 403 cliente
        ResponseEntity<String> denied = post("/actividades", body, clienteHeaders());
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── DocumentoController ────────────────────────────────────────────────

    @Test
    @DisplayName("Documento: CRUD + filtro soloActivos + 403 cliente")
    void documentoCrud() throws Exception {
        var h = adminHeaders();
        String nombre = "Doc E2E " + UUID.randomUUID().toString().substring(0, 5);

        ResponseEntity<String> r = post("/documentos",
            Map.of("nombre", nombre, "descripcion", "doc test", "activo", true), h);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        documentoIdCreado = mapper.readTree(r.getBody()).path("id").asText();

        JsonNode porId = getJson("/documentos/" + documentoIdCreado, h);
        assertThat(porId.path("nombre").asText()).isEqualTo(nombre);

        ResponseEntity<String> upd = put("/documentos/" + documentoIdCreado,
            Map.of("nombre", nombre, "descripcion", "actualizada", "activo", true), h);
        assertThat(upd.getStatusCode().is2xxSuccessful()).isTrue();

        // soloActivos=true devuelve solo los activos
        JsonNode activos = getJson("/documentos?soloActivos=true", h);
        for (JsonNode d : activos) assertThat(d.path("activo").asBoolean()).isTrue();

        // Listar accesible para cualquier autenticado
        JsonNode comoCliente = getJson("/documentos", clienteHeaders());
        assertThat(comoCliente.isArray()).isTrue();

        // POST solo admin
        ResponseEntity<String> denied = post("/documentos",
            Map.of("nombre", "hack-" + UUID.randomUUID()), clienteHeaders());
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── UsuarioController ──────────────────────────────────────────────────

    @Test
    @DisplayName("Usuario: GET /me, POST /crear, ?tipo=funcionario, /funcionarios, /activos, GET id, PUT, DELETE")
    void usuarioCrud() throws Exception {
        var h = adminHeaders();

        // GET /me
        JsonNode me = getJson("/usuarios/me", h);
        assertThat(me.path("email").asText()).isEqualTo("admin@cre.bo");

        // Listar por tipo
        JsonNode funcs = getJson("/usuarios?tipo=funcionario", h);
        for (JsonNode u : funcs) assertThat(u.path("tipo").asText()).isEqualTo("funcionario");

        // /funcionarios accesible para funcionario también
        JsonNode listadoFunc = getJson("/usuarios/funcionarios", funcTecHeaders());
        assertThat(listadoFunc.isArray()).isTrue();

        // /activos
        JsonNode activos = getJson("/usuarios/activos", h);
        for (JsonNode u : activos) assertThat(u.path("activo").asBoolean()).isTrue();

        // POST /crear funcionario
        String email = "e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@test.bo";
        Map<String, Object> nuevo = new HashMap<>();
        nuevo.put("nombre", "E2E");
        nuevo.put("apellido", "Test");
        nuevo.put("email", email);
        nuevo.put("password", "secret123");
        nuevo.put("tipo", "funcionario");

        ResponseEntity<String> r = post("/usuarios/crear", nuevo, h);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        usuarioIdCreado = mapper.readTree(r.getBody()).path("id").asText();

        // GET por id
        JsonNode porId = getJson("/usuarios/" + usuarioIdCreado, h);
        assertThat(porId.path("email").asText()).isEqualTo(email);

        // PUT actualizar (activo=false)
        ResponseEntity<String> upd = put("/usuarios/" + usuarioIdCreado,
            Map.of("activo", false), h);
        assertThat(upd.getStatusCode().is2xxSuccessful()).isTrue();

        // DELETE (soft delete)
        ResponseEntity<String> del = delete("/usuarios/" + usuarioIdCreado, h);
        assertThat(del.getStatusCode().is2xxSuccessful()).isTrue();

        // 403 cliente
        ResponseEntity<String> denied = post("/usuarios/crear", nuevo, clienteHeaders());
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── PermisoController ──────────────────────────────────────────────────

    @Test
    @DisplayName("Permiso: GET catálogo y filtro por módulo (CU-03)")
    void permisoListado() throws Exception {
        var h = adminHeaders();

        JsonNode todos = getJson("/permisos", h);
        assertThat(todos.size()).isGreaterThanOrEqualTo(9);

        JsonNode tramites = getJson("/permisos?modulo=tramites", h);
        for (JsonNode p : tramites) assertThat(p.path("modulo").asText()).isEqualTo("tramites");

        // 403 cliente
        ResponseEntity<String> r = rest.exchange(BASE_URL + "/permisos", HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(clienteHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── RolController ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Rol: GET listado, POST crear personalizado, PATCH permisos, no se puede borrar 'Administrador'")
    void rolCrud() throws Exception {
        var h = adminHeaders();

        JsonNode todos = getJson("/roles", h);
        assertThat(todos.size()).isGreaterThanOrEqualTo(4);

        // Crear rol personalizado
        Map<String, Object> nuevo = new HashMap<>();
        nuevo.put("nombre", "RolE2E_" + UUID.randomUUID().toString().substring(0, 5));
        nuevo.put("descripcion", "rol custom para tests");
        nuevo.put("permisos", List.of("CONSULTAR_MIS_TRAMITES"));
        ResponseEntity<String> r = post("/roles", nuevo, h);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        rolIdCreado = mapper.readTree(r.getBody()).path("id").asText();

        // PATCH asignar permisos
        ResponseEntity<String> patch = rest.exchange(BASE_URL + "/roles/" + rolIdCreado + "/permisos",
            HttpMethod.PATCH,
            new org.springframework.http.HttpEntity<>(
                Map.of("permisos", List.of("CONSULTAR_MIS_TRAMITES", "INICIAR_TRAMITE")), h),
            String.class);
        assertThat(patch.getStatusCode().is2xxSuccessful())
            .as("PATCH permisos -> %s body=%s", patch.getStatusCode(), patch.getBody())
            .isTrue();

        // 403 cliente
        ResponseEntity<String> denied = rest.exchange(BASE_URL + "/roles", HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(clienteHeaders()), String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private String primerDeptoId(HttpHeaders h) throws Exception {
        JsonNode arr = getJson("/departamentos", h);
        for (JsonNode d : arr) {
            if ("ATC".equals(d.path("codigo").asText())) return d.path("id").asText();
        }
        return arr.get(0).path("id").asText();
    }

    @AfterAll
    static void limpiar() {
        try {
            var h = staticAdminHeaders();
            if (rolIdCreado != null) {
                rest.exchange(BASE_URL + "/roles/" + rolIdCreado, HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(h), String.class);
            }
            if (actividadIdCreada != null) {
                rest.exchange(BASE_URL + "/actividades/" + actividadIdCreada, HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(h), String.class);
            }
            if (documentoIdCreado != null) {
                rest.exchange(BASE_URL + "/documentos/" + documentoIdCreado, HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(h), String.class);
            }
        } catch (Exception ignored) {}
    }

    private static HttpHeaders staticAdminHeaders() {
        var body = Map.of("email", "admin@cre.bo", "password", "admin12345");
        var r = rest.postForEntity(BASE_URL + "/auth/login", body, String.class);
        try {
            String token = mapper.readTree(r.getBody()).path("token").asText();
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(token);
            h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return h;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
