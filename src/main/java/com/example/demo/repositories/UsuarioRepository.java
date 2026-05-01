package com.example.demo.repositories;

import com.example.demo.models.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByTipo(String tipo);

    List<Usuario> findByActivoTrue();

    boolean existsByEmail(String email);
}
