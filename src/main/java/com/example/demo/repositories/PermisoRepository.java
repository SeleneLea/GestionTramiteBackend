package com.example.demo.repositories;

import com.example.demo.models.Permiso;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermisoRepository extends MongoRepository<Permiso, String> {
    Optional<Permiso> findByCodigo(String codigo);
    List<Permiso> findByModulo(String modulo);
}
