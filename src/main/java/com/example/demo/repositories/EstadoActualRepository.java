package com.example.demo.repositories;

import com.example.demo.models.EstadoActual;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoActualRepository extends MongoRepository<EstadoActual, String> {
    Optional<EstadoActual> findByTramiteId(String tramiteId);
    boolean existsByTramiteId(String tramiteId);
}
