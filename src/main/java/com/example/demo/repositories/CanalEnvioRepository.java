package com.example.demo.repositories;

import com.example.demo.models.CanalEnvio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CanalEnvioRepository extends MongoRepository<CanalEnvio, String> {
    Optional<CanalEnvio> findByTipo(String tipo);
    boolean existsByTipo(String tipo);
}
