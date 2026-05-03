package com.example.demo.repositories;

import com.example.demo.models.DocumentoArchivo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentoArchivoRepository extends MongoRepository<DocumentoArchivo, String> {

    List<DocumentoArchivo> findByRepositorioIdAndActivoTrue(String repositorioId);

    List<DocumentoArchivo> findByTramiteIdAndActivoTrue(String tramiteId);

    List<DocumentoArchivo> findByTramiteIdAndActividadIdAndActivoTrue(String tramiteId, String actividadId);

    List<DocumentoArchivo> findByPoliticaIdAndActivoTrue(String politicaId);
}
