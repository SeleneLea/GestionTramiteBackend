package com.example.demo.repositories;

import com.example.demo.models.NodoDiagrama;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NodoDiagramaRepository extends MongoRepository<NodoDiagrama, String> {
    List<NodoDiagrama> findByDiagramaId(String diagramaId);
    List<NodoDiagrama> findByDiagramaIdAndTipo(String diagramaId, String tipo);
    List<NodoDiagrama> findByDepartamentoId(String departamentoId);
}
