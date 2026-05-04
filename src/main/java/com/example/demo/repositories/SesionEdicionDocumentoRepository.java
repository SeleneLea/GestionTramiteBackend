package com.example.demo.repositories;

import com.example.demo.models.SesionEdicionDocumento;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesionEdicionDocumentoRepository extends MongoRepository<SesionEdicionDocumento, String> {

    Optional<SesionEdicionDocumento> findByDocumentoArchivoId(String documentoArchivoId);

    List<SesionEdicionDocumento> findByUltimoLatidoBefore(LocalDateTime instante);

    /**
     * Sesiones con al menos un participante cuyo latido es anterior al corte.
     * El path embebido se traduce a {@code {"participantes.ultimoLatido": {"$lt": instante}}},
     * de modo que se capturan también las sesiones aún "vivas" (con alguien activo)
     * que arrastran participantes obsoletos.
     */
    List<SesionEdicionDocumento> findByParticipantes_UltimoLatidoBefore(LocalDateTime instante);
}
