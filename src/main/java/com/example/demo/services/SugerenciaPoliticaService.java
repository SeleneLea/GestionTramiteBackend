package com.example.demo.services;

import com.example.demo.dto.SugerirPoliticaRequest;
import com.example.demo.dto.SugerirPoliticaResponse;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.SugerenciaPolitica;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.SugerenciaPoliticaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CU-40 — Orquesta la sugerencia de política para el cliente.
 *
 * Flujo:
 *  1. Lista las políticas activas.
 *  2. Llama al microservicio IA para obtener el top 3.
 *  3. Persiste la {@link SugerenciaPolitica} (histórico para reentrenamiento).
 *  4. Devuelve la respuesta al cliente con el {@code sugerenciaId} para confirmar luego.
 */
@Service
public class SugerenciaPoliticaService {

    public static final String FEEDBACK_PENDIENTE = "PENDIENTE";
    public static final String FEEDBACK_ACEPTADA  = "ACEPTADA";
    public static final String FEEDBACK_CAMBIADA  = "CAMBIADA";
    public static final String FEEDBACK_CANCELADA = "CANCELADA";

    @Autowired private IaProxyService iaProxy;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private SugerenciaPoliticaRepository sugerenciaRepository;

    public SugerirPoliticaResponse sugerir(SugerirPoliticaRequest req, String clienteId) {
        // Solo políticas activas son candidatas (RN-CL01)
        List<PoliticaNegocio> activas = politicaRepository.findByEstado("activa");
        if (activas.isEmpty()) {
            throw new IllegalStateException(
                    "No hay políticas activas — no se puede sugerir una para el trámite.");
        }

        List<Map<String, Object>> politicasParaIa = new ArrayList<>();
        for (PoliticaNegocio p : activas) {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("id", p.getId());
            entry.put("nombre", p.getNombre() != null ? p.getNombre() : "");
            // Texto real de la política → alimenta la heurística de respaldo del
            // microservicio cuando el modelo TF aún no conoce esta política.
            entry.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "");
            entry.put("categoria", p.getCategoria() != null ? p.getCategoria() : "");
            entry.put("palabras_clave", List.of());
            politicasParaIa.add(entry);
        }

        Map<String, Object> resp = iaProxy.sugerirPolitica(req.getDescripcion(), politicasParaIa);

        String politicaId = stringDe(resp.get("politica_id"));
        Float confianza   = floatDe(resp.get("confianza"));
        List<SugerirPoliticaResponse.Candidato> top3 = parseTop3(resp.get("top3"));

        // Persistir histórico
        SugerenciaPolitica sug = new SugerenciaPolitica();
        sug.setClienteId(clienteId);
        sug.setDescripcionOriginal(req.getDescripcion());
        sug.setPoliticaSugeridaId(politicaId);
        sug.setConfianza(confianza != null ? confianza : 0f);
        sug.setTop3(top3.stream()
                .map(c -> new SugerenciaPolitica.Candidato(
                        c.getPoliticaId(), c.getNombre(),
                        c.getConfianza() != null ? c.getConfianza() : 0f))
                .toList());
        sug.setFeedback(FEEDBACK_PENDIENTE);
        sug.setFechaCreacion(LocalDateTime.now());
        sug = sugerenciaRepository.save(sug);

        return new SugerirPoliticaResponse(
                sug.getId(),
                politicaId,
                confianza,
                top3);
    }

    public SugerenciaPolitica confirmar(String sugerenciaId, String politicaConfirmadaId,
                                        String tramiteCreadoId) {
        SugerenciaPolitica sug = sugerenciaRepository.findById(sugerenciaId)
                .orElseThrow(() -> new IllegalArgumentException("Sugerencia no encontrada: " + sugerenciaId));

        sug.setPoliticaConfirmadaId(politicaConfirmadaId);
        sug.setTramiteCreadoId(tramiteCreadoId);
        sug.setFeedback(
                politicaConfirmadaId.equals(sug.getPoliticaSugeridaId())
                        ? FEEDBACK_ACEPTADA : FEEDBACK_CAMBIADA);
        return sugerenciaRepository.save(sug);
    }

    public SugerenciaPolitica cancelar(String sugerenciaId) {
        SugerenciaPolitica sug = sugerenciaRepository.findById(sugerenciaId)
                .orElseThrow(() -> new IllegalArgumentException("Sugerencia no encontrada: " + sugerenciaId));
        sug.setFeedback(FEEDBACK_CANCELADA);
        return sugerenciaRepository.save(sug);
    }

    // ── parseo defensivo ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<SugerirPoliticaResponse.Candidato> parseTop3(Object raw) {
        if (!(raw instanceof List<?> items)) return List.of();
        List<SugerirPoliticaResponse.Candidato> out = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> m) {
                out.add(new SugerirPoliticaResponse.Candidato(
                        stringDe(m.get("politica_id")),
                        stringDe(m.get("nombre")),
                        floatDe(m.get("confianza"))));
            }
        }
        return out;
    }

    private String stringDe(Object o) {
        return o == null ? null : o.toString();
    }

    private Float floatDe(Object o) {
        if (o instanceof Number n) return n.floatValue();
        if (o instanceof String s) {
            try { return Float.parseFloat(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
