package com.example.demo.controllers;

import com.example.demo.dto.HistorialTramiteResponse;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.Tramite;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tramites")
public class HistorialTramiteController {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private PoliticaNegocioRepository politicaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping("/historial")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<HistorialTramiteResponse>> obtenerHistorialAdministrativo(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String departamentoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        List<Tramite> tramites = tramiteRepository.findAll();

        if (estado != null && !estado.isBlank()) {
            tramites.removeIf(t -> t.getEstadoActual() == null || !estado.equalsIgnoreCase(t.getEstadoActual()));
        }

        if (desde != null) {
            tramites.removeIf(t -> t.getFechaInicio() == null || t.getFechaInicio().toLocalDate().isBefore(desde));
        }

        if (hasta != null) {
            tramites.removeIf(t -> t.getFechaInicio() == null || t.getFechaInicio().toLocalDate().isAfter(hasta));
        }

        List<HistorialTramiteResponse> response = tramites.stream()
                .map(this::toHistorialResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    private HistorialTramiteResponse toHistorialResponse(Tramite tramite) {
        String politicaNombre = obtenerPoliticaNombre(tramite.getPoliticaId());
        String clienteNombre = obtenerClienteNombre(tramite.getClienteId());

        return new HistorialTramiteResponse(
                tramite.getId(),
                tramite.getCodigo(),
                tramite.getClienteId(),
                clienteNombre,
                tramite.getPoliticaId(),
                politicaNombre,
                tramite.getEstadoActual(),
                tramite.getFechaInicio(),
                tramite.getFechaCierreReal()
        );
    }

    private String obtenerPoliticaNombre(String politicaId) {
        if (politicaId == null || politicaId.isBlank()) {
            return "No especificada";
        }

        Optional<PoliticaNegocio> politica = politicaRepository.findById(politicaId);
        return politica.map(PoliticaNegocio::getNombre).orElse("No especificada");
    }

    private String obtenerClienteNombre(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            return "Desconocido";
        }

        Optional<Usuario> usuario = usuarioRepository.findById(clienteId);
        return usuario
                .map(u -> String.format("%s %s",
                        Optional.ofNullable(u.getNombre()).orElse(""),
                        Optional.ofNullable(u.getApellido()).orElse("")).trim())
                .filter(nombre -> !nombre.isBlank())
                .orElse("Desconocido");
    }
}
