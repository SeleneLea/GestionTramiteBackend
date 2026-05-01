package com.example.demo.repositories;

import com.example.demo.models.CampoPlantilla;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampoPlantillaRepository extends MongoRepository<CampoPlantilla, String> {
    List<CampoPlantilla> findByFormularioPlantillaId(String formularioPlantillaId);
    long countByFormularioPlantillaId(String formularioPlantillaId);
}
