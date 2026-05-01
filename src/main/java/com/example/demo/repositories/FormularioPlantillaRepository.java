package com.example.demo.repositories;

import com.example.demo.models.FormularioPlantilla;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormularioPlantillaRepository extends MongoRepository<FormularioPlantilla, String> {
    List<FormularioPlantilla> findByNodoId(String nodoId);
    Optional<FormularioPlantilla> findByNombre(String nombre);
}
