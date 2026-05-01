package com.example.demo.repositories;

import com.example.demo.models.LogAgente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogAgenteRepository extends MongoRepository<LogAgente, String> {
    List<LogAgente> findByUsuarioIdOrderByTimestampDesc(String usuarioId);
    List<LogAgente> findByContextoModulo(String contextoModulo);
}
