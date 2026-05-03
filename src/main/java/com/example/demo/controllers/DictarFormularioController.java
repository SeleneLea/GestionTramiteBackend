package com.example.demo.controllers;

import com.example.demo.dto.DictarFormularioResponse;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.TranscripcionVoz;
import com.example.demo.repositories.CampoPlantillaRepository;
import com.example.demo.repositories.FormularioPlantillaRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TranscripcionVozRepository;
import com.example.demo.services.IaProxyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CU-39 — Dictado de voz que rellena los campos del formulario activo de
 * una sección del expediente.
 */
@RestController
@RequestMapping("/api/expedientes")
@Tag(name = "IA · Dictar formulario", description = "CU-39 — voz → campos sugeridos del formulario")
public class DictarFormularioController {

    @Autowired private IaProxyService iaProxy;
    @Autowired private SeccionExpedienteRepository seccionRepository;
    @Autowired private FormularioPlantillaRepository formularioRepository;
    @Autowired private CampoPlantillaRepository campoRepository;
    @Autowired private TranscripcionVozRepository transcripcionRepository;

    private final ObjectMapper json = new ObjectMapper();

    @PostMapping(value = "/secciones/{seccionId}/dictar", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(summary = "Dictar y obtener campos sugeridos para el formulario activo")
    public ResponseEntity<DictarFormularioResponse> dictar(
            @PathVariable String seccionId,
            @RequestParam("audio") MultipartFile audio,
            Authentication auth) {

        SeccionExpediente seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new IllegalArgumentException("Sección no encontrada: " + seccionId));

        // Construir el schema del formulario activo (campos de la plantilla asociada al nodo)
        String schemaJson = construirSchemaCampos(seccion);

        // Delegar al microservicio IA — si falla, propaga 503 IA_NO_DISPONIBLE
        Map<String, Object> resp = iaProxy.vozAFormulario(audio, schemaJson);

        String texto = stringDe(resp.get("texto_transcrito"));
        List<DictarFormularioResponse.CampoSugerido> sugeridos = parseCampos(resp.get("campos"));

        // Persistir la transcripción para trazabilidad
        TranscripcionVoz tv = new TranscripcionVoz();
        tv.setSeccionId(seccionId);
        tv.setFuncionarioId(auth.getName());
        tv.setTextoTranscrito(texto != null ? texto : "");
        tv.setConfianzaTranscripcion(0.0f);
        tv.setDuracionSegundos(0.0f);
        tv.setFechaTranscripcion(LocalDateTime.now());
        tv = transcripcionRepository.save(tv);

        return ResponseEntity.ok(new DictarFormularioResponse(
                tv.getId(), texto, sugeridos));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String construirSchemaCampos(SeccionExpediente seccion) {
        // Cadena real:
        //   SeccionExpediente.nodoId → FormularioPlantilla.nodoId → CampoPlantilla.formularioPlantillaId
        if (seccion.getNodoId() == null) return "[]";

        var formularios = formularioRepository.findByNodoId(seccion.getNodoId());
        if (formularios.isEmpty()) return "[]";
        String plantillaId = formularios.get(0).getId();

        var campos = campoRepository.findByFormularioPlantillaId(plantillaId).stream()
                .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
                .toList();
        if (campos.isEmpty()) return "[]";

        List<Map<String, Object>> schema = new ArrayList<>();
        for (var c : campos) {
            schema.add(Map.of(
                    "nombre", c.getNombre() != null ? c.getNombre() : "",
                    "tipo",   c.getTipo()   != null ? c.getTipo()   : "texto"
            ));
        }
        try {
            return json.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<DictarFormularioResponse.CampoSugerido> parseCampos(Object raw) {
        if (!(raw instanceof List<?> items)) return List.of();
        List<DictarFormularioResponse.CampoSugerido> out = new ArrayList<>();
        for (Object it : items) {
            if (it instanceof Map<?, ?> m) {
                out.add(new DictarFormularioResponse.CampoSugerido(
                        stringDe(m.get("campo")),
                        stringDe(m.get("valor")),
                        floatDe(m.get("confianza"))));
            }
        }
        return out;
    }

    private String stringDe(Object o) { return o == null ? null : o.toString(); }

    private Float floatDe(Object o) {
        if (o instanceof Number n) return n.floatValue();
        if (o instanceof String s) {
            try { return Float.parseFloat(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
