package com.example.demo.services;

import com.example.demo.dto.ReporteNaturalRequest;
import com.example.demo.dto.ReporteNaturalResponse;
import com.example.demo.models.Reporte;
import com.example.demo.repositories.ReporteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU-41 — Genera reportes a partir de consultas en lenguaje natural.
 *
 * Flujo:
 *  1. Pasa la consulta al microservicio IA.
 *  2. El microservicio devuelve un pipeline MongoDB (colección + stages).
 *  3. Spring **valida** el pipeline contra una whitelist de colecciones y
 *     operadores prohibidos, lo ejecuta con {@link MongoTemplate} y
 *     persiste el {@link Reporte} para auditoría.
 */
@Service
public class ReporteNaturalService {

    @Autowired private IaProxyService iaProxy;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ReporteRepository reporteRepository;

    @Value("${app.reportes.max-filas:50000}")
    private long maxFilas;

    @Value("#{'${app.reportes.colecciones-permitidas:tramites,expedientes_digitales,documentos_archivo,sugerencias_politica,alertas_anomalia}'.split(',')}")
    private List<String> coleccionesPermitidas;

    /** Operadores prohibidos a nivel pipeline. */
    private static final Set<String> OPERADORES_PROHIBIDOS = Set.of(
            "$out", "$merge", "$function", "$accumulator", "$where", "$expr",
            "$lookup", "$unionWith", "$graphLookup"
    );

    private final ObjectMapper json = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public ReporteNaturalResponse generar(ReporteNaturalRequest req, String adminId) {

        Map<String, Object> resp = iaProxy.consultaNatural(req.getConsulta());

        String collection = stringDe(resp.get("collection"));
        List<Map<String, Object>> pipeline = (List<Map<String, Object>>)
                resp.getOrDefault("pipeline", List.of());

        if (collection == null || pipeline.isEmpty()) {
            throw new IllegalArgumentException(
                    "RPT_PIPELINE_INVALIDO: el microservicio devolvió una consulta vacía");
        }
        if (!coleccionesPermitidas.contains(collection)) {
            throw new IllegalArgumentException(
                    "RPT_COLECCION_NO_PERMITIDA: " + collection
                            + " (permitidas: " + coleccionesPermitidas + ")");
        }
        validarPipeline(pipeline);

        // Ejecutar
        List<Document> stages = new ArrayList<>();
        for (Map<String, Object> stage : pipeline) {
            // El pipeline de la IA trae fechas en Extended-JSON {"$date":"<iso>"}
            // que new Document(Map) NO interpreta; las normalizamos a java.util.Date.
            Object normalizado = normalizarFechas(stage);
            stages.add(new Document((Map<String, Object>) normalizado));
        }
        // Forzar $limit defensivo al final si no lo trae
        if (!tieneLimit(pipeline)) {
            stages.add(new Document("$limit", maxFilas));
        }

        var aggResult = mongoTemplate.getCollection(collection)
                .aggregate(stages)
                .into(new ArrayList<>());

        List<Map<String, Object>> filas = new ArrayList<>(aggResult.size());
        for (Document d : aggResult) {
            filas.add(new HashMap<>(d));
        }

        // Audita la lista REALMENTE ejecutada (stages, con el $limit defensivo),
        // no el pipeline original devuelto por la IA.
        String queryStr;
        try {
            queryStr = json.writeValueAsString(stages);
        } catch (Exception e) {
            queryStr = stages.toString();
        }

        // Persistir reporte (audit + reuso)
        Reporte r = new Reporte();
        r.setGeneradoPorId(adminId);
        r.setTipo("CONSULTA_NATURAL");
        // Mapa mutable: además de la consulta original, persistimos el pipeline
        // REALMENTE ejecutado (stages, con el $limit defensivo) para auditoría.
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("consultaOriginal", req.getConsulta());
        filtros.put("collection", collection);
        filtros.put("pipelineEjecutado", queryStr);
        r.setFiltros(filtros);
        r.setFormato(req.getFormatoExport() != null ? req.getFormatoExport() : "JSON");
        r.setFechaGeneracion(LocalDateTime.now());

        r = reporteRepository.save(r);

        // Muestra: las primeras 50 filas
        List<Map<String, Object>> muestra = filas.size() > 50 ? filas.subList(0, 50) : filas;

        return new ReporteNaturalResponse(
                r.getId(),
                collection,
                muestra,
                filas.size(),
                null,          // urlDescarga — pendiente cuando se enchufe export S3
                r.getFormato(),
                queryStr
        );
    }

    /**
     * Valida el pipeline de forma RECURSIVA: recorre todo el árbol Map/List y,
     * por cada Map, verifica que ninguna clave esté en {@link #OPERADORES_PROHIBIDOS},
     * descendiendo en los valores que sean Map o List. Así se alcanzan operadores
     * anidados ($expr, $where, $function, $accumulator, $lookup, $out, $merge…)
     * escondidos dentro de $facet o de subdocumentos, que la inspección de solo
     * nivel 1 dejaba pasar.
     */
    private void validarPipeline(List<Map<String, Object>> pipeline) {
        for (Map<String, Object> stage : pipeline) {
            validarNodo(stage);
        }
    }

    private void validarNodo(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                String clave = String.valueOf(entrada.getKey());
                if (OPERADORES_PROHIBIDOS.contains(clave)) {
                    throw new IllegalArgumentException(
                            "RPT_PIPELINE_INVALIDO: operador prohibido " + clave);
                }
                validarNodo(entrada.getValue());
            }
        } else if (valor instanceof List<?> lista) {
            for (Object elemento : lista) {
                validarNodo(elemento);
            }
        }
    }

    /**
     * Normaliza recursivamente los valores Extended-JSON {@code {"$date":"<iso>"}}
     * que el pipeline de la IA trae como String y que {@code new Document(Map)} no
     * interpreta, convirtiéndolos a {@link java.util.Date}. Sin esto, el reporte por
     * rango de fechas compara contra texto y devuelve 0 filas.
     */
    @SuppressWarnings("unchecked")
    private Object normalizarFechas(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            if (mapa.size() == 1 && mapa.containsKey("$date")
                    && mapa.get("$date") instanceof String s) {
                return Date.from(Instant.parse(s));
            }
            Map<String, Object> resultado = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                resultado.put(String.valueOf(entrada.getKey()),
                        normalizarFechas(entrada.getValue()));
            }
            return resultado;
        }
        if (valor instanceof List<?> lista) {
            List<Object> resultado = new ArrayList<>(lista.size());
            for (Object elemento : lista) {
                resultado.add(normalizarFechas(elemento));
            }
            return resultado;
        }
        return valor;
    }

    private boolean tieneLimit(List<Map<String, Object>> pipeline) {
        for (Map<String, Object> stage : pipeline) {
            if (stage.containsKey("$limit")) return true;
        }
        return false;
    }

    private String stringDe(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unused")
    private BasicQuery toQuery(Map<String, Object> match) {
        return new BasicQuery(new Document(match).toJson());
    }
}
