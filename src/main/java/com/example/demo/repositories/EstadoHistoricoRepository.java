package com.example.demo.repositories;

import com.example.demo.models.EstadoHistorico;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstadoHistoricoRepository extends MongoRepository<EstadoHistorico, String> {
    List<EstadoHistorico> findByTramiteIdOrderByFechaCambioAsc(String tramiteId);
}
