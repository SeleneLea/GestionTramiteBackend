package com.example.demo.services;

import com.example.demo.dto.HitoDTO;
import com.example.demo.dto.LineaTiempoResponse;
import com.example.demo.models.Departamento;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.EstadoSeccion;
import com.example.demo.models.EstadoTramite;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.ExpedienteDigitalRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TramiteCicloVidaService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private EstadoHistoricoRepository estadoHistoricoRepository;

    @Autowired
    private ExpedienteDigitalRepository expedienteDigitalRepository;

    @Autowired
    private SeccionExpedienteRepository seccionExpedienteRepository;

    @Autowired
    private NodoDiagramaRepository nodoDiagramaRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private TrazabilidadService trazabilidadService;

    @Autowired
    private NotificacionService notificacionService;

    public Tramite cancelarTramite(String tramiteId, String clienteId) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado"));

        if (!clienteId.equals(tramite.getClienteId())) {
            throw new IllegalArgumentException("No tiene permisos para cancelar este tramite");
        }

        if (EstadoTramite.esFinalizado(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El tramite ya se encuentra en un estado final y no puede ser cancelado");
        }

        String funcionarioAsignadoId = tramite.getFuncionarioActualId();

        expedienteDigitalRepository.findByTramiteId(tramiteId).ifPresent(expediente -> {
            LocalDateTime ahora = LocalDateTime.now();
            for (SeccionExpediente seccion : seccionExpedienteRepository.findByExpedienteId(expediente.getId())) {
                if (EstadoSeccion.esActivaParaTrabajo(seccion.getEstado())) {
                    seccion.setEstado(EstadoSeccion.DERIVADA.getValor());
                    seccion.setFechaCompletado(ahora);
                    seccionExpedienteRepository.save(seccion);
                }
            }
        });

        tramite.setEstadoActual(EstadoTramite.CANCELADO.getValor());
        tramite.setFuncionarioActualId(null);
        tramite.setNodoActualId(null);
        tramite.setNodosParalellosActivos(List.of());
        tramite.setFechaCierreReal(LocalDateTime.now());
        tramite = tramiteRepository.save(tramite);

        trazabilidadService.registrar(tramiteId, clienteId, "cancelar", null,
                Map.of("motivo", "Cancelado por el cliente"));

        if (funcionarioAsignadoId != null) {
            notificacionService.crearNotificacion(
                    funcionarioAsignadoId,
                    tramite.getId(),
                    "cambio_estado",
                    "Tramite cancelado por el cliente",
                    "El tramite " + tramite.getCodigo() + " fue cancelado por su solicitante y ha sido retirado de tu bandeja.",
                    "web"
            );
        }

        return tramite;
    }

    public LineaTiempoResponse getLineaTiempo(String tramiteId) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException("Tramite no encontrado"));

        List<EstadoHistorico> historial = estadoHistoricoRepository.findByTramiteIdOrderByFechaCambioAsc(tramiteId);

        LineaTiempoResponse response = new LineaTiempoResponse();
        response.setTramiteId(tramiteId);
        response.setEstadoActual(tramite.getEstadoActual());

        boolean tramiteFinalizado = EstadoTramite.esFinalizado(tramite.getEstadoActual());
        List<String> nodosParalelos = tramite.getNodosParalellosActivos();

        Map<String, String> departamentoPorNodo = new HashMap<>();

        List<HitoDTO> hitos = new ArrayList<>();
        for (EstadoHistorico hist : historial) {
            HitoDTO hito = new HitoDTO();
            hito.setFecha(hist.getFechaCambio());
            hito.setEstado(hist.getEstadoNuevo());
            String nodoHito = hist.getNodoNuevoId();
            hito.setDepartamento(resolverDepartamento(nodoHito, departamentoPorNodo));
            hito.setActor(hist.getActorId() != null ? hist.getActorId() : "Sistema");
            boolean esActual = !tramiteFinalizado && nodoHito != null
                    && (nodoHito.equals(tramite.getNodoActualId())
                        || (nodosParalelos != null && nodosParalelos.contains(nodoHito)));
            hito.setEsActual(esActual);
            hitos.add(hito);
        }
        response.setHitos(hitos);

        return response;
    }

    private String resolverDepartamento(String nodoId, Map<String, String> cache) {
        if (nodoId == null) {
            return "Departamento Asociado";
        }
        if (cache.containsKey(nodoId)) {
            return cache.get(nodoId);
        }
        String nombre = "Departamento Asociado";
        NodoDiagrama nodo = nodoDiagramaRepository.findById(nodoId).orElse(null);
        if (nodo != null && nodo.getDepartamentoId() != null) {
            Departamento depto = departamentoRepository.findById(nodo.getDepartamentoId()).orElse(null);
            if (depto != null && depto.getNombre() != null) {
                nombre = depto.getNombre();
            }
        }
        cache.put(nodoId, nombre);
        return nombre;
    }
}
