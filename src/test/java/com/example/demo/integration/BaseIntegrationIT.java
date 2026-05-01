package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base para los tests de integración HTTP. Pega contra el backend que YA
 * está corriendo en {@code http://localhost:8080} (gradle bootRun, prod, etc.)
 * — no levanta un contexto Spring nuevo, así evitamos conflictos con la
 * conexión Mongo del backend en vivo.
 *
 * Requisitos antes de correr la suite:
 *   1. Mongo accesible en localhost:27017 (Docker o servicio del sistema).
 *   2. Backend corriendo: {@code cd Backend && ./gradlew bootRun}.
 *   3. Seed automático completado (verificable con GET /api/health).
 */
public abstract class BaseIntegrationIT {

    protected static final String BASE_URL =
        System.getProperty("backend.url", "http://localhost:8080") + "/api";
    protected static final ObjectMapper mapper = new ObjectMapper();
    protected static final RestTemplate rest = buildRest();
    private static final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    private static RestTemplate buildRest() {
        // JdkClientHttpRequestFactory usa java.net.http.HttpClient, que SÍ soporta PATCH
        // (el HttpURLConnection legacy del default lanza ProtocolException con PATCH).
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory());
        // No lances excepción ante 4xx/5xx: los tests inspeccionan el status code.
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse r) throws IOException { return false; }
        });
        return rt;
    }

    @BeforeAll
    static void verificarBackendArriba() {
        ResponseEntity<String> health = rest.getForEntity(BASE_URL + "/health", String.class);
        assertThat(health.getStatusCode())
            .as("Backend debe estar corriendo en %s/health", BASE_URL)
            .isEqualTo(HttpStatus.OK);
    }

    protected String login(String email, String password) {
        return tokenCache.computeIfAbsent(email, e -> doLogin(e, password));
    }

    private String doLogin(String email, String password) {
        var body = Map.of("email", email, "password", password);
        var resp = rest.postForEntity(BASE_URL + "/auth/login", body, String.class);
        assertThat(resp.getStatusCode())
            .as("Login %s -> body=%s", email, resp.getBody())
            .isEqualTo(HttpStatus.OK);
        try {
            return mapper.readTree(resp.getBody()).path("token").asText();
        } catch (Exception ex) {
            throw new RuntimeException("Login parse: " + ex.getMessage(), ex);
        }
    }

    protected HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected HttpHeaders adminHeaders()   { return headers(login("admin@cre.bo",       "admin12345")); }
    protected HttpHeaders funcTecHeaders() { return headers(login("funcionario@cre.bo", "func12345")); }
    protected HttpHeaders funcAtcHeaders() { return headers(login("func_atc@cre.bo",    "func12345")); }
    protected HttpHeaders funcLegHeaders() { return headers(login("func_leg@cre.bo",    "func12345")); }
    protected HttpHeaders clienteHeaders() { return headers(login("cliente@cre.bo",     "cliente12345")); }

    protected JsonNode getJson(String path, HttpHeaders h) throws Exception {
        var resp = rest.exchange(BASE_URL + path, HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
            .as("GET %s -> %s body=%s", path, resp.getStatusCode(), resp.getBody())
            .isTrue();
        return mapper.readTree(resp.getBody());
    }

    protected ResponseEntity<String> post(String path, Object body, HttpHeaders h) {
        return rest.exchange(BASE_URL + path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    protected ResponseEntity<String> put(String path, Object body, HttpHeaders h) {
        return rest.exchange(BASE_URL + path, HttpMethod.PUT, new HttpEntity<>(body, h), String.class);
    }

    protected ResponseEntity<String> delete(String path, HttpHeaders h) {
        return rest.exchange(BASE_URL + path, HttpMethod.DELETE, new HttpEntity<>(h), String.class);
    }
}
