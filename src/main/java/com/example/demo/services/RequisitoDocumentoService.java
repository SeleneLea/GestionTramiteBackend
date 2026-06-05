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

@Service
public class RequisitoDocumentoService {

    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DocumentoArchivoRepository docRepo;

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

    public List<RequisitoDocumento> requisitosObligatoriosCliente(Actividad act) {
        if (act == null || act.getDocumentosRequeridos() == null) return List.of();
        return act.getDocumentosRequeridos().stream()
                .filter(r -> r.isObligatorio()
                        && RequisitoDocumento.CLIENTE.equalsIgnoreCase(r.getProveedor()))
                .toList();
    }

    public List<String> documentosFaltantesCliente(String tramiteId, String actividadId) {
        if (tramiteId == null || actividadId == null) return List.of();
        Actividad act = actividadRepository.findById(actividadId).orElse(null);
        List<String> obligatorios = requisitosObligatoriosCliente(act).stream()
                .map(RequisitoDocumento::getDocumentoId)
                .filter(Objects::nonNull)
                .toList();
        if (obligatorios.isEmpty()) return List.of();

        Set<String> cubiertos = docRepo
                .findByTramiteIdAndActivoTrue(tramiteId).stream()
                .map(DocumentoArchivo::getDocumentoRequeridoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return obligatorios.stream().filter(id -> !cubiertos.contains(id)).toList();
    }

    public boolean faltanObligatoriosCliente(String tramiteId, String actividadId) {
        return !documentosFaltantesCliente(tramiteId, actividadId).isEmpty();
    }
}
