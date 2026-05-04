package com.example.demo.config.seeders;

import com.example.demo.models.EstadoActual;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class EstadoSeeder {

    @Autowired private EstadoActualRepository estadoActualRepository;
    @Autowired private EstadoHistoricoRepository estadoHistoricoRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (estadoActualRepository.count() > 0) {
            log.info("[Seeder] Estados ya existen, omitiendo");
            return;
        }

        List<Tramite> tramites = tramiteRepository.findAll();
        List<NodoDiagrama> nodos = nodoRepository.findAll();

        String funcAtcId = userId("func_atc@cre.bo");
        String funcTecId = userId("funcionario@cre.bo");
        String funcLegId = userId("func_leg@cre.bo");
        String funcOpeId = userId("func_ope@cre.bo");
        String adminId   = userId("admin@cre.bo");

        for (Tramite t : tramites) {
            // Estado actual snapshot
            EstadoActual ea = new EstadoActual();
            ea.setTramiteId(t.getId());
            ea.setEstado(t.getEstadoActual());
            ea.setNodoId(t.getNodoActualId());
            ea.setDesde(t.getFechaInicio() != null ? t.getFechaInicio() : LocalDateTime.now());
            estadoActualRepository.save(ea);

            // Historial de estados segun el tramite
            crearHistorialParaTramite(t, nodos, funcAtcId, funcTecId, funcLegId, funcOpeId, adminId);
        }
        log.info("[Seeder] EstadoActual y EstadoHistorico OK");
    }

    private void crearHistorialParaTramite(Tramite t, List<NodoDiagrama> nodos,
                                            String funcAtcId, String funcTecId,
                                            String funcLegId, String funcOpeId, String adminId) {
        String nAtcVer   = nodoId(nodos, "actividad", "Verificar Documentos");
        String nFork     = nodoId(nodos, "fork",      null);
        String nTecInsp  = nodoId(nodos, "actividad", "Inspeccion en Campo");
        String nLegContr = nodoId(nodos, "actividad", "Revisar Contrato");
        String nDecision = nodoId(nodos, "decision",  null);
        String nOpeCierre= nodoId(nodos, "actividad", "Cierre y Conexion");
        String nFin      = nodoId(nodos, "fin",       null);

        LocalDateTime base = t.getFechaInicio() != null ? t.getFechaInicio() : LocalDateTime.now().minusDays(5);

        switch (t.getCodigo()) {
            case "TRM-2024-001" -> {
                // Tramite completado: paso por todos los nodos
                hist(t.getId(), "En curso",   "En curso", null,        nAtcVer,   funcAtcId, "Documentos verificados", base.plusHours(2));
                hist(t.getId(), "En curso", "En curso", nAtcVer,     nFork,     funcAtcId, "Derivado a inspeccion y presupuesto", base.plusDays(1));
                hist(t.getId(), "En curso", "En curso", nFork,       nTecInsp,  funcTecId, "Inspeccion completada", base.plusDays(5));
                hist(t.getId(), "En curso", "En curso", nTecInsp,    nLegContr, funcLegId, "Presupuesto elaborado, contrato en revision", base.plusDays(10));
                hist(t.getId(), "En curso", "En curso", nLegContr,   nDecision, funcLegId, "Contrato revisado y aprobado", base.plusDays(18));
                hist(t.getId(), "En curso", "En curso", nDecision,   nOpeCierre,funcOpeId, "Decision: aprobado, iniciando conexion", base.plusDays(20));
                hist(t.getId(), "En curso", "Aprobado", nOpeCierre,  nFin,      funcOpeId, "Conexion realizada exitosamente", base.plusDays(25));
            }
            case "TRM-2024-002" -> {
                hist(t.getId(), "En curso",   "En curso", null,        nAtcVer,   funcAtcId, "Documentos recibidos", base.plusHours(1));
                hist(t.getId(), "En curso", "En curso", nAtcVer,     nLegContr, funcLegId, "Derivado a revision legal", base.plusDays(3));
                hist(t.getId(), "En curso", "Rechazado",  nLegContr,   nDecision, funcLegId, "Contrato rechazado: documentacion incompleta", base.plusDays(15));
            }
            case "TRM-2024-003" -> {
                hist(t.getId(), "En curso",   "En curso", null,        nAtcVer,   funcAtcId, "Documentos verificados", base.plusHours(3));
                hist(t.getId(), "En curso", "En curso", nAtcVer,     nFork,     funcAtcId, "Derivado a inspeccion y presupuesto en paralelo", base.plusDays(2));
            }
            case "TRM-2024-004" -> {
                hist(t.getId(), "En curso",   "En curso",   null,        nAtcVer,   funcAtcId, "Tramite iniciado por el cliente", base);
            }
            case "TRM-2024-005" -> {
                hist(t.getId(), "En curso",   "En curso", null,        nAtcVer,   funcAtcId, "Documentos verificados", base.plusHours(2));
                hist(t.getId(), "En curso", "En curso", nAtcVer,     nFork,     funcAtcId, "Derivado a inspeccion", base.plusDays(2));
                hist(t.getId(), "En curso", "Observado",  nFork,       nLegContr, funcLegId, "Contrato con observaciones pendientes", base.plusDays(8));
            }
            case "TRM-2024-006" -> {
                hist(t.getId(), "En curso",   "Cancelado",  null,        nAtcVer,   adminId, "Tramite cancelado a solicitud del cliente", base.plusDays(2));
            }
            default -> {
                // Historial minimo para cualquier tramite no cubierto explicitamente:
                // garantiza >=2 entradas y un timeline no vacio para el detector de anomalias.
                hist(t.getId(), null,        "En curso",          null,                 nAtcVer,            funcAtcId, "Tramite iniciado", base);
                if (t.getNodoActualId() != null) {
                    hist(t.getId(), "En curso", t.getEstadoActual(), nAtcVer,              t.getNodoActualId(), funcTecId, "Avance a etapa actual", base.plusDays(1));
                } else if (t.getNodosParalellosActivos() != null && !t.getNodosParalellosActivos().isEmpty()) {
                    List<String> nodosParalelos = t.getNodosParalellosActivos();
                    hist(t.getId(), "En curso", t.getEstadoActual(), nAtcVer,              nodosParalelos.get(0), funcTecId, "Avance a etapa paralela", base.plusDays(1));
                }
            }
        }
    }

    private void hist(String tramiteId, String estadoAnterior, String estadoNuevo,
                      String nodoAnteriorId, String nodoNuevoId, String actorId,
                      String motivo, LocalDateTime fecha) {
        EstadoHistorico h = new EstadoHistorico();
        h.setTramiteId(tramiteId);
        h.setEstadoAnterior(estadoAnterior);
        h.setEstadoNuevo(estadoNuevo);
        h.setNodoAnteriorId(nodoAnteriorId);
        h.setNodoNuevoId(nodoNuevoId);
        h.setActorId(actorId);
        h.setMotivo(motivo);
        h.setFechaCambio(fecha);
        estadoHistoricoRepository.save(h);
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
