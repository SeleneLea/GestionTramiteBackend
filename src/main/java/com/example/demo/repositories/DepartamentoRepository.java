package com.example.demo.repositories;

import com.example.demo.models.Departamento;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartamentoRepository extends MongoRepository<Departamento, String> {
    Optional<Departamento> findByCodigo(String codigo);
    Optional<Departamento> findByNombre(String nombre);
    List<Departamento> findByActivoTrue();
}
