package com.example.demo.services;

import com.example.demo.dto.FormularioPlantillaRequest;
import com.example.demo.models.CampoPlantilla;
import com.example.demo.models.ColaboracionDiagrama;
import com.example.demo.models.DiagramaWorkflow;
import com.example.demo.models.FormularioPlantilla;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.CampoPlantillaRepository;
import com.example.demo.repositories.ColaboracionDiagramaRepository;
import com.example.demo.repositories.DiagramaWorkflowRepository;
import com.example.demo.repositories.FormularioPlantillaRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class FormularioPlantillaService {

    @Autowired private FormularioPlantillaRepository formularioRepository;
    @Autowired private CampoPlantillaRepository campoRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private ColaboracionDiagramaRepository colaboracionRepository;

    public FormularioPlantilla crearParaNodo(String nodoId, FormularioPlantillaRequest req,
                                              String usuarioId) {
        NodoDiagrama nodo = nodoRequerido(nodoId);

        if (!"actividad".equals(nodo.getTipo())) {
            throw new IllegalArgumentException(
                    "Solo los nodos de tipo 'actividad' admiten formulario");
        }

        DiagramaWorkflow diagrama = diagramaRequerido(nodo.getDiagramaId());
        validarPermisoEdicion(diagrama, usuarioId);

        if (!formularioRepository.findByNodoId(nodoId).isEmpty()) {
            throw new IllegalArgumentException(
                    "El nodo ya tiene un formulario. Edita el existente o elimínalo primero");
        }

        FormularioPlantilla f = new FormularioPlantilla();
        f.setNodoId(nodoId);
        f.setNombre(req.getNombre());
        f.setCamposPlantillaIds(new ArrayList<>());
        f.setPermiteAdjuntos(req.isPermiteAdjuntos());
        f.setPermiteDictadoVoz(req.isPermiteDictadoVoz());
        FormularioPlantilla guardado = formularioRepository.save(f);

        nodo.setFormularioPlantillaId(guardado.getId());
        nodoRepository.save(nodo);

        return guardado;
    }

    public Optional<FormularioPlantilla> obtenerPorNodo(String nodoId) {
        List<FormularioPlantilla> lista = formularioRepository.findByNodoId(nodoId);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    public Optional<FormularioPlantilla> obtenerPorId(String formularioId) {
        return formularioRepository.findById(formularioId);
    }

    public List<CampoPlantilla> listarCampos(String formularioId) {
        formularioRepository.findById(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado"));
        return campoRepository.findByFormularioPlantillaId(formularioId).stream()
                .sorted(Comparator.comparingInt(CampoPlantilla::getOrden))
                .toList();
    }

    public FormularioPlantilla actualizar(String formularioId, FormularioPlantillaRequest req,
                                           String usuarioId) {
        FormularioPlantilla f = formularioRepository.findById(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado"));

        DiagramaWorkflow diagrama = diagramaDelFormulario(f);
        validarPermisoEdicion(diagrama, usuarioId);

        f.setNombre(req.getNombre());
        f.setPermiteAdjuntos(req.isPermiteAdjuntos());
        f.setPermiteDictadoVoz(req.isPermiteDictadoVoz());
        return formularioRepository.save(f);
    }

    public void eliminar(String formularioId, String usuarioId) {
        FormularioPlantilla f = formularioRepository.findById(formularioId)
                .orElseThrow(() -> new IllegalArgumentException("Formulario no encontrado"));

        DiagramaWorkflow diagrama = diagramaDelFormulario(f);
        validarPermisoEdicion(diagrama, usuarioId);

        campoRepository.findByFormularioPlantillaId(formularioId)
                .forEach(c -> campoRepository.deleteById(c.getId()));

        nodoRepository.findById(f.getNodoId()).ifPresent(n -> {
            n.setFormularioPlantillaId(null);
            nodoRepository.save(n);
        });

        formularioRepository.deleteById(formularioId);
    }

    DiagramaWorkflow diagramaDelFormulario(FormularioPlantilla f) {
        NodoDiagrama nodo = nodoRequerido(f.getNodoId());
        return diagramaRequerido(nodo.getDiagramaId());
    }

    void validarPermisoEdicion(DiagramaWorkflow diagrama, String usuarioId) {
        if (!"borrador".equals(diagrama.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede diseñar el formulario en un diagrama en estado 'borrador'");
        }
        if (usuarioId == null) {
            throw new AccessDeniedException("Sesión no válida");
        }
        if (usuarioId.equals(diagrama.getCreadorId())) {
            return;
        }
        boolean editorAceptado = colaboracionRepository.findByDiagramaId(diagrama.getId()).stream()
                .anyMatch(c -> usuarioId.equals(c.getInvitadoId())
                        && "editor".equalsIgnoreCase(c.getRolColaboracion())
                        && "aceptada".equalsIgnoreCase(c.getEstado()));
        if (!editorAceptado) {
            throw new AccessDeniedException(
                    "Solo el creador del diagrama o un colaborador con permiso de editor puede modificar el formulario");
        }
    }

    private NodoDiagrama nodoRequerido(String nodoId) {
        return nodoRepository.findById(nodoId)
                .orElseThrow(() -> new IllegalArgumentException("Nodo no encontrado"));
    }

    private DiagramaWorkflow diagramaRequerido(String diagramaId) {
        return diagramaRepository.findById(diagramaId)
                .orElseThrow(() -> new IllegalStateException("Diagrama del nodo no existe"));
    }
}
