package com.example.demo.services;

import com.example.demo.models.EstadoTramite;
import com.example.demo.models.Secuencia;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TramiteService {

    @Autowired
    private TramiteRepository tramiteRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Tramite crearTramite(Tramite tramite) {
        tramite.setCodigo(generarCodigo());
        tramite.setEstadoActual(EstadoTramite.EN_CURSO.getValor());
        tramite.setFechaInicio(LocalDateTime.now());
        if (tramite.getPrioridad() == 0) {
            tramite.setPrioridad(3);
        }
        return tramiteRepository.save(tramite);
    }

    public List<Tramite> listarTodos() {
        return tramiteRepository.findAll();
    }

    public Optional<Tramite> buscarPorId(String id) {
        return tramiteRepository.findById(id);
    }

    public Optional<Tramite> buscarPorCodigo(String codigo) {
        return tramiteRepository.findByCodigo(codigo);
    }

    public List<Tramite> obtenerTramitesActivos() {
        return tramiteRepository.findTramitesActivos();
    }

    public List<Tramite> listarPorCliente(String clienteId) {
        return tramiteRepository.findByClienteId(clienteId);
    }

    public long contarPorEstado(String estado) {
        return tramiteRepository.countByEstadoActual(estado);
    }

    private String generarCodigo() {
        int year = LocalDateTime.now().getYear();
        Secuencia sec = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is("tramite-" + year)),
                new Update().inc("seq", 1),
                new FindAndModifyOptions().returnNew(true).upsert(true),
                Secuencia.class,
                "secuencias");
        return String.format("TR-%d-%05d", year, sec.getSeq());
    }
}
