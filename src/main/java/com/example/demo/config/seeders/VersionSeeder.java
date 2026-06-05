package com.example.demo.config.seeders;

import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.VersionDiagrama;
import com.example.demo.models.VersionPolitica;
import com.example.demo.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class VersionSeeder {

    @Autowired private VersionPoliticaRepository versionPoliticaRepository;
    @Autowired private VersionDiagramaRepository versionDiagramaRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (versionPoliticaRepository.count() > 0) {
            log.info("[Seeder] Versiones ya existen, omitiendo");
            return;
        }

        String adminId   = userId("admin@cre.bo");
        String superUser = userId("superuser@cre.bo");

        LocalDateTime now = LocalDateTime.now();

        List<PoliticaNegocio> politicas = politicaRepository.findAll();

        politicas.stream()
                .filter(p -> "Nueva conexion residencial".equals(p.getNombre()))
                .findFirst()
                .ifPresent(politica -> {
                    String diagramaId = politica.getDiagramaId();

                    if (!versionPoliticaRepository.existsByPoliticaIdAndNumeroVersion(politica.getId(), 1)) {
                        VersionPolitica vp1 = new VersionPolitica();
                        vp1.setPoliticaId(politica.getId());
                        vp1.setNumeroVersion(1);
                        vp1.setDiagramaSnapshotId(diagramaId);
                        vp1.setCreadorId(adminId);
                        vp1.setNotasCambio("Version inicial del proceso de nueva conexion residencial");
                        vp1.setFechaVersion(now.minusDays(30));
                        versionPoliticaRepository.save(vp1);
                    }

                    if (!versionPoliticaRepository.existsByPoliticaIdAndNumeroVersion(politica.getId(), 2)) {
                        VersionPolitica vp2 = new VersionPolitica();
                        vp2.setPoliticaId(politica.getId());
                        vp2.setNumeroVersion(2);
                        vp2.setDiagramaSnapshotId(diagramaId);
                        vp2.setCreadorId(superUser);
                        vp2.setNotasCambio("Ajuste del SLA de revision legal de 48h a 24h. Adicion de nodo de notificacion automatica al cliente.");
                        vp2.setFechaVersion(now.minusDays(5));
                        versionPoliticaRepository.save(vp2);
                    }

                    if (diagramaId != null && !versionDiagramaRepository.existsByDiagramaIdAndNumeroVersion(diagramaId, 1)) {
                        VersionDiagrama vd1 = new VersionDiagrama();
                        vd1.setDiagramaId(diagramaId);
                        vd1.setNumeroVersion(1);
                        vd1.setSnapshot(Map.of(
                                "nodos", 10,
                                "transiciones", 11,
                                "swimlanes", List.of("ATC", "TEC", "LEG", "OPE"),
                                "tipo_flujo", "paralelo_condicional"
                        ));
                        vd1.setModificadoPorId(adminId);
                        vd1.setDescripcionCambio("Version inicial publicada del diagrama de conexion residencial");
                        vd1.setFechaVersion(now.minusDays(30));
                        versionDiagramaRepository.save(vd1);
                    }
                });

        log.info("[Seeder] Versiones de politica y diagrama OK");
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
