package com.example.demo.config.seeders;

import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.Tramite;
import com.example.demo.models.Trazabilidad;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.TrazabilidadRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TrazabilidadSeeder {

    @Autowired private TrazabilidadRepository trazabilidadRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (trazabilidadRepository.count() > 0) {
            log.info("[Seeder] Trazabilidad ya existe, omitiendo");
            return;
        }

        List<Tramite> tramites = tramiteRepository.findAll();
        List<NodoDiagrama> nodos = nodoRepository.findAll();

        String funcAtcId = userId("func_atc@cre.bo");
        String funcTecId = userId("funcionario@cre.bo");
        String funcLegId = userId("func_leg@cre.bo");
        String funcOpeId = userId("func_ope@cre.bo");
        String adminId   = userId("admin@cre.bo");

        String nAtcVer  = nodoId(nodos, "actividad", "Verificar Documentos");
        String nLegContr= nodoId(nodos, "actividad", "Revisar Contrato");
        String nFin     = nodoId(nodos, "fin", null);

        for (Tramite t : tramites) {
            LocalDateTime base = t.getFechaInicio() != null ? t.getFechaInicio() : LocalDateTime.now().minusDays(5);
            String hash0 = "sha256-" + t.getCodigo() + "-init";

            switch (t.getCodigo()) {
                case "TRM-2024-001" -> {
                    String h1 = reg(t.getId(), funcAtcId, "INICIAR_TRAMITE",   nAtcVer,  null,     hash0,                   null, base);
                    String h2 = reg(t.getId(), funcAtcId, "COMPLETAR_NODO",    nAtcVer,  Map.of("documentos_ok", true), h1, hash0, base.plusHours(6));
                    String h3 = reg(t.getId(), funcTecId, "COMPLETAR_NODO",    null,     Map.of("inspeccion_ok", true), h2, h1,   base.plusDays(5));
                    String h4 = reg(t.getId(), funcLegId, "COMPLETAR_NODO",    nLegContr,Map.of("contrato", "CONT-2024-0421"), h3, h2, base.plusDays(18));
                    reg(t.getId(), funcOpeId, "COMPLETAR_TRAMITE", nFin, Map.of("medidor", "MED20240421", "estado_final", "Completado"), h4, h3, base.plusDays(25));
                }
                case "TRM-2024-002" -> {
                    String h1 = reg(t.getId(), funcAtcId, "INICIAR_TRAMITE",  nAtcVer,  null, hash0, null, base);
                    reg(t.getId(), funcLegId, "RECHAZAR_TRAMITE", nLegContr, Map.of("motivo", "Documentacion insuficiente"), h1, hash0, base.plusDays(15));
                }
                case "TRM-2024-003" -> {
                    String h1 = reg(t.getId(), funcAtcId, "INICIAR_TRAMITE",  nAtcVer, null, hash0, null, base);
                    reg(t.getId(), funcTecId, "AVANZAR_NODO", null, Map.of("rama", "paralelo"), h1, hash0, base.plusDays(2));
                }
                case "TRM-2024-004" -> {
                    reg(t.getId(), funcAtcId, "INICIAR_TRAMITE", nAtcVer, null, hash0, null, base);
                }
                case "TRM-2024-005" -> {
                    String h1 = reg(t.getId(), funcAtcId, "INICIAR_TRAMITE",     nAtcVer,  null, hash0, null, base);
                    reg(t.getId(), funcLegId,  "OBSERVAR_NODO", nLegContr, Map.of("observacion", "Falta firma notarial"), h1, hash0, base.plusDays(8));
                }
                case "TRM-2024-006" -> {
                    String h1 = reg(t.getId(), funcAtcId, "INICIAR_TRAMITE",  nAtcVer, null, hash0, null, base);
                    reg(t.getId(), adminId,    "CANCELAR_TRAMITE", null, Map.of("motivo", "Solicitud del cliente"), h1, hash0, base.plusDays(2));
                }
            }
        }
        log.info("[Seeder] Trazabilidad OK");
    }

    private String reg(String tramiteId, String actorId, String accion, String nodoId,
                       Map<String, Object> datosDespues, String hashActual, String hashAnterior,
                       LocalDateTime ts) {
        Trazabilidad tr = new Trazabilidad();
        tr.setTramiteId(tramiteId);
        tr.setActorId(actorId);
        tr.setAccion(accion);
        tr.setNodoId(nodoId);
        tr.setDatosAntes(null);
        tr.setDatosDespues(datosDespues);
        tr.setHashActual(hashActual);
        tr.setHashAnterior(hashAnterior);
        tr.setTimestamp(ts);
        trazabilidadRepository.save(tr);
        return hashActual;
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
