package com.example.demo.services;

import com.example.demo.dto.HitoDTO;
import com.example.demo.dto.LineaTiempoResponse;
import com.example.demo.models.EstadoHistorico;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.EstadoHistoricoRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TramiteCicloVidaService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private EstadoHistoricoRepository estadoHistoricoRepository;

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

        if ("Aprobado".equals(tramite.getEstadoActual()) || "Rechazado".equals(tramite.getEstadoActual())) {
            throw new IllegalArgumentException("El tramite ya se encuentra en un estado final y no puede ser cancelado");
        }

        // Guardar el funcionario antes de limpiar la asignacion.
        String funcionarioAsignadoId = tramite.getFuncionarioActualId();

        tramite.setEstadoActual("Cancelado por el usuario");
        tramite.setFuncionarioActualId(null);
        tramite.setNodoActualId(null);
        tramite.setNodosParalellosActivos(List.of());
        tramite = tramiteRepository.save(tramite);

        trazabilidadService.registrar(tramiteId, clienteId, "cancelar", null,
                Map.of("motivo", "Cancelado por el cliente"));

        // CU-19: notificar al funcionario que tenia el tramite asignado.
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

        List<HitoDTO> hitos = new ArrayList<>();
        for (EstadoHistorico hist : historial) {
            HitoDTO hito = new HitoDTO();
            hito.setFecha(hist.getFechaCambio());
            hito.setEstado(hist.getEstadoNuevo());
            hito.setDepartamento("Departamento Asociado");
            hito.setActor(hist.getActorId() != null ? hist.getActorId() : "Sistema");
            String nodoHito = hist.getNodoNuevoId();
            hito.setEsActual(nodoHito != null && nodoHito.equals(tramite.getNodoActualId()));
            hitos.add(hito);
        }
        response.setHitos(hitos);

        return response;
    }
}
