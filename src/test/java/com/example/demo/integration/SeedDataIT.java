package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la base que sirve el backend está sembrada correctamente
 * con los datos del enunciado (departamentos CRE, roles, políticas, etc.).
 *
 * Usa HTTP en vez de acceso directo a Mongo: así medimos lo MISMO que
 * cualquier cliente real (web Angular, mobile Flutter) verá.
 */
class SeedDataIT extends BaseIntegrationIT {

    @Test
    @DisplayName("/api/health: backend UP y conteos > 0 en colecciones core")
    void health() throws Exception {
        JsonNode h = getJson("/health", new org.springframework.http.HttpHeaders());
        assertThat(h.path("status").asText()).isEqualTo("UP");
        JsonNode c = h.path("colecciones");
        assertThat(c.path("usuarios").asInt()).isGreaterThanOrEqualTo(9);
        assertThat(c.path("roles").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(c.path("departamentos").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(c.path("politicas_negocio").asInt()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("/api/usuarios: cuentas demo del seed visibles para admin")
    void usuarios() throws Exception {
        JsonNode arr = getJson("/usuarios", adminHeaders());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(9);

        boolean tieneAdmin = false, tieneFunc = false, tieneCliente = false;
        for (JsonNode u : arr) {
            String email = u.path("email").asText();
            if ("admin@cre.bo".equals(email))       tieneAdmin = true;
            if ("funcionario@cre.bo".equals(email)) tieneFunc = true;
            if ("cliente@cre.bo".equals(email))     tieneCliente = true;
        }
        assertThat(tieneAdmin).as("admin@cre.bo").isTrue();
        assertThat(tieneFunc).as("funcionario@cre.bo").isTrue();
        assertThat(tieneCliente).as("cliente@cre.bo").isTrue();
    }

    @Test
    @DisplayName("/api/departamentos: ATC, TEC, LEG, OPE están sembrados")
    void departamentos() throws Exception {
        JsonNode arr = getJson("/departamentos", adminHeaders());
        assertThat(arr.isArray()).isTrue();
        long codigos = 0;
        for (JsonNode d : arr) {
            String cod = d.path("codigo").asText();
            if (cod.equals("ATC") || cod.equals("TEC") || cod.equals("LEG") || cod.equals("OPE")) codigos++;
        }
        assertThat(codigos).as("códigos ATC/TEC/LEG/OPE presentes").isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("/api/politicas: 'Nueva conexion residencial' aparece activa")
    void politicas() throws Exception {
        JsonNode arr = getJson("/politicas", adminHeaders());
        boolean encontrada = false;
        for (JsonNode p : arr) {
            if ("Nueva conexion residencial".equals(p.path("nombre").asText())) {
                encontrada = true;
                assertThat(p.path("estado").asText()).isEqualTo("activa");
            }
        }
        assertThat(encontrada).as("política demo presente").isTrue();
    }

    @Test
    @DisplayName("/api/actividades: catálogo con al menos 7 actividades")
    void actividades() throws Exception {
        JsonNode arr = getJson("/actividades", adminHeaders());
        assertThat(arr.size()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("/api/roles: 4 roles del sistema (Cliente, Funcionario, Admin, SuperUser)")
    void roles() throws Exception {
        JsonNode arr = getJson("/roles", adminHeaders());
        long sistema = 0;
        for (JsonNode r : arr) {
            String nombre = r.path("nombre").asText();
            if (nombre.equals("Cliente") || nombre.equals("Funcionario")
                || nombre.equals("Administrador") || nombre.equals("SuperUser")) sistema++;
        }
        assertThat(sistema).isGreaterThanOrEqualTo(4);
    }
}
