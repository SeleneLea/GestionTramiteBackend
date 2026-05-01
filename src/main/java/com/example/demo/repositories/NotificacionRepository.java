package com.example.demo.repositories;

import com.example.demo.models.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    List<Notificacion> findByDestinatarioIdAndLeidaFalse(String destinatarioId);
    List<Notificacion> findByDestinatarioIdOrderByFechaCreacionDesc(String destinatarioId);
    List<Notificacion> findByEstadoEnvioAndIntentosEnvioLessThan(String estadoEnvio, int maxIntentos);
    long countByDestinatarioIdAndLeidaFalse(String destinatarioId);
}
