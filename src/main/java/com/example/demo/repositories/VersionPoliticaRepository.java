package com.example.demo.repositories;

import com.example.demo.models.VersionPolitica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VersionPoliticaRepository extends MongoRepository<VersionPolitica, String> {
    List<VersionPolitica> findByPoliticaId(String politicaId);
    boolean existsByPoliticaIdAndNumeroVersion(String politicaId, int numeroVersion);
}
