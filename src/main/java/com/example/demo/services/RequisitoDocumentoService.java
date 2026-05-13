package com.example.demo.services;

import com.example.demo.models.Actividad;
import com.example.demo.models.DocumentoArchivo;
import com.example.demo.models.RequisitoDocumento;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DocumentoArchivoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lógica de la "compuerta de documentos": qué requisitos OBLIGATORIOS del CLIENTE
 * faltan en un paso del trámite.
 *
 * <p>IMPORTANTE — opt-in por actividad: la compuerta solo aplica a actividades que
 * tienen {@link Actividad#getDocumentosRequeridos()} explícito. Las actividades legacy
 * (solo {@code documentoIds}) NO se enforce, para no romper trámites existentes.
 */
@Service
public class RequisitoDocumentoService {

    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DocumentoArchivoRepository docRepo;

    /**
     * Lista COMPLETA de requisitos de la actividad para mostrar (no para la compuerta):
     * usa {@code documentosRequeridos} explícito si existe; si no, deriva de
     * {@code documentoIds} legacy tratando cada uno como CLIENTE + obligatorio.
     */
    public List<RequisitoDocumento> requisitosDe(Actividad act) {
        if (act == null) return List.of();
        if (act.getDocumentosRequeridos() != null && !act.getDocumentosRequeridos().isEmpty()) {
            return act.getDocumentosRequeridos();
        }
        if (act.getDocumentoIds() == null) return List.of();
        return act.getDocumentoIds().stream()
                .map(id -> new RequisitoDocumento(id, RequisitoDocumento.CLIENTE, true))
                .toList();
    }

    /** Requisitos obligatorios que debe aportar el CLIENTE en esta actividad (explícitos). */
    public List<RequisitoDocumento> requisitosObligatoriosCliente(Actividad act) {
        if (act == null || act.getDocumentosRequeridos() == null) return List.of();
        return act.getDocumentosRequeridos().stream()
                .filter(r -> r.isObligatorio()
                        && RequisitoDocumento.CLIENTE.equalsIgnoreCase(r.getProveedor()))
                .toList();
    }

    /**
     * documentoIds de los requisitos obligatorios del cliente que AÚN no están cubiertos
     * por un documento subido (enlazado por {@code documentoRequeridoId}) en este trámite/actividad.
     */
    public List<String> documentosFaltantesCliente(String tramiteId, String actividadId) {
        if (tramiteId == null || actividadId == null) return List.of();
        Actividad act = actividadRepository.findById(actividadId).orElse(null);
        List<String> obligatorios = requisitosObligatoriosCliente(act).stream()
                .map(RequisitoDocumento::getDocumentoId)
                .filter(Objects::nonNull)
                .toList();
        if (obligatorios.isEmpty()) return List.of();

        // NO-REDUNDANCIA: un requisito se considera cubierto si el cliente ya subió ese
        // documento en CUALQUIER actividad de ESTE trámite (no solo en la actual). Así, si
        // varias actividades piden la misma Cédula, no se le vuelve a pedir.
        Set<String> cubiertos = docRepo
                .findByTramiteIdAndActivoTrue(tramiteId).stream()
                .map(DocumentoArchivo::getDocumentoRequeridoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return obligatorios.stream().filter(id -> !cubiertos.contains(id)).toList();
    }

    /** ¿Faltan requisitos obligatorios del cliente en este paso? (false si la actividad no los configura). */
    public boolean faltanObligatoriosCliente(String tramiteId, String actividadId) {
        return !documentosFaltantesCliente(tramiteId, actividadId).isEmpty();
    }
}
