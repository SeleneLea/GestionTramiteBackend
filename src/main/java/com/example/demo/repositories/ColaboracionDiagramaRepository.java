package com.example.demo.repositories;

import com.example.demo.models.ColaboracionDiagrama;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColaboracionDiagramaRepository extends MongoRepository<ColaboracionDiagrama, String> {
    List<ColaboracionDiagrama> findByDiagramaId(String diagramaId);
    List<ColaboracionDiagrama> findByInvitadoId(String invitadoId);
    List<ColaboracionDiagrama> findByInvitadoIdAndEstado(String invitadoId, String estado);
    long countByInvitadoIdAndEstado(String invitadoId, String estado);
}
