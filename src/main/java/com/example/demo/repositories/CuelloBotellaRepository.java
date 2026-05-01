package com.example.demo.repositories;

import com.example.demo.models.CuelloBotella;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuelloBotellaRepository extends MongoRepository<CuelloBotella, String> {
    List<CuelloBotella> findByDepartamentoId(String departamentoId);
    List<CuelloBotella> findByPeriodo(String periodo);
    List<CuelloBotella> findAllByOrderByFechaDeteccionDesc();
}
