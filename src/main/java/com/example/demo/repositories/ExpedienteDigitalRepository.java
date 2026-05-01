package com.example.demo.repositories;

import com.example.demo.models.ExpedienteDigital;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpedienteDigitalRepository extends MongoRepository<ExpedienteDigital, String> {
    Optional<ExpedienteDigital> findByTramiteId(String tramiteId);
}
