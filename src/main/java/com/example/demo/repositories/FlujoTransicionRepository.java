package com.example.demo.repositories;

import com.example.demo.models.FlujoTransicion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlujoTransicionRepository extends MongoRepository<FlujoTransicion, String> {
    List<FlujoTransicion> findByDiagramaId(String diagramaId);
    List<FlujoTransicion> findByNodoOrigenId(String nodoOrigenId);
    List<FlujoTransicion> findByNodoDestinoId(String nodoDestinoId);
}
