package com.example.demo.repositories;

import com.example.demo.models.VersionDocumento;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VersionDocumentoRepository extends MongoRepository<VersionDocumento, String> {

    List<VersionDocumento> findByDocumentoArchivoIdOrderByNumeroVersionDesc(String documentoArchivoId);

    Optional<VersionDocumento> findByDocumentoArchivoIdAndNumeroVersion(String documentoArchivoId,
                                                                        int numeroVersion);

    Optional<VersionDocumento> findFirstByDocumentoArchivoIdOrderByNumeroVersionDesc(String documentoArchivoId);

    Optional<VersionDocumento> findByDocumentoArchivoIdAndHashSha256(String documentoArchivoId, String hashSha256);
}
