package com.example.demo.repositories;

import com.example.demo.models.MetricaTiempo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetricaTiempoRepository extends MongoRepository<MetricaTiempo, String> {
    List<MetricaTiempo> findByTramiteId(String tramiteId);
    List<MetricaTiempo> findByDepartamentoId(String departamentoId);
    List<MetricaTiempo> findByActividadId(String actividadId);
    List<MetricaTiempo> findByActividadIdOrderByFechaFinActividadDesc(String actividadId);
    List<MetricaTiempo> findBySuperoSlaTrue();
}
