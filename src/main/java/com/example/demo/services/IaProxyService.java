package com.example.demo.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Proxy hacia el microservicio Python/FastAPI.
 *
 * Si el microservicio cae o tarda más del timeout configurado, este servicio
 * lanza {@link ResponseStatusException} con código {@code 503 IA_NO_DISPONIBLE},
 * de modo que los controllers degradan limpiamente.
 *
 * Todos los métodos devuelven {@code Map<String,Object>} para no acoplarnos al
 * schema interno del microservicio — los services consumidores extraen los
 * campos que les interesan.
 */
@Service
@Slf4j
public class IaProxyService {

    @Autowired
    @Qualifier("iaServiceRestClient")
    private RestClient ia;

    // ── CU-39 · voz → formulario ─────────────────────────────────────────────

    public Map<String, Object> vozAFormulario(MultipartFile audio, String schemaCamposJson) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            byte[] bytes = audio.getBytes();
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return audio.getOriginalFilename() != null
                            ? audio.getOriginalFilename() : "audio.wav";
                }
            };
            body.add("audio", resource);
            body.add("schema_campos", schemaCamposJson != null ? schemaCamposJson : "[]");
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el audio: " + e.getMessage(), e);
        }

        return postMultipart("/nlp/voz-a-formulario", body);
    }

    // ── CU-40 · sugerir política ─────────────────────────────────────────────

    public Map<String, Object> sugerirPolitica(String descripcion,
                                                List<Map<String, Object>> politicasActivas) {
        Map<String, Object> req = Map.of(
                "descripcion", descripcion,
                "politicas_activas", politicasActivas
        );
        return postJson("/asignacion/politica", req);
    }

    // ── CU-41 · reportes naturales ───────────────────────────────────────────

    public Map<String, Object> consultaNatural(String consulta) {
        return postJson("/reportes/consulta-natural", Map.of("consulta", consulta));
    }

    // ── CU-46 · clasificar intención del asistente (modelo TensorFlow propio) ──

    public Map<String, Object> clasificarIntencion(String consulta) {
        return postJson("/nlp/clasificar-intencion",
                Map.of("consulta", consulta != null ? consulta : ""));
    }

    // ── CU-42 · ruta óptima ──────────────────────────────────────────────────

    public Map<String, Object> rutaOptima(String tramiteId, String politicaId,
                                           Map<String, Object> contexto,
                                           List<Map<String, Object>> nodosPolitica) {
        Map<String, Object> req = Map.of(
                "tramite_id", tramiteId,
                "politica_id", politicaId,
                "contexto", contexto != null ? contexto : Map.of(),
                "nodos_politica", nodosPolitica != null ? nodosPolitica : List.of()
        );
        return postJson("/enrutamiento/ruta-optima", req);
    }

    // ── CU-43 · riesgo de demora (batch) ─────────────────────────────────────

    public List<Map<String, Object>> riesgoDemora(List<Map<String, Object>> features) {
        return postJsonList("/enrutamiento/riesgo-demora",
                Map.of("tramites", features != null ? features : List.of()));
    }

    // ── CU-44 · prioridades ──────────────────────────────────────────────────

    public List<Map<String, Object>> prioridades(String funcionarioId,
                                                  List<Map<String, Object>> tramites) {
        Map<String, Object> req = Map.of(
                "funcionario_id", funcionarioId,
                "tramites", tramites != null ? tramites : List.of()
        );
        return postJsonList("/enrutamiento/prioridades", req);
    }

    // ── CU-45 · anomalías ────────────────────────────────────────────────────

    public List<Map<String, Object>> anomalias(List<Map<String, Object>> secuencias) {
        return postJsonList("/enrutamiento/anomalias",
                Map.of("secuencias", secuencias != null ? secuencias : List.of()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(String path, Object body) {
        try {
            Map<String, Object> resp = ia.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return resp != null ? resp : Map.of();
        } catch (ResourceAccessException ex) {
            log.warn("[IA] microservicio no disponible en {}: {}", path, ex.getMessage());
            throw iaNoDisponible(ex);
        } catch (HttpServerErrorException ex) {
            log.warn("[IA] 5xx en {}: {}", path, ex.getMessage());
            throw iaNoDisponible(ex);
        } catch (RestClientException ex) {
            log.warn("[IA] error de cliente en {}: {}", path, ex.getMessage());
            throw iaNoDisponible(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> postJsonList(String path, Object body) {
        try {
            List<Map<String, Object>> resp = ia.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return resp != null ? resp : List.of();
        } catch (RestClientException ex) {
            log.warn("[IA] error en {}: {}", path, ex.getMessage());
            throw iaNoDisponible(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postMultipart(String path, MultiValueMap<String, Object> body) {
        try {
            Map<String, Object> resp = ia.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return resp != null ? resp : Map.of();
        } catch (RestClientException ex) {
            log.warn("[IA] error multipart en {}: {}", path, ex.getMessage());
            throw iaNoDisponible(ex);
        }
    }

    private ResponseStatusException iaNoDisponible(Throwable causa) {
        return new ResponseStatusException(
                HttpStatusCode.valueOf(503),
                "IA_NO_DISPONIBLE: " + causa.getClass().getSimpleName() + " - " + causa.getMessage(),
                causa);
    }
}
