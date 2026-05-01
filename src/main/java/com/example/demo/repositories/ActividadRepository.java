package com.example.demo.repositories;

import com.example.demo.models.Actividad;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActividadRepository extends MongoRepository<Actividad, String> {
    List<Actividad> findByDepartamentoId(String departamentoId);
    List<Actividad> findByReutilizableTrue();
}
