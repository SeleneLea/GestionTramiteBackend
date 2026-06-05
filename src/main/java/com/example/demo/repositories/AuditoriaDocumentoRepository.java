package com.example.demo.repositories;

import com.example.demo.models.AuditoriaDocumento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditoriaDocumentoRepository extends MongoRepository<AuditoriaDocumento, String> {

    @Query("{ 'documentoArchivoId': ?0, " +
            "  'timestamp': { $gte: ?1, $lte: ?2 } }")
    Page<AuditoriaDocumento> buscarPorDocumentoEntreFechas(String documentoArchivoId,
                                                            LocalDateTime desde,
                                                            LocalDateTime hasta,
                                                            Pageable pageable);

    Page<AuditoriaDocumento> findByDocumentoArchivoIdOrderByTimestampDesc(String documentoArchivoId,
                                                                          Pageable pageable);
}
