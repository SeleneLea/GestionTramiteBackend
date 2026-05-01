package com.example.demo.services;

import com.example.demo.dto.CampoPlantillaRequest;
import com.example.demo.models.CampoPlantilla;
import com.example.demo.models.FormularioPlantilla;
import com.example.demo.repositories.CampoPlantillaRepository;
import com.example.demo.repositories.FormularioPlantillaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CampoPlantillaService {

    private static final Set<String> TIPOS_CON_OPCIONES = Set.of("select", "radio");

    @Autowired private CampoPlantillaRepository campoRepository;
    @Autowired private FormularioPlantillaRepository formularioRepository;
    @Autowired private FormularioPlantillaService formularioService;

    public CampoPlantilla agregar(String formularioId, CampoPlantillaRequest req, String usuarioId) {
        FormularioPlantilla form = formularioRepository.findById(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado"));

        formularioService.validarPermisoEdicion(
                formularioService.diagramaDelFormulario(form), usuarioId);

        validarReglasCampo(req);

        boolean duplicado = campoRepository.findByFormularioPlantillaId(formularioId).stream()
                .anyMatch(c -> c.getNombre().equalsIgnoreCase(req.getNombre()));
        if (duplicado) {
            throw new IllegalArgumentException(
                    "Ya existe un campo con el nombre '" + req.getNombre() + "' en este formulario");
        }

        CampoPlantilla c = new CampoPlantilla();
        c.setFormularioPlantillaId(formularioId);
        copiar(req, c);
        CampoPlantilla guardado = campoRepository.save(c);

        if (form.getCamposPlantillaIds() == null) {
            form.setCamposPlantillaIds(new ArrayList<>());
        }
        form.getCamposPlantillaIds().add(guardado.getId());
        formularioRepository.save(form);

        return guardado;
    }

    public CampoPlantilla actualizar(String campoId, CampoPlantillaRequest req, String usuarioId) {
        CampoPlantilla c = campoRepository.findById(campoId)
                .orElseThrow(() -> new IllegalArgumentException("Campo no encontrado"));

        FormularioPlantilla form = formularioRepository.findById(c.getFormularioPlantillaId())
                .orElseThrow(() -> new IllegalStateException("Formulario del campo no existe"));

        formularioService.validarPermisoEdicion(
                formularioService.diagramaDelFormulario(form), usuarioId);

        validarReglasCampo(req);

        boolean duplicado = campoRepository.findByFormularioPlantillaId(c.getFormularioPlantillaId()).stream()
                .anyMatch(other -> !other.getId().equals(campoId)
                        && other.getNombre().equalsIgnoreCase(req.getNombre()));
        if (duplicado) {
            throw new IllegalArgumentException(
                    "Ya existe un campo con el nombre '" + req.getNombre() + "' en este formulario");
        }

        copiar(req, c);
        return campoRepository.save(c);
    }

    public void eliminar(String campoId, String usuarioId) {
        CampoPlantilla c = campoRepository.findById(campoId)
                .orElseThrow(() -> new IllegalArgumentException("Campo no encontrado"));

        FormularioPlantilla form = formularioRepository.findById(c.getFormularioPlantillaId())
                .orElseThrow(() -> new IllegalStateException("Formulario del campo no existe"));

        formularioService.validarPermisoEdicion(
                formularioService.diagramaDelFormulario(form), usuarioId);

        campoRepository.deleteById(campoId);

        if (form.getCamposPlantillaIds() != null) {
            form.getCamposPlantillaIds().remove(campoId);
            formularioRepository.save(form);
        }
    }

    public List<CampoPlantilla> reordenar(String formularioId, List<String> idsEnOrden,
                                           String usuarioId) {
        FormularioPlantilla form = formularioRepository.findById(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado"));

        formularioService.validarPermisoEdicion(
                formularioService.diagramaDelFormulario(form), usuarioId);

        List<CampoPlantilla> campos = campoRepository.findByFormularioPlantillaId(formularioId);
        if (idsEnOrden == null || idsEnOrden.size() != campos.size()) {
            throw new IllegalArgumentException(
                    "Debe enviar exactamente los IDs de todos los campos del formulario");
        }

        for (int i = 0; i < idsEnOrden.size(); i++) {
            String campoId = idsEnOrden.get(i);
            CampoPlantilla c = campos.stream()
                    .filter(x -> x.getId().equals(campoId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El campo " + campoId + " no pertenece al formulario"));
            c.setOrden(i + 1);
            campoRepository.save(c);
        }

        form.setCamposPlantillaIds(new ArrayList<>(idsEnOrden));
        formularioRepository.save(form);

        return campoRepository.findByFormularioPlantillaId(formularioId).stream()
                .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
                .toList();
    }

    private void validarReglasCampo(CampoPlantillaRequest req) {
        boolean requiereOpciones = TIPOS_CON_OPCIONES.contains(req.getTipo());
        boolean tieneOpciones = req.getOpciones() != null && !req.getOpciones().isEmpty();

        if (requiereOpciones && !tieneOpciones) {
            throw new IllegalArgumentException(
                    "Los campos de tipo '" + req.getTipo() + "' requieren al menos una opcion");
        }
        if (!requiereOpciones && tieneOpciones) {
            throw new IllegalArgumentException(
                    "El tipo '" + req.getTipo() + "' no admite lista de opciones");
        }
    }

    private void copiar(CampoPlantillaRequest req, CampoPlantilla c) {
        c.setNombre(req.getNombre());
        c.setEtiqueta(req.getEtiqueta());
        c.setTipo(req.getTipo());
        c.setObligatorio(req.isObligatorio());
        c.setOpciones(req.getOpciones());
        c.setValidacionRegex(req.getValidacionRegex());
        c.setOrden(req.getOrden());
    }
}
