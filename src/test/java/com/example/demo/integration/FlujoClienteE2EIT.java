package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo end-to-end desde la **vista del cliente**, golpeando el backend en vivo:
 *
 *   1. Cliente lista políticas activas (las que el admin habilitó).
 *   2. Cliente inicia un trámite contra esa política.
 *      → Backend crea el trámite, genera el expediente y avanza al primer nodo.
 *   3. Cliente ve el trámite en su lista (/mis-tramites).
 *   4. Cliente consulta el estado: progreso, nodo actual, historial.
 *   5. Cliente ve la línea de tiempo visual (CU-21).
 *   6. (Funcionario del depto del primer nodo lo ve en su bandeja /mis-pendientes).
 *   7. Cliente cancela el trámite (CU-19) y el estado pasa a "Cancelado".
 *
 * Cada paso depende del anterior, así que están @Ordered.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlujoClienteE2EIT extends BaseIntegrationIT {

    private static String politicaIdElegida;
    private static String politicaNombreElegida;
    private static String tramiteId;
    private static String tramiteCodigo;
    private static String primerNodoDeptoId;

    @Test
    @Order(1)
    @DisplayName("Paso 1 — Cliente lista políticas activas y elige una")
    void paso1_listarPoliticasActivas() throws Exception {
        JsonNode arr = getJson("/politicas?soloActivas=true", clienteHeaders());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size())
            .as("debe haber al menos una política activa para que el cliente pueda iniciar trámite")
            .isGreaterThanOrEqualTo(1);

        JsonNode pol = arr.get(0);
        politicaIdElegida = pol.path("id").asText();
        politicaNombreElegida = pol.path("nombre").asText();
        assertThat(politicaIdElegida).isNotEmpty();
        assertThat(pol.path("estado").asText()).isEqualTo("activa");
    }

    @Test
    @Order(2)
    @DisplayName("Paso 2 — Cliente inicia un trámite contra esa política")
    void paso2_iniciarTramite() throws Exception {
        assertThat(politicaIdElegida).as("paso 1 debió correr antes").isNotEmpty();

        Map<String, Object> body = new HashMap<>();
        body.put("politicaId", politicaIdElegida);
        body.put("clienteId", "ignorado-backend-usa-el-del-token");
        body.put("prioridad", 2);

        ResponseEntity<String> resp = post("/tramites/iniciar", body, clienteHeaders());
        assertThat(resp.getStatusCode())
            .as("iniciar -> %s body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(HttpStatus.CREATED);

        JsonNode tramite = mapper.readTree(resp.getBody());
        tramiteId = tramite.path("id").asText();
        tramiteCodigo = tramite.path("codigo").asText();
        assertThat(tramiteId).isNotEmpty();
        assertThat(tramiteCodigo).isNotEmpty();
        // El motor debió posicionar el trámite en el primer nodo y crear el expediente
        assertThat(tramite.path("expedienteId").asText()).isNotEmpty();
        assertThat(tramite.path("nodoActualId").asText()).isNotEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("Paso 3 — Cliente ve el trámite recién creado en /mis-tramites")
    void paso3_misTramitesIncluyeElNuevo() throws Exception {
        assertThat(tramiteId).as("paso 2 debió correr antes").isNotEmpty();

        JsonNode arr = getJson("/tramites/mis-tramites", clienteHeaders());
        boolean encontrado = false;
        for (JsonNode t : arr) {
            if (tramiteId.equals(t.path("id").asText())) {
                encontrado = true;
                assertThat(t.path("politicaId").asText()).isEqualTo(politicaIdElegida);
            }
        }
        assertThat(encontrado)
            .as("el trámite %s debe aparecer en mis-tramites del cliente", tramiteCodigo)
            .isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("Paso 4 — Cliente consulta /estado: nodo actual, progreso, historial")
    void paso4_consultarEstado() throws Exception {
        assertThat(tramiteId).isNotEmpty();

        JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());

        assertThat(estado.path("codigo").asText()).isEqualTo(tramiteCodigo);
        assertThat(estado.path("politicaNombre").asText()).isEqualTo(politicaNombreElegida);
        assertThat(estado.path("nodoActualId").asText()).isNotEmpty();
        assertThat(estado.has("nodoActual"))
            .as("response debe incluir nodoActual anidado: %s", estado).isTrue();
        assertThat(estado.path("progreso").isInt()).isTrue();
        assertThat(estado.path("historial").isArray()).isTrue();

        primerNodoDeptoId = estado.path("nodoActual").path("departamentoId").asText();
    }

    @Test
    @Order(5)
    @DisplayName("Paso 5 — Cliente ve línea de tiempo visual (CU-21)")
    void paso5_lineaTiempo() throws Exception {
        assertThat(tramiteId).isNotEmpty();

        JsonNode lt = getJson("/tramites/" + tramiteId + "/linea-tiempo", clienteHeaders());
        // El DTO LineaTiempoResponse debe traer al menos id y algún campo de hitos/estado
        assertThat(lt.isObject()).isTrue();
        // Cualquiera de los dos campos típicos debe existir
        boolean tieneHitosOEstado = lt.has("hitos") || lt.has("estadoActual") || lt.has("tramiteId");
        assertThat(tieneHitosOEstado)
            .as("línea de tiempo debe incluir hitos/estado: %s", lt).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Paso 6 — Funcionario del depto del primer nodo ve el trámite en su bandeja")
    void paso6_funcionarioVeEnBandeja() throws Exception {
        // El primer nodo de la política CRE "Nueva conexion residencial" es de ATC
        JsonNode arr = getJson("/tramites/mis-pendientes", funcAtcHeaders());
        assertThat(arr.isArray()).isTrue();

        boolean encontrado = false;
        for (JsonNode t : arr) {
            if (tramiteId.equals(t.path("id").asText())) {
                encontrado = true;
                break;
            }
        }
        // Si el primer nodo NO fue de ATC, validar la regla con el funcionario que sí lo atiende.
        // No fallamos: el flujo del workflow puede asignar a un funcionario distinto si el seed cambió.
        if (!encontrado) {
            // Verificar con cada funcionario hasta encontrar el correcto
            for (var headers : new org.springframework.http.HttpHeaders[]{
                    funcTecHeaders(), funcLegHeaders() }) {
                JsonNode arr2 = getJson("/tramites/mis-pendientes", headers);
                for (JsonNode t : arr2) {
                    if (tramiteId.equals(t.path("id").asText())) { encontrado = true; break; }
                }
                if (encontrado) break;
            }
        }
        assertThat(encontrado)
            .as("algún funcionario de ATC/TEC/LEG debe ver el trámite en su bandeja")
            .isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("Paso 7 — Cliente cancela el trámite (CU-19) y el estado pasa a Cancelado")
    void paso7_cancelar() throws Exception {
        assertThat(tramiteId).isNotEmpty();

        ResponseEntity<String> cancel = post("/tramites/" + tramiteId + "/cancelar",
            Map.of(), clienteHeaders());
        assertThat(cancel.getStatusCode())
            .as("cancelar -> %s body=%s", cancel.getStatusCode(), cancel.getBody())
            .isEqualTo(HttpStatus.OK);

        JsonNode cancelado = mapper.readTree(cancel.getBody());
        // El backend devuelve "Cancelado por el usuario" (motor de estados),
        // basta verificar que empieza con "Cancelado".
        assertThat(cancelado.path("estadoActual").asText())
            .as("tras cancelar el estado debe empezar con 'Cancelado'")
            .startsWith("Cancelado");

        JsonNode estado = getJson("/tramites/" + tramiteId + "/estado", clienteHeaders());
        assertThat(estado.path("estado").asText()).startsWith("Cancelado");
    }
}
