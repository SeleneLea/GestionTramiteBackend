package com.example.demo.services;

import com.example.demo.dto.ReporteNaturalRequest;
import com.example.demo.dto.ReporteNaturalResponse;
import com.example.demo.models.Departamento;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.Reporte;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.repositories.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-41 — Reportes ad-hoc por consulta natural.
 *
 * El microservicio IA interpreta la consulta (NLP) y devuelve un PLAN: pipeline
 * Mongo SEGURO + qué nombres enriquecer + filtros por nombre. Spring valida y
 * ejecuta el pipeline, resuelve los nombres (cliente/política/departamento) vía
 * repositorios (sin $lookup frágil), aplica los filtros por nombre y puede
 * exportar el resultado a Excel/PDF.
 */
@Service
public class ReporteNaturalService {

    @Autowired private IaProxyService iaProxy;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ReporteRepository reporteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;

    @Value("${app.reportes.max-filas:50000}")
    private long maxFilas;

    @Value("#{'${app.reportes.colecciones-permitidas:tramites,expedientes_digitales,documentos_archivo,sugerencias_politica,alertas_anomalia}'.split(',')}")
    private List<String> coleccionesPermitidas;

    /** Operadores prohibidos (los joins se hacen en Java, no en Mongo). */
    private static final Set<String> OPERADORES_PROHIBIDOS = Set.of(
            "$out", "$merge", "$function", "$accumulator", "$where", "$expr",
            "$lookup", "$unionWith", "$graphLookup");

    private final ObjectMapper json = new ObjectMapper();

    /** Resultado de ejecutar un plan: filas enriquecidas + metadatos. */
    private record Resultado(String collection, List<Map<String, Object>> filas, String queryStr) {}

    public ReporteNaturalResponse generar(ReporteNaturalRequest req, String adminId) {
        Resultado res = ejecutarPlan(req.getConsulta());

        Reporte r = new Reporte();
        r.setGeneradoPorId(adminId);
        r.setTipo("CONSULTA_NATURAL");
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("consultaOriginal", req.getConsulta());
        filtros.put("collection", res.collection());
        filtros.put("pipelineEjecutado", res.queryStr());
        r.setFiltros(filtros);
        r.setFormato(req.getFormatoExport() != null ? req.getFormatoExport() : "JSON");
        r.setFechaGeneracion(LocalDateTime.now());
        r = reporteRepository.save(r);

        List<Map<String, Object>> filas = res.filas();
        List<Map<String, Object>> muestra = filas.size() > 50 ? filas.subList(0, 50) : filas;
        return new ReporteNaturalResponse(r.getId(), res.collection(), muestra,
                filas.size(), null, r.getFormato(), res.queryStr());
    }

    /** Ejecuta la consulta natural y devuelve los bytes del archivo (xlsx|pdf). */
    public byte[] exportar(String consulta, String formato) {
        Resultado res = ejecutarPlan(consulta);
        return "pdf".equalsIgnoreCase(formato)
                ? exportarPdf(res.filas())
                : exportarExcel(res.filas());
    }

