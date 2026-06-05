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

        try {
            mongoTemplate.indexOps("repositorios_documentales").getIndexInfo().stream()
                    .filter(ix -> ix.isUnique()
                            && ix.getIndexFields().size() == 1
                            && "politicaId".equals(ix.getIndexFields().get(0).getKey()))
                    .forEach(ix -> mongoTemplate.indexOps("repositorios_documentales").dropIndex(ix.getName()));
        } catch (RuntimeException e) {

        }

        mongoTemplate.indexOps("tramites")
                .ensureIndex(new Index()
                        .on("estadoActual", Sort.Direction.ASC)
                        .on("fechaInicio", Sort.Direction.DESC));

        mongoTemplate.indexOps("tramites")
                .ensureIndex(new Index().on("clienteId", Sort.Direction.ASC));

        mongoTemplate.indexOps("tramites")
                .ensureIndex(new Index().on("funcionarioActualId", Sort.Direction.ASC));

        mongoTemplate.indexOps("trazabilidad")
                .ensureIndex(new Index()
                        .on("tramiteId", Sort.Direction.ASC)
                        .on("timestamp", Sort.Direction.DESC));

        mongoTemplate.indexOps("notificaciones")
                .ensureIndex(new Index()
                        .on("destinatarioId", Sort.Direction.ASC)
                        .on("leida", Sort.Direction.ASC));

        mongoTemplate.indexOps("notificaciones")
                .ensureIndex(new Index()
                        .on("destinatarioId", Sort.Direction.ASC)
                        .on("fechaCreacion", Sort.Direction.DESC));

        mongoTemplate.indexOps("estados_historicos")
                .ensureIndex(new Index()
                        .on("tramiteId", Sort.Direction.ASC)
                        .on("fechaCambio", Sort.Direction.DESC));

        mongoTemplate.indexOps("secciones_expediente")
                .ensureIndex(new Index()
                        .on("expedienteId", Sort.Direction.ASC)
                        .on("ordenSeccion", Sort.Direction.ASC));

        mongoTemplate.indexOps("logs_agente")
                .ensureIndex(new Index()
                        .on("usuarioId", Sort.Direction.ASC)
                        .on("timestamp", Sort.Direction.DESC));

        mongoTemplate.indexOps("nodos_diagrama")
                .ensureIndex(new Index().on("diagramaId", Sort.Direction.ASC));

        mongoTemplate.indexOps("flujos_transicion")
                .ensureIndex(new Index().on("diagramaId", Sort.Direction.ASC));
    }
}
