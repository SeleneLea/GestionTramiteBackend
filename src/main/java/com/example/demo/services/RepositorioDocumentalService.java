package com.example.demo.services;

import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.RepositorioDocumental;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.RepositorioDocumentalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * CU-32 — Crea y mantiene el repositorio documental asociado a una política.
 *
 * {@link #crearAlGuardarPolitica(String)} es idempotente: si la política ya
 * tiene {@code repositorioId}, devuelve el existente sin crear otro.
 */
@Service
@Slf4j
public class RepositorioDocumentalService {

    @Autowired private RepositorioDocumentalRepository repoRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;

    /**
     * Crea el repositorio asociado a la política. Idempotente.
     */
    public RepositorioDocumental crearAlGuardarPolitica(String politicaId) {
        PoliticaNegocio politica = politicaRepository.findById(politicaId)
                .orElseThrow(() -> new IllegalArgumentException("Política no encontrada: " + politicaId));

        // ¿Ya existe? — devolverlo
        var existente = repoRepository.findByPoliticaId(politicaId);
        if (existente.isPresent()) {
            // Garantizar que la política tenga el repositorioId enlazado
            if (politica.getRepositorioId() == null) {
                politica.setRepositorioId(existente.get().getId());
                politicaRepository.save(politica);
            }
            return existente.get();
        }

        RepositorioDocumental r = new RepositorioDocumental();
        r.setPoliticaId(politicaId);
        r.setNombre("Repositorio - " + politica.getNombre());
        r.setBucketKey("politicas/" + politicaId + "/");
        r.setTotalArchivos(0);
        r.setTotalBytes(0);
        r.setActivo(true);
        r.setFechaCreacion(LocalDateTime.now());
        r = repoRepository.save(r);

        politica.setRepositorioId(r.getId());
        politicaRepository.save(politica);

        log.info("[Repositorio] Creado para política {} → {}", politicaId, r.getId());
        return r;
    }

    public RepositorioDocumental buscarPorId(String id) {
        return repoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repositorio no encontrado: " + id));
    }

    public RepositorioDocumental buscarPorPolitica(String politicaId) {
        return repoRepository.findByPoliticaId(politicaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La política no tiene repositorio: " + politicaId));
    }

    /** Actualiza los totales tras una subida. */
    public void incrementarTotales(String repositorioId, long bytes) {
        repoRepository.findById(repositorioId).ifPresent(r -> {
            r.setTotalArchivos(r.getTotalArchivos() + 1);
            r.setTotalBytes(r.getTotalBytes() + bytes);
            repoRepository.save(r);
        });
    }
}
