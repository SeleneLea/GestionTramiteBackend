package com.example.demo.services;

import com.example.demo.models.RepositorioDocumental;
import com.example.demo.repositories.RepositorioDocumentalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class RepositorioDocumentalService {

    @Autowired private RepositorioDocumentalRepository repoRepository;

    public RepositorioDocumental crearAlIniciarTramite(String tramiteId, String politicaId) {
        if (politicaId == null || politicaId.isBlank()) {
            throw new IllegalArgumentException("politicaId requerido para crear el repositorio del trámite: " + tramiteId);
        }

        var existente = repoRepository.findByTramiteId(tramiteId);
        if (existente.isPresent()) {
            return existente.get();
        }

        RepositorioDocumental r = new RepositorioDocumental();
        r.setTramiteId(tramiteId);
        r.setPoliticaId(politicaId);
        r.setNombre("Repositorio - Tramite " + tramiteId);
        r.setBucketKey("tramites/" + tramiteId + "/");
        r.setTotalArchivos(0);
        r.setTotalBytes(0);
        r.setActivo(true);
        r.setFechaCreacion(LocalDateTime.now());

        try {
            RepositorioDocumental guardado = repoRepository.save(r);
            log.info("[Repositorio] Creado para trámite {} → {}", tramiteId, guardado.getId());
            return guardado;
        } catch (DuplicateKeyException e) {
            return repoRepository.findByTramiteId(tramiteId).orElseThrow();
        }
    }

    public RepositorioDocumental buscarPorId(String id) {
        return repoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repositorio no encontrado: " + id));
    }

    public RepositorioDocumental buscarPorTramite(String tramiteId) {
        return repoRepository.findByTramiteId(tramiteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El trámite no tiene repositorio: " + tramiteId));
    }

    public void incrementarTotales(String repositorioId, long bytes) {
        repoRepository.findById(repositorioId).ifPresent(r -> {
            r.setTotalArchivos(r.getTotalArchivos() + 1);
            r.setTotalBytes(r.getTotalBytes() + bytes);
            repoRepository.save(r);
        });
    }
}
