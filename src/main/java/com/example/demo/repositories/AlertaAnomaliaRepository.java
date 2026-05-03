package com.example.demo.repositories;

import com.example.demo.models.AlertaAnomalia;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertaAnomaliaRepository extends MongoRepository<AlertaAnomalia, String> {

    List<AlertaAnomalia> findByTramiteIdOrderByFechaDeteccionDesc(String tramiteId);

    List<AlertaAnomalia> findByFalsoPositivoFalseOrderByFechaDeteccionDesc();
}
