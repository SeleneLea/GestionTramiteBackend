package com.example.demo.repositories;

import com.example.demo.models.Documento;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentoRepository extends MongoRepository<Documento, String> {
    List<Documento> findByActivo(boolean activo);
    Optional<Documento> findByNombre(String nombre);
}
