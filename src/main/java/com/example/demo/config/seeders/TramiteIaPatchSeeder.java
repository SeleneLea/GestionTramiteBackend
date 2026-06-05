package com.example.demo.config.seeders;

import com.example.demo.models.EstadoTramite;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TramiteIaPatchSeeder {

    @Autowired private TramiteRepository tramiteRepo;
    @Autowired private DiagramaWorkflowRepository diagramaRepo;
    @Autowired private NodoDiagramaRepository nodoRepo;

    public void seed() {
        var tramites = tramiteRepo.findAll();
        if (tramites.isEmpty()) {
            log.info("[Seeder] TramiteIaPatch omitido (no hay trámites)");
            return;
        }

        int actualizados = 0;
        for (Tramite t : tramites) {
            boolean cambio = false;

            boolean cerrado = EstadoTramite.esFinalizado(t.getEstadoActual());

            if (t.getRiesgoDemora() == null || t.getRiesgoDemora().isBlank()) {
                t.setRiesgoDemora(cerrado ? "bajo" : nivelPorPrioridad(t.getPrioridad()));
                cambio = true;
            }
            if (t.getProbSuperarSla() == null) {
                t.setProbSuperarSla(cerrado ? 0.10f : probPorNivel(t.getRiesgoDemora()));
                cambio = true;
            }
            if (t.getRutaSugerida() == null || t.getRutaSugerida().isEmpty()) {
                List<String> ruta = rutaCanonicaDeLaPolitica(t.getPoliticaId());
                if (!ruta.isEmpty()) {
                    t.setRutaSugerida(ruta);
                    cambio = true;
                }
            }

            if (cambio) {
                tramiteRepo.save(t);
                actualizados++;
            }
        }
        log.info("[Seeder] TramiteIaPatch OK ({} trámites actualizados)", actualizados);
    }

    private String nivelPorPrioridad(Integer prioridad) {
        if (prioridad == null) return "desconocido";
        if (prioridad >= 3) return "alto";
        if (prioridad == 2) return "medio";
        return "bajo";
    }

    private float probPorNivel(String nivel) {
        if (nivel == null) return 0.30f;
        switch (nivel) {
            case "alto":   return 0.85f;
            case "medio":  return 0.55f;
            case "bajo":   return 0.20f;
            default:       return 0.30f;
        }
    }

    private List<String> rutaCanonicaDeLaPolitica(String politicaId) {
        if (politicaId == null) return new ArrayList<>();
        var diagrama = diagramaRepo.findByPoliticaId(politicaId);
        if (diagrama.isEmpty()) return new ArrayList<>();

        var nodos = nodoRepo.findByDiagramaId(diagrama.get().getId());
        return nodos.stream()
                .filter(n -> "actividad".equalsIgnoreCase(n.getTipo()))
                .map(NodoDiagrama::getId)
                .collect(Collectors.toList());
    }
}