    // ── núcleo: interpretar (IA) → ejecutar → enriquecer → filtrar ──────────────
    @SuppressWarnings("unchecked")
    private Resultado ejecutarPlan(String consulta) {
        Map<String, Object> resp = iaProxy.consultaNatural(consulta);

        String collection = stringDe(resp.get("collection"));
        List<Map<String, Object>> pipeline = new ArrayList<>(
                (List<Map<String, Object>>) resp.getOrDefault("pipeline", List.of()));
        List<String> enriquecer = (List<String>) resp.getOrDefault("enriquecer", List.of());
        Map<String, Object> filtrosPost = (Map<String, Object>) resp.getOrDefault("filtros_post", Map.of());

        if (collection == null || pipeline.isEmpty()) {
            throw new IllegalArgumentException(
                    "RPT_PIPELINE_INVALIDO: el microservicio devolvió una consulta vacía");
        }
        if (!coleccionesPermitidas.contains(collection)) {
            throw new IllegalArgumentException("RPT_COLECCION_NO_PERMITIDA: " + collection);
        }
        validarPipeline(pipeline);

        // Filtro por NOMBRE de política → resolver a ids y anteponer un $match (confiable).
        String politicaNombre = stringDe(filtrosPost.get("politica_nombre"));
        if (politicaNombre != null && !politicaNombre.isBlank()) {
            List<String> ids = resolverPoliticaIds(politicaNombre);
            // Si no matchea ninguna política, devolvemos vacío (el filtro no se cumple).
            pipeline.add(0, Map.of("$match", Map.of("politicaId", Map.of("$in", ids))));
        }

        List<Document> stages = new ArrayList<>();
        for (Map<String, Object> stage : pipeline) {
            stages.add(new Document((Map<String, Object>) normalizarFechas(stage)));
        }
        if (!tieneLimit(pipeline)) stages.add(new Document("$limit", maxFilas));

        var agg = mongoTemplate.getCollection(collection).aggregate(stages).into(new ArrayList<>());
        List<Map<String, Object>> filas = new ArrayList<>(agg.size());
        for (Document d : agg) filas.add(new LinkedHashMap<>(d));

        enriquecerNombres(filas, enriquecer);

        // Filtro por NOMBRE de departamento (tras enriquecer, porque es derivado).
        String deptNombre = stringDe(filtrosPost.get("departamento_nombre"));
        if (deptNombre != null && !deptNombre.isBlank()) {
            String n = norm(deptNombre);
            filas = filas.stream()
                    .filter(f -> norm(stringDe(f.get("departamento"))).contains(n))
                    .collect(Collectors.toList());
        }

        String queryStr;
        try { queryStr = json.writeValueAsString(stages); } catch (Exception e) { queryStr = stages.toString(); }
        return new Resultado(collection, filas, queryStr);
    }

    /** Resuelve clienteId/politicaId/nodoActualId a nombres legibles, en lote. */
    private void enriquecerNombres(List<Map<String, Object>> filas, List<String> enriquecer) {
        if (filas.isEmpty() || enriquecer == null || enriquecer.isEmpty()) return;

        if (enriquecer.contains("cliente_nombre")) {
            Map<String, String> nombres = mapNombres(idsDe(filas, "clienteId"), "usuario");
            for (Map<String, Object> f : filas) {
                String id = stringDe(f.get("clienteId"));
                if (id != null) f.put("cliente", nombres.getOrDefault(id, id));
            }
        }
        if (enriquecer.contains("politica_nombre")) {
            Map<String, String> nombres = mapNombres(idsDe(filas, "politicaId"), "politica");
            for (Map<String, Object> f : filas) {
                String id = stringDe(f.get("politicaId"));
                if (id != null) f.put("politica", nombres.getOrDefault(id, id));
            }
        }
        if (enriquecer.contains("departamento_nombre")) {
            Map<String, String> deptDeNodo = departamentoPorNodo(idsDe(filas, "nodoActualId"));
            for (Map<String, Object> f : filas) {
                String nodoId = stringDe(f.get("nodoActualId"));
                if (nodoId != null) f.put("departamento", deptDeNodo.getOrDefault(nodoId, ""));
            }
        }
    }

