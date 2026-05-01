package com.example.demo.repositories;

import com.example.demo.models.Adjunto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdjuntoRepository extends MongoRepository<Adjunto, String> {
    List<Adjunto> findBySeccionId(String seccionId);
    List<Adjunto> findByTramiteId(String tramiteId);
    List<Adjunto> findByTramiteIdAndActividadId(String tramiteId, String actividadId);
}
