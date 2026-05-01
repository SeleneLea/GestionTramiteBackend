package com.example.demo.repositories;

import com.example.demo.models.VersionDiagrama;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VersionDiagramaRepository extends MongoRepository<VersionDiagrama, String> {
    List<VersionDiagrama> findByDiagramaId(String diagramaId);
    boolean existsByDiagramaIdAndNumeroVersion(String diagramaId, int numeroVersion);
}
