package com.example.demo.config.seeders;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class DiagramaSeeder {

    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository transicionRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (diagramaRepository.count() > 0) {
            // Migrate decision transitions from "Aprobado"/"Rechazado" to "si"/"no"
            // to match what WorkflowEngineService.avanzarDesde() expects.
            transicionRepository.findAll().forEach(t -> {
                if ("Aprobado".equals(t.getEtiqueta())) {
                    t.setEtiqueta("si");
                    transicionRepository.save(t);
                } else if ("Rechazado".equals(t.getEtiqueta())) {
                    t.setEtiqueta("no");
                    transicionRepository.save(t);
                }
            });
            log.info("[Seeder] Diagramas ya existen, etiquetas de decision normalizadas");
            return;
        }

        String adminId = usuarioRepository.findByEmail("admin@cre.bo")
                .map(u -> u.getId()).orElse("system");

        String politicaId = politicaRepository.findAll().stream()
                .filter(p -> "Nueva conexion residencial".equals(p.getNombre()))
                .findFirst().map(p -> p.getId()).orElse(null);

        String atcId = deptoId("ATC");
        String tecId = deptoId("TEC");
        String legId = deptoId("LEG");
        String opeId = deptoId("OPE");

        List<Actividad> acts = actividadRepository.findAll();
        String actAtcVer    = actId(acts, "Verificación de documentos del cliente");
        String actTecInsp   = actId(acts, "Inspección técnica en campo");
        String actTecPres   = actId(acts, "Elaboración de presupuesto técnico");
        String actLegContr  = actId(acts, "Revisión y aprobación del contrato");
        String actOpeCierre = actId(acts, "Ejecución de trabajos técnicos");

        DiagramaWorkflow diagrama = new DiagramaWorkflow();
        diagrama.setNombre("Flujo - Nueva Conexion Residencial");
        diagrama.setPoliticaId(politicaId);
        diagrama.setCreadorId(adminId);
        diagrama.setSwimlanes(List.of("ATC", "TEC", "LEG", "OPE"));
        diagrama.setVersionActual(1);
        diagrama.setEstado("publicado");
        diagrama.setGeneradoPorIa(false);
        diagrama.setFechaCreacion(LocalDateTime.now());
        diagrama.setUltimaModificacion(LocalDateTime.now());
        diagrama = diagramaRepository.save(diagrama);
        String diagId = diagrama.getId();

        if (politicaId != null) {
            politicaRepository.findById(politicaId).ifPresent(p -> {
                p.setDiagramaId(diagId);
                politicaRepository.save(p);
            });
        }

        NodoDiagrama nInicio    = nodo(diagId, "inicio",    "Inicio",                null,        null,  null,  1);
        NodoDiagrama nAtcVer    = nodo(diagId, "actividad", "Verificar Documentos",  actAtcVer,   atcId, "ATC", 2);
        NodoDiagrama nFork      = nodo(diagId, "fork",      "Fork",                  null,        null,  null,  3);
        NodoDiagrama nTecInsp   = nodo(diagId, "actividad", "Inspeccion en Campo",   actTecInsp,  tecId, "TEC", 4);
        NodoDiagrama nTecPres   = nodo(diagId, "actividad", "Elaborar Presupuesto",  actTecPres,  tecId, "TEC", 5);
        NodoDiagrama nJoin      = nodo(diagId, "join",      "Join",                  null,        null,  null,  6);
        NodoDiagrama nLegContr  = nodo(diagId, "actividad", "Revisar Contrato",      actLegContr, legId, "LEG", 7);
        NodoDiagrama nDecision  = nodo(diagId, "decision",  "Contrato aprobado?",    null,        null,  null,  8);
        NodoDiagrama nOpeCierre = nodo(diagId, "actividad", "Cierre y Conexion",     actOpeCierre,opeId, "OPE", 9);
        NodoDiagrama nFin       = nodo(diagId, "fin",       "Fin",                   null,        null,  null,  10);

        trans(diagId, nInicio.getId(),    nAtcVer.getId(),    "secuencial",  null,        null);
        trans(diagId, nAtcVer.getId(),    nFork.getId(),      "secuencial",  null,        null);
        trans(diagId, nFork.getId(),      nTecInsp.getId(),   "paralelo",    null,        "Rama 1");
        trans(diagId, nFork.getId(),      nTecPres.getId(),   "paralelo",    null,        "Rama 2");
        trans(diagId, nTecInsp.getId(),   nJoin.getId(),      "paralelo",    null,        null);
        trans(diagId, nTecPres.getId(),   nJoin.getId(),      "paralelo",    null,        null);
        trans(diagId, nJoin.getId(),      nLegContr.getId(),  "secuencial",  null,        null);
        trans(diagId, nLegContr.getId(),  nDecision.getId(),  "secuencial",  null,        null);
        trans(diagId, nDecision.getId(),  nOpeCierre.getId(), "condicional", "si",  "si");
        trans(diagId, nDecision.getId(),  nFork.getId(),      "condicional", "no",  "no");
        trans(diagId, nOpeCierre.getId(), nFin.getId(),       "secuencial",  null,        null);

        log.info("[Seeder] Diagrama Conexion Residencial OK");
    }

    private String deptoId(String codigo) {
        return departamentoRepository.findByCodigo(codigo).map(d -> d.getId()).orElse(null);
    }

    private String actId(List<Actividad> acts, String nombre) {
        return acts.stream().filter(a -> nombre.equals(a.getNombre()))
                .findFirst().map(Actividad::getId).orElse(null);
    }

    private NodoDiagrama nodo(String diagId, String tipo, String nombre, String actividadId,
                               String departamentoId, String swimlane, int orden) {
        NodoDiagrama n = new NodoDiagrama();
        n.setDiagramaId(diagId);
        n.setTipo(tipo);
        n.setNombre(nombre);
        n.setActividadId(actividadId);
        n.setDepartamentoId(departamentoId);
        n.setSwimlane(swimlane);
        n.setOrden(orden);
        return nodoRepository.save(n);
    }

    private void trans(String diagId, String origenId, String destinoId,
                       String tipo, String condicion, String etiqueta) {
        FlujoTransicion t = new FlujoTransicion();
        t.setDiagramaId(diagId);
        t.setNodoOrigenId(origenId);
        t.setNodoDestinoId(destinoId);
        t.setTipo(tipo);
        t.setCondicion(condicion);
        t.setEtiqueta(etiqueta);
        transicionRepository.save(t);
    }
}
