package com.example.demo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** POST /api/auth/login — credenciales seedeadas. */
class AuthHttpIT extends BaseIntegrationIT {

    @Test
    @DisplayName("login admin@cre.bo → 200 con token JWT y rol Administrador")
    void loginAdmin() throws Exception {
        ResponseEntity<String> r = post("/auth/login",
            Map.of("email", "admin@cre.bo", "password", "admin12345"),
            new org.springframework.http.HttpHeaders());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(r.getBody());
        assertThat(body.path("token").asText()).isNotEmpty();
        assertThat(body.path("rol").asText()).isEqualTo("Administrador");
        assertThat(body.path("email").asText()).isEqualTo("admin@cre.bo");
    }

    @Test
    @DisplayName("login funcionario@cre.bo → rol Funcionario")
    void loginFunc() throws Exception {
        ResponseEntity<String> r = post("/auth/login",
            Map.of("email", "funcionario@cre.bo", "password", "func12345"),
            new org.springframework.http.HttpHeaders());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(r.getBody()).path("rol").asText()).isEqualTo("Funcionario");
    }

    @Test
    @DisplayName("login cliente@cre.bo → rol Cliente")
    void loginCliente() throws Exception {
        ResponseEntity<String> r = post("/auth/login",
            Map.of("email", "cliente@cre.bo", "password", "cliente12345"),
            new org.springframework.http.HttpHeaders());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(r.getBody()).path("rol").asText()).isEqualTo("Cliente");
    }

    @Test
    @DisplayName("login con password incorrecta → 4xx")
    void loginPasswordIncorrecta() {
        ResponseEntity<String> r = post("/auth/login",
            Map.of("email", "admin@cre.bo", "password", "WRONG"),
            new org.springframework.http.HttpHeaders());

        assertThat(r.getStatusCode().is4xxClientError()).as("body=%s", r.getBody()).isTrue();
    }

    @Test
    @DisplayName("login con email no registrado → 4xx")
    void loginEmailInexistente() {
        ResponseEntity<String> r = post("/auth/login",
            Map.of("email", "ghost@nope.bo", "password", "x"),
            new org.springframework.http.HttpHeaders());

        assertThat(r.getStatusCode().is4xxClientError()).isTrue();
    }
}
