package com.example.demo.config.seeders;

import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TramiteSeeder {

    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;

    public void seed() {
        String politicaId = politicaRepository.findAll().stream()
                .filter(p -> "Nueva conexion residencial".equals(p.getNombre()))
                .findFirst().map(p -> p.getId()).orElse(null);

        List<NodoDiagrama> nodos = nodoRepository.findAll();
        String nAtcVer    = nodoId(nodos, "actividad", "Verificar Documentos");
        String nTecInsp   = nodoId(nodos, "actividad", "Inspeccion en Campo");
        String nTecPres   = nodoId(nodos, "actividad", "Elaborar Presupuesto");
        String nLegContr  = nodoId(nodos, "actividad", "Revisar Contrato");
        String nOpeCierre = nodoId(nodos, "actividad", "Cierre y Conexion");
        String nDecision  = nodoId(nodos, "decision",  null);
        String nFin       = nodoId(nodos, "fin",       null);

        String clienteId  = userId("cliente@cre.bo");
        String cliente2Id = userId("cliente2@cre.bo");
        String cliente3Id = userId("cliente3@cre.bo");
        String funcTecId  = userId("funcionario@cre.bo");
        String funcAtcId  = userId("func_atc@cre.bo");
        String funcLegId  = userId("func_leg@cre.bo");
        String funcOpeId  = userId("func_ope@cre.bo");

        LocalDateTime now = LocalDateTime.now();

        // ─────────── cliente@cre.bo (8 tramites, todas las etapas) ───────────

        // TRM-2024-001: COMPLETADO — aprobado e instalado (cerrado hace 5 dias)
        crear("TRM-2024-001", clienteId, politicaId, "Completado",
                nFin, funcOpeId, 2,
                now.minusDays(30), now.minusDays(5), now.minusDays(5),
                new ArrayList<>());

        // TRM-2024-002: RECHAZADO — contrato rechazado en decision (hace 10 dias)
        crear("TRM-2024-002", clienteId, politicaId, "Rechazado",
                nDecision, funcLegId, 3,
                now.minusDays(25), now.minusDays(18), now.minusDays(10),
                new ArrayList<>());

        // TRM-2024-008: INICIADO — recien creado, en ATC verificando documentos
        crear("TRM-2024-008", clienteId, politicaId, "Iniciado",
                nAtcVer, funcAtcId, 1,
                now.minusHours(6), now.plusDays(10), null,
                new ArrayList<>());

        // TRM-2024-009: EN PROCESO — TEC paralelo (inspeccion + presupuesto)
        crear("TRM-2024-009", clienteId, politicaId, "En proceso",
                nTecInsp, funcTecId, 2,
                now.minusDays(4), now.plusDays(6), null,
                List.of(nTecInsp, nTecPres));

        // TRM-2024-010: EN PROCESO — listo para que LEG apruebe (post-join)
        crear("TRM-2024-010", clienteId, politicaId, "En proceso",
                nLegContr, funcLegId, 2,
                now.minusDays(9), now.plusDays(5), null,
                new ArrayList<>());

        // TRM-2024-011: OBSERVADO — observaciones en revision de contrato
        crear("TRM-2024-011", clienteId, politicaId, "Observado",
                nLegContr, funcLegId, 3,
                now.minusDays(14), now.minusDays(2), null,
                new ArrayList<>());

        // TRM-2024-012: EN PROCESO — aprobado en decision, en OPE ejecutando cierre
        crear("TRM-2024-012", clienteId, politicaId, "En proceso",
                nOpeCierre, funcOpeId, 2,
                now.minusDays(20), now.plusDays(2), null,
                new ArrayList<>());

        // TRM-2024-013: CANCELADO — cancelado por el cliente en etapa inicial
        crear("TRM-2024-013", clienteId, politicaId, "Cancelado",
                nAtcVer, null, 1,
                now.minusDays(17), now.minusDays(15), now.minusDays(15),
                new ArrayList<>());

        // ─────────── cliente2@cre.bo (3 tramites) ───────────

        // TRM-2024-003: EN PROCESO — en TEC inspeccion (paralelo)
        crear("TRM-2024-003", cliente2Id, politicaId, "En proceso",
                nTecInsp, funcTecId, 2,
                now.minusDays(10), now.plusDays(5), null,
                List.of(nTecInsp, nTecPres));

        // TRM-2024-005: OBSERVADO — con observaciones en revision de contrato
        crear("TRM-2024-005", cliente2Id, politicaId, "Observado",
                nLegContr, funcLegId, 3,
                now.minusDays(15), now.minusDays(5), null,
                new ArrayList<>());

        // TRM-2024-007: EN PROCESO — listo para que LEG apruebe
        crear("TRM-2024-007", cliente2Id, politicaId, "En proceso",
                nLegContr, funcLegId, 2,
                now.minusDays(12), now.plusDays(8), null,
                new ArrayList<>());

        // ─────────── cliente3@cre.bo (3 tramites) ───────────

        // TRM-2024-004: INICIADO — recien iniciado
        crear("TRM-2024-004", cliente3Id, politicaId, "Iniciado",
                nAtcVer, funcAtcId, 1,
                now.minusDays(1), now.plusDays(10), null,
                new ArrayList<>());

        // TRM-2024-006: CANCELADO — cancelado por el cliente
        crear("TRM-2024-006", cliente3Id, politicaId, "Cancelado",
                nAtcVer, null, 1,
                now.minusDays(20), now.minusDays(18), now.minusDays(18),
                new ArrayList<>());

        // TRM-2024-014: COMPLETADO — instalado hace 2 meses
        crear("TRM-2024-014", cliente3Id, politicaId, "Completado",
                nFin, funcOpeId, 2,
                now.minusDays(60), now.minusDays(35), now.minusDays(35),
                new ArrayList<>());

        log.info("[Seeder] Tramites OK (14 tramites — 8 cliente@, 3 cliente2@, 3 cliente3@)");
    }

    private void crear(String codigo, String clienteId, String politicaId, String estado,
                       String nodoActualId, String funcionarioId, int prioridad,
                       LocalDateTime inicio, LocalDateTime estimadaCierre, LocalDateTime cierreReal,
                       List<String> nodosParalelos) {
        if (tramiteRepository.findByCodigo(codigo).isEmpty()) {
            Tramite t = new Tramite();
            t.setCodigo(codigo);
            t.setClienteId(clienteId);
            t.setPoliticaId(politicaId);
            t.setEstadoActual(estado);
            t.setNodoActualId(nodoActualId);
            t.setFuncionarioActualId(funcionarioId);
            t.setPrioridad(prioridad);
            t.setFechaInicio(inicio);
            t.setFechaEstimadaCierre(estimadaCierre);
            t.setFechaCierreReal(cierreReal);
            t.setNodosParalellosActivos(nodosParalelos);
            tramiteRepository.save(t);
        }
    }

    private String nodoId(List<NodoDiagrama> nodos, String tipo, String nombre) {
        return nodos.stream()
                .filter(n -> tipo.equals(n.getTipo()) && (nombre == null || nombre.equals(n.getNombre())))
                .findFirst().map(NodoDiagrama::getId).orElse(null);
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
