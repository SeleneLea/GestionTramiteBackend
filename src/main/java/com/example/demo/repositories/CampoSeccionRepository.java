package com.example.demo.repositories;

import com.example.demo.models.CampoSeccion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampoSeccionRepository extends MongoRepository<CampoSeccion, String> {
    List<CampoSeccion> findBySeccionId(String seccionId);
}
