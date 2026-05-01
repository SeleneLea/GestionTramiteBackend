package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class MongoIndexConfig {

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {
        // Trámites — búsqueda por estado + fecha
        mongoTemplate.indexOps("tramites")
                .ensureIndex(new Index()
                        .on("estadoActual", Sort.Direction.ASC)
                        .on("fechaInicio", Sort.Direction.DESC));

        // Trámites — búsqueda rápida por cliente
        mongoTemplate.indexOps("tramites")
                .ensureIndex(new Index().on("clienteId", Sort.Direction.ASC));

        // Trámites — búsqueda por funcionario
        mongoTemplate.indexOps("tramites")
                .ensureIndex(new Index().on("funcionarioActualId", Sort.Direction.ASC));

        // Trazabilidad — consulta por trámite ordenada por tiempo
        mongoTemplate.indexOps("trazabilidad")
                .ensureIndex(new Index()
                        .on("tramiteId", Sort.Direction.ASC)
                        .on("timestamp", Sort.Direction.DESC));

        // Notificaciones — no leídas por destinatario
        mongoTemplate.indexOps("notificaciones")
                .ensureIndex(new Index()
                        .on("destinatarioId", Sort.Direction.ASC)
                        .on("leida", Sort.Direction.ASC));

        // Estados históricos por trámite
        mongoTemplate.indexOps("estados_historicos")
                .ensureIndex(new Index()
                        .on("tramiteId", Sort.Direction.ASC)
                        .on("fechaCambio", Sort.Direction.DESC));

        // Secciones por expediente ordenadas
        mongoTemplate.indexOps("secciones_expediente")
                .ensureIndex(new Index()
                        .on("expedienteId", Sort.Direction.ASC)
                        .on("ordenSeccion", Sort.Direction.ASC));

        // Logs del agente por usuario
        mongoTemplate.indexOps("logs_agente")
                .ensureIndex(new Index()
                        .on("usuarioId", Sort.Direction.ASC)
                        .on("timestamp", Sort.Direction.DESC));

        // Nodos por diagrama
        mongoTemplate.indexOps("nodos_diagrama")
                .ensureIndex(new Index().on("diagramaId", Sort.Direction.ASC));

        // Flujos por diagrama
        mongoTemplate.indexOps("flujos_transicion")
                .ensureIndex(new Index().on("diagramaId", Sort.Direction.ASC));
    }
}
