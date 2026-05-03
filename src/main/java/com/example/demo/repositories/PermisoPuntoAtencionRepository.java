package com.example.demo.repositories;

import com.example.demo.models.PermisoPuntoAtencion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermisoPuntoAtencionRepository extends MongoRepository<PermisoPuntoAtencion, String> {

    Optional<PermisoPuntoAtencion> findByPoliticaIdAndActividadId(String politicaId, String actividadId);

    List<PermisoPuntoAtencion> findByPoliticaId(String politicaId);

    List<PermisoPuntoAtencion> findByActividadId(String actividadId);
}
