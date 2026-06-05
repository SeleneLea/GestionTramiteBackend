package com.example.demo.services;

import com.example.demo.models.AlertaAnomalia;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.AlertaAnomaliaRepository;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlertaAnomaliaService {

    @Autowired private IaProxyService iaProxy;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private EstadoHistoricoRepository estadoHistoricoRepository;
    @Autowired private AlertaAnomaliaRepository alertaRepo;

    public List<AlertaAnomalia> detectarYPersistir() {
        List<Tramite> tramites = tramiteRepository.findAll();
        List<Map<String, Object>> secuencias = new ArrayList<>();

        for (Tramite t : tramites) {
            List<EstadoHistorico> hist = estadoHistoricoRepository
                    .findByTramiteIdOrderByFechaCambioAsc(t.getId());
            if (hist.size() < 2) continue;

            List<Map<String, Object>> trans = new ArrayList<>();
            for (int i = 1; i < hist.size(); i++) {
                EstadoHistorico anterior = hist.get(i - 1);
                EstadoHistorico actual = hist.get(i);
                long delta = anterior.getFechaCambio() != null && actual.getFechaCambio() != null
                        ? Duration.between(anterior.getFechaCambio(), actual.getFechaCambio()).getSeconds()
                        : 0;
                Map<String, Object> entry = new HashMap<>();
                entry.put("nodo_anterior", anterior.getNodoNuevoId() != null ? anterior.getNodoNuevoId() : "");
                entry.put("nodo", actual.getNodoNuevoId() != null ? actual.getNodoNuevoId() : "");
                entry.put("delta_segundos", delta);
                trans.add(entry);
            }
            Map<String, Object> sec = new HashMap<>();
            sec.put("tramite_id", t.getId());
            sec.put("transiciones", trans);
            secuencias.add(sec);
        }

        if (secuencias.isEmpty()) return List.of();

        List<Map<String, Object>> anomalias = iaProxy.anomalias(secuencias);

        List<AlertaAnomalia> creadas = new ArrayList<>();
        for (Map<String, Object> a : anomalias) {
            AlertaAnomalia alerta = new AlertaAnomalia();
            alerta.setTramiteId(stringDe(a.get("tramite_id")));
            alerta.setCategoria(stringDe(a.get("categoria")));
            alerta.setScore(floatDe(a.get("score")));
            alerta.setDescripcion(stringDe(a.get("descripcion")));
            alerta.setFalsoPositivo(false);
            alerta.setFechaDeteccion(LocalDateTime.now());
            creadas.add(alertaRepo.save(alerta));
        }
        return creadas;
    }

    public List<AlertaAnomalia> listarNoRevisadas() {
        return alertaRepo.findByFalsoPositivoFalseOrderByFechaDeteccionDesc();
    }

    public AlertaAnomalia marcarFalsoPositivo(String id, String adminId) {
        AlertaAnomalia a = alertaRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada: " + id));
        a.setFalsoPositivo(true);
        a.setRevisadaPor(adminId);
        a.setFechaRevision(LocalDateTime.now());
        return alertaRepo.save(a);
    }

    private String stringDe(Object o) { return o == null ? null : o.toString(); }

    private float floatDe(Object o) {
        if (o instanceof Number n) return n.floatValue();
        if (o instanceof String s) {
            try { return Float.parseFloat(s); } catch (NumberFormatException ignored) {}
        }
        return 0f;
    }
}
