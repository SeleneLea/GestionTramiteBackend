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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TrazabilidadSeeder {

    private static final String HASH_GENESIS =
            "0000000000000000000000000000000000000000000000000000000000000000";

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

            switch (t.getCodigo()) {
                case "TRM-2024-001" -> {
                    String h1 = reg(t.getId(), funcAtcId, "iniciar",         nAtcVer,  null,     HASH_GENESIS, base);
                    String h2 = reg(t.getId(), funcAtcId, "completar_nodo",  nAtcVer,  Map.of("documentos_ok", true), h1, base.plusHours(6));
                    String h3 = reg(t.getId(), funcTecId, "completar_nodo",  null,     Map.of("inspeccion_ok", true), h2, base.plusDays(5));
                    String h4 = reg(t.getId(), funcLegId, "completar_nodo",  nLegContr,Map.of("contrato", "CONT-2024-0421"), h3, base.plusDays(18));
                    reg(t.getId(), funcOpeId, "completar_nodo", nFin, Map.of("medidor", "MED20240421", "estado_final", "Completado"), h4, base.plusDays(25));
                }
                case "TRM-2024-002" -> {
                    String h1 = reg(t.getId(), funcAtcId, "iniciar",  nAtcVer,  null, HASH_GENESIS, base);
                    reg(t.getId(), funcLegId, "rechazar", nLegContr, Map.of("motivo", "Documentacion insuficiente"), h1, base.plusDays(15));
                }
                case "TRM-2024-003" -> {
                    String h1 = reg(t.getId(), funcAtcId, "iniciar",  nAtcVer, null, HASH_GENESIS, base);
                    reg(t.getId(), funcTecId, "completar_rama_paralela", null, Map.of("rama", "paralelo"), h1, base.plusDays(2));
                }
                case "TRM-2024-004" -> {
                    reg(t.getId(), funcAtcId, "iniciar", nAtcVer, null, HASH_GENESIS, base);
                }
                case "TRM-2024-005" -> {
                    String h1 = reg(t.getId(), funcAtcId, "iniciar",     nAtcVer,  null, HASH_GENESIS, base);
                    reg(t.getId(), funcLegId,  "observar", nLegContr, Map.of("observacion", "Falta firma notarial"), h1, base.plusDays(8));
                }
                case "TRM-2024-006" -> {
                    String h1 = reg(t.getId(), funcAtcId, "iniciar",  nAtcVer, null, HASH_GENESIS, base);
                    reg(t.getId(), adminId,    "cancelar", null, Map.of("motivo", "Solicitud del cliente"), h1, base.plusDays(2));
                }
                default -> {

                    String h1 = reg(t.getId(), funcAtcId, "iniciar", nAtcVer, null, HASH_GENESIS, base);
                    if (t.getNodoActualId() != null) {
                        reg(t.getId(), funcTecId, "completar_nodo", t.getNodoActualId(),
                                Map.of("avance", "etapa_actual"), h1, base.plusDays(1));
                    }
                }
            }
        }
        log.info("[Seeder] Trazabilidad OK");
    }

    private String reg(String tramiteId, String actorId, String accion, String nodoId,
                       Map<String, Object> datosDespues, String hashAnterior,
                       LocalDateTime ts) {
        Trazabilidad tr = new Trazabilidad();
        tr.setTramiteId(tramiteId);
        tr.setActorId(actorId);
        tr.setAccion(accion);
        tr.setNodoId(nodoId);
        tr.setDatosAntes(null);
        tr.setDatosDespues(datosDespues);

        tr.setTimestamp(ts != null ? ts.truncatedTo(ChronoUnit.MILLIS) : null);
        tr.setHashAnterior(hashAnterior);

        tr.setHashActual(generarHash(construirInputHash(tr)));

        trazabilidadRepository.save(tr);
        return tr.getHashActual();
    }

    private String construirInputHash(Trazabilidad t) {
        Map<String, Object> datos = t.getDatosDespues();
        String datosCanon = new java.util.TreeMap<>(
                datos != null ? datos : java.util.Map.<String, Object>of()).toString();
        return String.join("|",
                t.getTramiteId() == null ? "" : t.getTramiteId(),
                t.getActorId() == null ? "" : t.getActorId(),
                t.getAccion() == null ? "" : t.getAccion(),
                t.getNodoId() == null ? "" : t.getNodoId(),
                datosCanon,
                String.valueOf(t.getTimestamp()),
                t.getHashAnterior() == null ? "" : t.getHashAnterior());
    }

    private String generarHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error de integridad: algoritmo SHA-256 no disponible.");
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
