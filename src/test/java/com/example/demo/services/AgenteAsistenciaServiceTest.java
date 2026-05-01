package com.example.demo.services;

import com.example.demo.dto.AgenteRequest;
import com.example.demo.dto.AgenteResponse;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.TramiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de la KB local del agente CU-31. No carga Spring context ni
 * requiere MongoDB — sólo Mockito para los repos.
 */
@ExtendWith(MockitoExtension.class)
class AgenteAsistenciaServiceTest {

    @Mock
    private PoliticaNegocioRepository politicaRepo;

    @Mock
    private TramiteRepository tramiteRepo;

    @InjectMocks
    private AgenteAsistenciaService svc;

    private AgenteRequest req;

    @BeforeEach
    void setUp() {
        req = new AgenteRequest();
        req.setConsulta("hola");
        req.setModuloActivo("/admin/diagramas");
    }

    @Test
    @DisplayName("Saludo: detecta módulo de diagramas y propone acción contextual para admin")
    void saludoEnDiagramas_proponeAccionAdmin() {
        AgenteResponse r = svc.responder(req, "ROLE_ADMINISTRADOR");

        assertThat(r.getRespuesta()).containsIgnoringCase("diseño de flujos");
        assertThat(r.getFuente()).isEqualTo("kb-local");
        assertThat(r.getAccion()).isNotNull();
        assertThat(r.getAccion().getRuta()).isEqualTo("/admin/dashboard");
        assertThat(r.getAccion().getTipo()).isEqualTo("navegar");
    }

    @Test
    @DisplayName("Saludo: rol funcionario sugiere bandeja de entrada")
    void saludoComoFuncionario_sugiereBandeja() {
        req.setConsulta("");
        req.setModuloActivo("/funcionario/bandeja-entrada");
        AgenteResponse r = svc.responder(req, "ROLE_FUNCIONARIO");

        assertThat(r.getAccion().getRuta()).isEqualTo("/funcionario/bandeja-entrada");
    }

    @Test
    @DisplayName("Consulta de estado: usa el trámite REAL del repositorio")
    void consultaEstado_devuelveDatosDelTramite() {
        Tramite t = new Tramite();
        t.setId("t1");
        t.setCodigo("TR-2026-001");
        t.setEstadoActual("En proceso");
        t.setNodoActualId("n1");
        when(tramiteRepo.findById("t1")).thenReturn(Optional.of(t));

        req.setConsulta("¿cuál es el estado de mi tramite?");
        req.setTramiteIdOpcional("t1");
        AgenteResponse r = svc.responder(req, "ROLE_CLIENTE");

        assertThat(r.getRespuesta()).contains("TR-2026-001").contains("En proceso");
        assertThat(r.getAccion().getRuta()).isEqualTo("/cliente/tramites/t1/timeline");
    }

    @Test
    @DisplayName("Pregunta por políticas: lista las activas del repositorio")
    void preguntaPorPoliticas_listaActivas() {
        PoliticaNegocio p1 = new PoliticaNegocio();
        p1.setNombre("Nueva conexión residencial");
        PoliticaNegocio p2 = new PoliticaNegocio();
        p2.setNombre("Cambio de medidor");
        when(politicaRepo.findByEstado("ACTIVA")).thenReturn(List.of(p1, p2));

        req.setConsulta("¿qué políticas hay?");
        AgenteResponse r = svc.responder(req, "ROLE_ADMINISTRADOR");

        assertThat(r.getRespuesta())
            .contains("Nueva conexión residencial")
            .contains("Cambio de medidor");
        assertThat(r.getAccion().getRuta()).isEqualTo("/admin/diagramas");
    }

    @Test
    @DisplayName("Consulta sobre nodos: explica fork/join/decision al admin")
    void consultaNodos_explicaSemantica() {
        req.setConsulta("¿cómo funciona el fork?");
        AgenteResponse r = svc.responder(req, "ROLE_ADMINISTRADOR");

        assertThat(r.getRespuesta()).containsIgnoringCase("fork");
        assertThat(r.getAccion()).isNotNull();
    }

    @Test
    @DisplayName("Consulta fuera de alcance: responde respuesta canónica sin acción")
    void consultaFueraDeAlcance_respondeGenerico() {
        req.setConsulta("¿cuál es el clima hoy?");
        AgenteResponse r = svc.responder(req, "ROLE_CLIENTE");

        assertThat(r.getRespuesta()).containsIgnoringCase("Solo puedo asistirte");
        assertThat(r.getAccion()).isNull();
    }
}
