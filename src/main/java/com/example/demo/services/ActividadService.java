package com.example.demo.services;

import com.example.demo.dto.ActividadRequest;
import com.example.demo.models.Actividad;
import com.example.demo.models.Departamento;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DepartamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class ActividadService {

    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DepartamentoRepository departamentoRepository;

    public Actividad crear(ActividadRequest req) {
        Departamento depto = departamentoRepository.findById(req.getDepartamentoId())
                .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));

        if (!depto.isActivo()) {
            throw new IllegalArgumentException("No se pueden crear actividades en un departamento inactivo");
        }

        Actividad a = new Actividad();
        a.setNombre(req.getNombre());
        a.setDescripcion(req.getDescripcion());
        a.setDepartamentoId(req.getDepartamentoId());
        a.setFuncionarioResponsableId(req.getFuncionarioResponsableId());
        a.setSlaHoras(req.getSlaHoras());
        a.setSalidasPosibles(normalizar(req.getSalidasPosibles()));
        a.setCamposRequeridos(req.getCamposRequeridos());
        a.setDocumentoIds(req.getDocumentoIds());
        a.setDocumentosRequeridos(req.getDocumentosRequeridos());
        a.setReutilizable(req.isReutilizable());
        a.setFechaCreacion(LocalDateTime.now());

        return actividadRepository.save(a);
    }

    public List<Actividad> listarTodas() {
        return actividadRepository.findAll();
    }

    public List<Actividad> listarPorDepartamento(String departamentoId) {
        return actividadRepository.findByDepartamentoId(departamentoId);
    }

    public List<Actividad> listarReutilizables() {
        return actividadRepository.findByReutilizableTrue();
    }

    public Optional<Actividad> buscarPorId(String id) {
        return actividadRepository.findById(id);
    }

    public Actividad actualizar(String id, ActividadRequest req) {
        Actividad a = actividadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Actividad no encontrada"));

        if (!req.getDepartamentoId().equals(a.getDepartamentoId())) {
            Departamento depto = departamentoRepository.findById(req.getDepartamentoId())
                    .orElseThrow(() -> new IllegalArgumentException("Departamento destino no encontrado"));
            if (!depto.isActivo()) {
                throw new IllegalArgumentException("El departamento destino está inactivo");
            }
        }

        a.setNombre(req.getNombre());
        a.setDescripcion(req.getDescripcion());
        a.setDepartamentoId(req.getDepartamentoId());
        a.setFuncionarioResponsableId(req.getFuncionarioResponsableId());
        a.setSlaHoras(req.getSlaHoras());
        a.setSalidasPosibles(normalizar(req.getSalidasPosibles()));
        a.setCamposRequeridos(req.getCamposRequeridos());
        a.setDocumentoIds(req.getDocumentoIds());
        a.setDocumentosRequeridos(req.getDocumentosRequeridos());
        a.setReutilizable(req.isReutilizable());

        return actividadRepository.save(a);
    }

    private List<String> normalizar(List<String> salidas) {
        if (salidas == null || salidas.isEmpty()) {
            return new ArrayList<>(List.of("completar"));
        }
        return new ArrayList<>(new LinkedHashSet<>(salidas));
    }

    public void eliminar(String id) {
        if (!actividadRepository.existsById(id)) {
            throw new IllegalArgumentException("Actividad no encontrada");
        }
        actividadRepository.deleteById(id);
    }
}
