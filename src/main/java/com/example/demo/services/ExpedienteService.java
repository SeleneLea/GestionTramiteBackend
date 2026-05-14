package com.example.demo.services;

import com.example.demo.dto.CampoValorDto;
import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.CompletarSeccionRequest;
import com.example.demo.dto.GuardarSeccionRequest;
import com.example.demo.models.CampoPlantilla;
import com.example.demo.models.CampoSeccion;
import com.example.demo.models.EstadoSeccion;
import com.example.demo.models.EstadoTramite;
import com.example.demo.models.ExpedienteDigital;
import com.example.demo.models.FormularioPlantilla;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.CampoPlantillaRepository;
import com.example.demo.repositories.CampoSeccionRepository;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.ExpedienteDigitalRepository;
import com.example.demo.repositories.FormularioPlantillaRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpedienteService {

    @Autowired
    private ExpedienteDigitalRepository expedienteRepository;

    @Autowired
    private SeccionExpedienteRepository seccionRepository;

    @Autowired
    private CampoSeccionRepository campoRepository;

    @Autowired
    private FormularioPlantillaRepository formularioRepository;

    @Autowired
    private CampoPlantillaRepository campoPlantillaRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private WorkflowEngineService workflowEngineService;

    public Map<String, Object> obtenerExpedienteCompleto(String tramiteId) {
        ExpedienteDigital expediente = expedienteRepository.findByTramiteId(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Expediente no encontrado para el tramite: " + tramiteId));

        List<SeccionExpediente> secciones = seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(expediente.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("expediente", expediente);

        List<Map<String, Object>> seccionesCompletas = secciones.stream().map(seccion -> {
            Map<String, Object> secMap = new HashMap<>();
            secMap.put("infoSeccion", seccion);

            // Si la sección está en_curso y aún no se han instanciado los CampoSeccion,
            // copiamos la plantilla del nodo a campos con valor vacío para que el
            // funcionario sepa visualmente qué tiene que llenar (CU-13c).
            List<CampoSeccion> campos = campoRepository.findBySeccionId(seccion.getId());
            if (campos.isEmpty() && EstadoSeccion.esActivaParaTrabajo(seccion.getEstado())) {
                campos = instanciarCamposDesdeNodo(seccion);
            }
            secMap.put("campos", enriquecerConPlantilla(campos));

            if (seccion.getDepartamentoId() != null) {
                departamentoRepository.findById(seccion.getDepartamentoId())
                        .ifPresent(d -> secMap.put("departamentoNombre", d.getCodigo() + " · " + d.getNombre()));
            }
            return secMap;
        }).toList();

        response.put("secciones", seccionesCompletas);
        return response;
    }

    public SeccionExpediente guardarSeccion(String seccionId, GuardarSeccionRequest request,
                                            String funcionarioId, boolean esAdmin) {
        SeccionExpediente seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new IllegalArgumentException("Seccion no encontrada"));

        // Guard de trámite finalizado (WF-07): no se puede editar un expediente
        // cuyo trámite ya está cerrado. Cargamos el trámite vía expediente.
        ExpedienteDigital exp = expedienteRepository.findById(seccion.getExpedienteId())
                .orElseThrow(() -> new IllegalStateException("Expediente no encontrado"));
        Tramite tramite = tramiteRepository.findById(exp.getTramiteId())
                .orElseThrow(() -> new IllegalStateException("Tramite no encontrado"));

        // Autorización (SEC): solo el funcionario asignado al nodo ACTUAL puede
        // editar esta sección. Admin tiene override. Validar ANTES de mutar.
        validarAutorizacionNodoActual(seccion, tramite, funcionarioId, esAdmin);

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalStateException("El tramite ya esta cerrado");
        }

        if (!EstadoSeccion.esActivaParaTrabajo(seccion.getEstado())) {
            throw new IllegalStateException("Solo se pueden editar secciones recibidas o en ejecución");
        }

        if (request.getCampos() != null) {
            for (CampoValorDto cv : request.getCampos()) {
                campoRepository.findById(cv.getCampoId()).ifPresent(campo -> {
                    if (campo.getSeccionId().equals(seccionId)) {
                        campo.setValor(cv.getValor());
                        campo.setFechaGuardado(LocalDateTime.now());
                        campoRepository.save(campo);
                    }
                });
            }
        }

        seccion.setFuncionarioId(funcionarioId);
        // Editar implica tomar la sección: Pendiente de recepción / Observado → En ejecución.
        EstadoSeccion estadoSec = EstadoSeccion.from(seccion.getEstado());
        if (estadoSec == EstadoSeccion.PENDIENTE_RECEPCION || estadoSec == EstadoSeccion.OBSERVADO) {
            seccion.setEstado(EstadoSeccion.EN_EJECUCION.getValor());
        }
        return seccionRepository.save(seccion);
    }

    /**
     * Fusiona CampoSeccion (id, valor, fechaGuardado) con la metadata del
     * CampoPlantilla (etiqueta, opciones, obligatorio, validacionRegex)
     * para que el front pueda renderizar inputs con texto amigable y
     * validar sin hacer una segunda llamada.
     */
    private List<Map<String, Object>> enriquecerConPlantilla(List<CampoSeccion> campos) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CampoSeccion c : campos) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.getId());
            row.put("seccionId", c.getSeccionId());
            row.put("campoPlantillaId", c.getCampoPlantillaId());
            row.put("nombre", c.getNombre());
            row.put("tipo", c.getTipo());
            row.put("valor", c.getValor());
            row.put("fueDictado", c.isFueDictado());
            row.put("fechaGuardado", c.getFechaGuardado());

            // Metadata de la plantilla (puede no existir si el campo se creó
            // huérfano, por eso es defensivo).
            if (c.getCampoPlantillaId() != null) {
                campoPlantillaRepository.findById(c.getCampoPlantillaId()).ifPresent(cp -> {
                    row.put("etiqueta", cp.getEtiqueta());
                    row.put("obligatorio", cp.isObligatorio());
                    row.put("opciones", cp.getOpciones());
                    row.put("validacionRegex", cp.getValidacionRegex());
                    row.put("orden", cp.getOrden());
                });
            }
            out.add(row);
        }
        // Ordenar por "orden" de la plantilla (los que no tienen orden, al final).
        out.sort((a, b) -> {
            Integer oa = (Integer) a.get("orden");
            Integer ob = (Integer) b.get("orden");
            if (oa == null && ob == null) return 0;
            if (oa == null) return 1;
            if (ob == null) return -1;
            return Integer.compare(oa, ob);
        });
        return out;
    }

    /**
     * Cuando una sección recién entra en_curso, normalmente no tiene
     * CampoSeccion creados. Para que el funcionario vea visualmente qué
     * tiene que rellenar, copiamos los campos de la plantilla del nodo
     * con valor vacío. Idempotente: si ya hay campos, no hace nada.
     */
    private List<CampoSeccion> instanciarCamposDesdeNodo(SeccionExpediente seccion) {
        String nodoId = seccion.getNodoId();
        if (nodoId == null) return new ArrayList<>();

        List<FormularioPlantilla> formularios = formularioRepository.findByNodoId(nodoId);
        if (formularios.isEmpty()) return new ArrayList<>();

        FormularioPlantilla form = formularios.get(0);
        List<CampoPlantilla> plantilla = campoPlantillaRepository
                .findByFormularioPlantillaId(form.getId());
        plantilla.sort((a, b) -> Integer.compare(a.getOrden(), b.getOrden()));

        List<CampoSeccion> nuevos = new ArrayList<>();
        for (CampoPlantilla cp : plantilla) {
            CampoSeccion cs = new CampoSeccion();
            cs.setSeccionId(seccion.getId());
            cs.setCampoPlantillaId(cp.getId());
            cs.setNombre(cp.getNombre());
            cs.setTipo(cp.getTipo());
            cs.setValor("");
            cs.setFueDictado(false);
            cs.setFechaGuardado(LocalDateTime.now());
            nuevos.add(campoRepository.save(cs));
        }
        return nuevos;
    }

    public Tramite completarSeccionYAvanzar(String seccionId, CompletarSeccionRequest request,
                                            String funcionarioId, boolean esAdmin) {
        SeccionExpediente seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new IllegalArgumentException("Seccion no encontrada"));

        // Validar ANTES de mutar la sección (WF-07): cargar expediente + trámite
        // y rechazar si el trámite ya está cerrado o la sección no es trabajable.
        ExpedienteDigital exp = expedienteRepository.findById(seccion.getExpedienteId())
                .orElseThrow(() -> new IllegalStateException("Expediente no encontrado"));
        Tramite tramite = tramiteRepository.findById(exp.getTramiteId())
                .orElseThrow(() -> new IllegalStateException("Tramite no encontrado"));

        // Autorización (SEC): solo el funcionario asignado al nodo ACTUAL puede
        // completar/derivar esta sección. Admin tiene override. Validar ANTES de mutar.
        validarAutorizacionNodoActual(seccion, tramite, funcionarioId, esAdmin);

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalStateException("El tramite ya esta cerrado");
        }

        if (!EstadoSeccion.esActivaParaTrabajo(seccion.getEstado())) {
            throw new IllegalStateException("Solo se pueden completar secciones recibidas o en ejecución");
        }

        // Validaciones superadas: recién ahora mutamos la sección a DERIVADA.
        seccion.setEstado(EstadoSeccion.DERIVADA.getValor());
        seccion.setFuncionarioId(funcionarioId);
        seccion.setFechaCompletado(LocalDateTime.now());
        seccionRepository.save(seccion);

        CompletarNodoRequest engineRequest = new CompletarNodoRequest();
        engineRequest.setFuncionarioId(funcionarioId);
        engineRequest.setDecision(request.getDecisionTomada());
        engineRequest.setNotas(request.getNotasOperativas());

        return workflowEngineService.completarNodo(tramite.getId(), engineRequest);
    }

    /**
     * Autorización a nivel de nodo (SEC): un funcionario solo puede editar/completar
     * una sección si ésta pertenece al nodo ACTUAL del trámite y si él es el
     * funcionario asignado a ese nodo (o si el nodo aún no tiene funcionario asignado).
     * Los administradores (esAdmin) saltan toda la validación (override).
     *
     * @throws AccessDeniedException si el funcionario no está autorizado.
     */
    public static void validarAutorizacionNodoActual(SeccionExpediente seccion, Tramite tramite,
                                                      String funcionarioId, boolean esAdmin) {
        if (esAdmin) {
            return; // ADMINISTRADOR: override total.
        }

        // a) La sección debe pertenecer al nodo ACTUAL del trámite.
        if (seccion.getNodoId() == null
                || !seccion.getNodoId().equals(tramite.getNodoActualId())) {
            throw new AccessDeniedException(
                    "Solo se pueden editar secciones del nodo actual del tramite");
        }

        // b) El nodo debe estar sin asignar O asignado al funcionario que pide.
        String asignado = tramite.getFuncionarioActualId();
        if (asignado != null && !asignado.equals(funcionarioId)) {
            throw new AccessDeniedException(
                    "Solo el funcionario asignado al nodo actual puede editar esta seccion");
        }
    }
}
