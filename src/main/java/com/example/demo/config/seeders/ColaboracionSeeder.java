package com.example.demo.config.seeders;

import com.example.demo.models.ColaboracionDiagrama;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.repositories.ColaboracionDiagramaRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class ColaboracionSeeder {

    @Autowired private ColaboracionDiagramaRepository colaboracionRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (colaboracionRepository.count() > 0) {
            log.info("[Seeder] Colaboraciones ya existen, omitiendo");
            return;
        }

        List<DiagramaWorkflow> diagramas = diagramaRepository.findAll();
        if (diagramas.isEmpty()) {
            log.warn("[Seeder] Sin diagramas para crear colaboraciones");
            return;
        }

        String diagramaId  = diagramas.get(0).getId();
        String diag2Id     = diagramas.size() > 1 ? diagramas.get(1).getId() : diagramaId;
        String adminId     = userId("admin@cre.bo");
        String admin2Id    = userId("admin2@cre.bo");
        String funcTecId   = userId("funcionario@cre.bo");
        String funcLegId   = userId("func_leg@cre.bo");
        String funcOpeId   = userId("func_ope@cre.bo");

        LocalDateTime now = LocalDateTime.now();

        crear(diagramaId, adminId, funcTecId, "editor", "aceptada",
                now.minusDays(10), now.minusDays(9));

        crear(diagramaId, adminId, funcLegId, "visor", "aceptada",
                now.minusDays(8), now.minusDays(7));

        crear(diagramaId, adminId, funcOpeId, "visor", "pendiente",
                now.minusDays(2), null);

        crear(diagramaId, admin2Id, adminId, "editor", "aceptada",
                now.minusDays(5), now.minusDays(4));
        crear(diag2Id, admin2Id, adminId, "visualizador", "pendiente",
                now.minusDays(1), null);

        log.info("[Seeder] Colaboraciones OK (admin invita x3; admin2 invita al admin x2)");
    }

    private void crear(String diagramaId, String adminId, String invitadoId,
                       String rol, String estado, LocalDateTime invitacion, LocalDateTime respuesta) {
        ColaboracionDiagrama c = new ColaboracionDiagrama();
        c.setDiagramaId(diagramaId);
        c.setAdminInvitadorId(adminId);
        c.setInvitadoId(invitadoId);
        c.setRolColaboracion(rol);
        c.setEstado(estado);
        c.setFechaInvitacion(invitacion);
        c.setFechaRespuesta(respuesta);
        colaboracionRepository.save(c);
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
