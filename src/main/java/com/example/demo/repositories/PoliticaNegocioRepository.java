package com.example.demo.repositories;

import com.example.demo.models.PoliticaNegocio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PoliticaNegocioRepository extends MongoRepository<PoliticaNegocio, String> {
    Optional<PoliticaNegocio> findByNombre(String nombre);
    List<PoliticaNegocio> findByEstado(String estado);
    List<PoliticaNegocio> findByCreadorId(String creadorId);
}
