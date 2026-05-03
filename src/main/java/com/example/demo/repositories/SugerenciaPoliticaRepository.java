package com.example.demo.repositories;

import com.example.demo.models.SugerenciaPolitica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SugerenciaPoliticaRepository extends MongoRepository<SugerenciaPolitica, String> {

    List<SugerenciaPolitica> findByClienteIdOrderByFechaCreacionDesc(String clienteId);
}