    private Set<String> idsDe(List<Map<String, Object>> filas, String campo) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> f : filas) {
            String v = stringDe(f.get(campo));
            if (v != null && !v.isBlank()) ids.add(v);
        }
        return ids;
    }

    private Map<String, String> mapNombres(Set<String> ids, String tipo) {
        Map<String, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        if ("usuario".equals(tipo)) {
            for (Usuario u : usuarioRepository.findAllById(ids)) {
                String n = (u.getNombre() != null ? u.getNombre() : "")
                        + (u.getApellido() != null ? " " + u.getApellido() : "");
                out.put(u.getId(), n.trim());
            }
        } else if ("politica".equals(tipo)) {
            for (PoliticaNegocio p : politicaRepository.findAllById(ids)) {
                out.put(p.getId(), p.getNombre() != null ? p.getNombre() : p.getId());
            }
        }
        return out;
    }

    /** nodoActualId → nombre del departamento (vía NodoDiagrama.departamentoId). */
    private Map<String, String> departamentoPorNodo(Set<String> nodoIds) {
        Map<String, String> out = new HashMap<>();
        if (nodoIds.isEmpty()) return out;
        Map<String, String> deptDeNodo = new HashMap<>();
        Set<String> deptIds = new LinkedHashSet<>();
        for (NodoDiagrama n : nodoRepository.findAllById(nodoIds)) {
            if (n.getDepartamentoId() != null) {
                deptDeNodo.put(n.getId(), n.getDepartamentoId());
                deptIds.add(n.getDepartamentoId());
            }
        }
        Map<String, String> nombreDept = new HashMap<>();
        for (Departamento d : departamentoRepository.findAllById(deptIds)) {
            nombreDept.put(d.getId(), d.getNombre() != null ? d.getNombre() : d.getId());
        }
        deptDeNodo.forEach((nodoId, deptId) -> out.put(nodoId, nombreDept.getOrDefault(deptId, "")));
        return out;
    }

    private List<String> resolverPoliticaIds(String nombre) {
        String n = norm(nombre);
        return politicaRepository.findAll().stream()
                .filter(p -> p.getNombre() != null && norm(p.getNombre()).contains(n))
                .map(PoliticaNegocio::getId)
                .collect(Collectors.toList());
    }

    // ── export genérico (cualquier conjunto de columnas) ───────────────────────
    private byte[] exportarExcel(List<Map<String, Object>> filas) {
        List<String> cols = columnasDe(filas);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Reporte");
            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < cols.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(cols.get(c));
                cell.setCellStyle(headStyle);
            }
            int rowIdx = 1;
            for (Map<String, Object> fila : filas) {
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < cols.size(); c++) {
                    row.createCell(c).setCellValue(stringDe(fila.get(cols.get(c))) != null
                            ? stringDe(fila.get(cols.get(c))) : "");
                }
            }
            for (int c = 0; c < cols.size(); c++) sheet.autoSizeColumn(c);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el Excel: " + e.getMessage(), e);
        }
    }

    private byte[] exportarPdf(List<Map<String, Object>> filas) {
        List<String> cols = columnasDe(filas);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdf)) {

            doc.add(new Paragraph("Reporte por consulta natural (CU-41)"));
            if (cols.isEmpty()) {
                doc.add(new Paragraph("Sin resultados."));
            } else {
                Table table = new Table(UnitValue.createPercentArray(cols.size())).useAllAvailableWidth();
                for (String col : cols) {
                    table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(col)));
                }
                for (Map<String, Object> fila : filas) {
                    for (String col : cols) {
                        String v = stringDe(fila.get(col));
                        table.addCell(new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(v != null ? v : "")));
                    }
                }
                doc.add(table);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
    }

    private List<String> columnasDe(List<Map<String, Object>> filas) {
        return filas.isEmpty() ? List.of() : new ArrayList<>(filas.get(0).keySet());
    }

    // ── validación / utilidades (igual que antes) ──────────────────────────────
    private void validarPipeline(List<Map<String, Object>> pipeline) {
        for (Map<String, Object> stage : pipeline) validarNodo(stage);
    }

    private void validarNodo(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (OPERADORES_PROHIBIDOS.contains(String.valueOf(e.getKey()))) {
                    throw new IllegalArgumentException(
                            "RPT_PIPELINE_INVALIDO: operador prohibido " + e.getKey());
                }
                validarNodo(e.getValue());
            }
        } else if (valor instanceof List<?> lista) {
            for (Object el : lista) validarNodo(el);
        }
    }

    @SuppressWarnings("unchecked")
    private Object normalizarFechas(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            if (mapa.size() == 1 && mapa.containsKey("$date") && mapa.get("$date") instanceof String s) {
                return Date.from(Instant.parse(s));
            }
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                r.put(String.valueOf(e.getKey()), normalizarFechas(e.getValue()));
            }
            return r;
        }
        if (valor instanceof List<?> lista) {
            List<Object> r = new ArrayList<>(lista.size());
            for (Object el : lista) r.add(normalizarFechas(el));
            return r;
        }
        return valor;
    }

    private boolean tieneLimit(List<Map<String, Object>> pipeline) {
        return pipeline.stream().anyMatch(s -> s.containsKey("$limit"));
    }

    private String stringDe(Object o) {
        return o == null ? null : o.toString();
    }

    private String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
