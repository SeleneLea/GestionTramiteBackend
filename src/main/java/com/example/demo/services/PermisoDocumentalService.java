package com.example.demo.services;

import com.example.demo.dto.PermisoPuntoAtencionRequest;
import com.example.demo.models.PermisoPuntoAtencion;
import com.example.demo.repositories.PermisoPuntoAtencionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CU-36 — Configurar permisos por punto de atención.
 *
 * Niveles válidos: SOLO_LECTURA | SOLO_EDICION | LECTURA_Y_EDICION.
 * Default cuando no hay configuración explícita: SOLO_LECTURA (RN-P01).
 */
@Service
public class PermisoDocumentalService {

    public static final String SOLO_LECTURA       = "SOLO_LECTURA";
    public static final String SOLO_EDICION       = "SOLO_EDICION";
    public static final String LECTURA_Y_EDICION  = "LECTURA_Y_EDICION";

    @Autowired private PermisoPuntoAtencionRepository repo;

    public PermisoPuntoAtencion upsert(PermisoPuntoAtencionRequest req, String adminId) {
        validarNivel(req.getNivelAcceso());

        PermisoPuntoAtencion p = repo.findByPoliticaIdAndActividadId(
                req.getPoliticaId(), req.getActividadId())
                .orElseGet(PermisoPuntoAtencion::new);

        p.setPoliticaId(req.getPoliticaId());
        p.setActividadId(req.getActividadId());
        p.setNivelAcceso(req.getNivelAcceso());
        p.setTiposDocumentoVisibles(req.getTiposDocumentoVisibles());
        p.setActualizadoPorId(adminId);
        p.setFechaActualizacion(LocalDateTime.now());

        return repo.save(p);
    }

    public PermisoPuntoAtencion buscarOPorDefecto(String politicaId, String actividadId) {
        return repo.findByPoliticaIdAndActividadId(politicaId, actividadId)
                .orElseGet(() -> {
                    PermisoPuntoAtencion p = new PermisoPuntoAtencion();
                    p.setPoliticaId(politicaId);
                    p.setActividadId(actividadId);
                    p.setNivelAcceso(SOLO_LECTURA);   // RN-P01
                    return p;
                });
    }

    public List<PermisoPuntoAtencion> listarPorPolitica(String politicaId) {
        return repo.findByPoliticaId(politicaId);
    }

    /** True si el nivel del permiso permite subir/editar documentos. */
    public boolean permiteEscritura(String nivelAcceso) {
        return SOLO_EDICION.equals(nivelAcceso) || LECTURA_Y_EDICION.equals(nivelAcceso);
    }

    /** True si el nivel del permiso permite leer documentos. */
    public boolean permiteLectura(String nivelAcceso) {
        return SOLO_LECTURA.equals(nivelAcceso) || LECTURA_Y_EDICION.equals(nivelAcceso);
    }

    private void validarNivel(String nivel) {
        if (!SOLO_LECTURA.equals(nivel)
                && !SOLO_EDICION.equals(nivel)
                && !LECTURA_Y_EDICION.equals(nivel)) {
            throw new IllegalArgumentException(
                    "nivelAcceso inválido: " + nivel + " (válidos: SOLO_LECTURA, SOLO_EDICION, LECTURA_Y_EDICION)");
        }
    }
}
