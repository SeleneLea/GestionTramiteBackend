package com.example.demo.repositories;

import com.example.demo.models.Reporte;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReporteRepository extends MongoRepository<Reporte, String> {
    List<Reporte> findByGeneradoPorIdOrderByFechaGeneracionDesc(String generadoPorId);
}
