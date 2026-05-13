package com.example.demo.repositories;

import com.example.demo.models.RepositorioDocumental;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioDocumentalRepository extends MongoRepository<RepositorioDocumental, String> {
    Optional<RepositorioDocumental> findByTramiteId(String tramiteId);
}
