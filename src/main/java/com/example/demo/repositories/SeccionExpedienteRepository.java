package com.example.demo.repositories;

import com.example.demo.models.SeccionExpediente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeccionExpedienteRepository extends MongoRepository<SeccionExpediente, String> {
    List<SeccionExpediente> findByExpedienteId(String expedienteId);
    List<SeccionExpediente> findByFuncionarioIdAndEstado(String funcionarioId, String estado);
    List<SeccionExpediente> findByExpedienteIdOrderByOrdenSeccionAsc(String expedienteId);
}
