package com.example.demo.services;

import com.example.demo.dto.CampoValorDto;
import com.example.demo.dto.CompletarNodoRequest;
import com.example.demo.dto.CompletarSeccionRequest;
import com.example.demo.dto.GuardarSeccionRequest;
import com.example.demo.models.ExpedienteDigital;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.CampoSeccionRepository;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.ExpedienteDigitalRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
            secMap.put("campos", campoRepository.findBySeccionId(seccion.getId()));
            if (seccion.getDepartamentoId() != null) {
                departamentoRepository.findById(seccion.getDepartamentoId())
                        .ifPresent(d -> secMap.put("departamentoNombre", d.getCodigo() + " · " + d.getNombre()));
            }
            return secMap;
        }).toList();

        response.put("secciones", seccionesCompletas);
        return response;
    }

    public SeccionExpediente guardarSeccion(String seccionId, GuardarSeccionRequest request, String funcionarioId) {
        SeccionExpediente seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new IllegalArgumentException("Seccion no encontrada"));

        if (!"en_curso".equals(seccion.getEstado())) {
            throw new IllegalStateException("Solo se pueden editar secciones en estado en_curso");
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
        return seccionRepository.save(seccion);
    }

    public Tramite completarSeccionYAvanzar(String seccionId, CompletarSeccionRequest request, String funcionarioId) {
        SeccionExpediente seccion = seccionRepository.findById(seccionId)
                .orElseThrow(() -> new IllegalArgumentException("Seccion no encontrada"));

        seccion.setEstado("completada");
        seccion.setFuncionarioId(funcionarioId);
        seccion.setFechaCompletado(LocalDateTime.now());
        seccionRepository.save(seccion);

        ExpedienteDigital exp = expedienteRepository.findById(seccion.getExpedienteId())
                .orElseThrow(() -> new IllegalStateException("Expediente no encontrado"));
        Tramite tramite = tramiteRepository.findById(exp.getTramiteId())
                .orElseThrow(() -> new IllegalStateException("Tramite no encontrado"));

        CompletarNodoRequest engineRequest = new CompletarNodoRequest();
        engineRequest.setFuncionarioId(funcionarioId);
        engineRequest.setDecision(request.getDecisionTomada());
        engineRequest.setNotas(request.getNotasOperativas());

        return workflowEngineService.completarNodo(tramite.getId(), engineRequest);
    }
}
