package com.example.demo.repositories;

import com.example.demo.models.DiagramaWorkflow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagramaWorkflowRepository extends MongoRepository<DiagramaWorkflow, String> {
    Optional<DiagramaWorkflow> findByPoliticaId(String politicaId);
    List<DiagramaWorkflow> findByCreadorId(String creadorId);
    List<DiagramaWorkflow> findByEstado(String estado);
}
