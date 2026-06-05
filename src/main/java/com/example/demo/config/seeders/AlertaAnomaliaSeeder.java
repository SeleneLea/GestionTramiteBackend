package com.example.demo.config.seeders;

import com.example.demo.models.AlertaAnomalia;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.AlertaAnomaliaRepository;
import com.example.demo.repositories.TramiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class AlertaAnomaliaSeeder {

    @Autowired private AlertaAnomaliaRepository alertaRepo;
    @Autowired private TramiteRepository tramiteRepo;

    public void seed() {
        if (alertaRepo.count() > 0) {
            log.info("[Seeder] AlertaAnomalia ya existe, omitiendo");
            return;
        }

        List<Tramite> tramites = tramiteRepo.findAll();
        if (tramites.isEmpty()) {
            log.info("[Seeder] AlertaAnomalia omitido (no hay trámites)");
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();

        crear(tramites, 0, "tiempo_atipico", 0.92f,
                "El trámite lleva 3.4× el tiempo promedio para esta etapa (Verificación de documentos).",
                ahora.minusHours(2));

        crear(tramites, 1, "tiempo_atipico", 0.78f,
                "SLA superado en la etapa de Elaboración de Presupuesto (+18h respecto al límite).",
                ahora.minusHours(8));

        crear(tramites, 2, "secuencia_inusual", 0.81f,
                "Orden de nodos atípico: Cierre antes de Aprobación legal (probabilidad < 1% en el histórico).",
                ahora.minusHours(14));

        crear(tramites, 3, "loop_derivaciones", 0.95f,
                "El expediente fue derivado 4 veces entre TEC y LEG en 48h (umbral normal: 1).",
                ahora.minusDays(1));

        crear(tramites, 4 % tramites.size(), "salto_no_autorizado", 0.88f,
                "Avance al nodo de Cierre sin pasar por Inspección técnica obligatoria.",
                ahora.minusDays(2));

        crear(tramites, 5 % tramites.size(), "secuencia_inusual", 0.55f,
                "Patrón de actividad fuera del flujo estándar — revisar bitácora.",
                ahora.minusDays(3));

        log.info("[Seeder] AlertaAnomalia OK (6 alertas creadas)");
    }

    private void crear(List<Tramite> tramites, int indice, String categoria, float score,
                       String descripcion, LocalDateTime fechaDeteccion) {
        if (tramites.isEmpty()) return;
        Tramite t = tramites.get(indice % tramites.size());

        AlertaAnomalia a = new AlertaAnomalia();
        a.setTramiteId(t.getId());
        a.setCategoria(categoria);
        a.setScore(score);
        a.setDescripcion(descripcion);
        a.setFalsoPositivo(false);
        a.setFechaDeteccion(fechaDeteccion);
        alertaRepo.save(a);
    }
}
