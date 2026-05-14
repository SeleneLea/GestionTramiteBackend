package com.example.demo.services;

import com.example.demo.dto.ReporteRequest;
import com.example.demo.models.Reporte;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReporteService {

    @Autowired
    private ReporteRepository reporteRepository;

    @Autowired
    private TramiteRepository tramiteRepository;

    private static final String STORAGE_DIR = "./uploads/reportes/";

    /** Mismas columnas que el CSV: cabecera única para CSV, Excel y PDF. */
    private static final String[] COLUMNAS = {"ID", "Estado", "ClienteID", "FechaInicio"};

    /** Extensión de archivo según el formato solicitado. */
    private static String extensionPara(String formato) {
        if ("EXCEL".equalsIgnoreCase(formato) || "XLSX".equalsIgnoreCase(formato)) {
            return "xlsx";
        }
        if ("PDF".equalsIgnoreCase(formato)) {
            return "pdf";
        }
        return "csv";
    }

    public Reporte generarReporte(ReporteRequest request, String adminId) throws Exception {
        Path dirPath = Paths.get(STORAGE_DIR);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        String formato = request.getFormato() != null ? request.getFormato() : "CSV";
        String fileName = UUID.randomUUID() + "." + extensionPara(formato);
        Path filePath = dirPath.resolve(fileName);

        List<Tramite> tramites = filtrarTramites(request.getFiltros());

        if ("CSV".equalsIgnoreCase(formato)) {
            generarCSV(tramites, filePath);
        } else if ("EXCEL".equalsIgnoreCase(formato) || "XLSX".equalsIgnoreCase(formato)) {
            generarExcel(tramites, filePath);
        } else if ("PDF".equalsIgnoreCase(formato)) {
            generarPDF(tramites, filePath);
        } else {
            throw new IllegalArgumentException("Formato no soportado: " + formato);
        }

        Reporte reporte = new Reporte();
        reporte.setGeneradoPorId(adminId);
        reporte.setTipo(request.getTipo());
        reporte.setFiltros(request.getFiltros());
        reporte.setFormato(formato);
        reporte.setUrlArchivo(filePath.toString());
        reporte.setFechaGeneracion(LocalDateTime.now());

        return reporteRepository.save(reporte);
    }

    private List<Tramite> filtrarTramites(Map<String, Object> filtros) {
        List<Tramite> base = new ArrayList<>(tramiteRepository.findAll());
        if (filtros == null) {
            return base;
        }

        Object estado = filtros.get("estado");
        if (estado instanceof String estadoStr && !estadoStr.isBlank()) {
            base.removeIf(t -> !estadoStr.equalsIgnoreCase(t.getEstadoActual()));
        }

        return base;
    }

    private void generarCSV(List<Tramite> tramites, Path filePath) throws Exception {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", COLUMNAS)).append("\n");
        for (Tramite t : tramites) {
            String[] fila = valoresFila(t);
            csv.append(String.join(",", fila)).append("\n");
        }
        Files.writeString(filePath, csv.toString());
    }

    /** Valores de una fila, en el mismo orden y formato que COLUMNAS. */
    private String[] valoresFila(Tramite t) {
        return new String[]{
                t.getId() != null ? t.getId() : "",
                t.getEstadoActual() != null ? t.getEstadoActual() : "",
                t.getClienteId() != null ? t.getClienteId() : "",
                t.getFechaInicio() != null ? t.getFechaInicio().toString() : ""
        };
    }

    private void generarExcel(List<Tramite> tramites, Path filePath) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("Reporte");

            // Cabecera en negrita
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int c = 0; c < COLUMNAS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(COLUMNAS[c]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Tramite t : tramites) {
                Row row = sheet.createRow(rowIdx++);
                String[] fila = valoresFila(t);
                for (int c = 0; c < fila.length; c++) {
                    row.createCell(c).setCellValue(fila[c]);
                }
            }

            for (int c = 0; c < COLUMNAS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
        }
    }

    private void generarPDF(List<Tramite> tramites, Path filePath) throws Exception {
        try (PdfWriter writer = new PdfWriter(Files.newOutputStream(filePath));
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            document.add(new Paragraph("Reporte de Trámites — Historial (CU-26)"));

            Table table = new Table(UnitValue.createPercentArray(COLUMNAS.length))
                    .useAllAvailableWidth();

            for (String col : COLUMNAS) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(col)));
            }

            for (Tramite t : tramites) {
                String[] fila = valoresFila(t);
                for (String valor : fila) {
                    table.addCell(new com.itextpdf.layout.element.Cell()
                            .add(new Paragraph(valor)));
                }
            }

            document.add(table);
        }
    }

    public byte[] descargarReporte(String reporteId) throws Exception {
        Reporte reporte = reporteRepository.findById(reporteId)
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado"));

        Path path = Paths.get(reporte.getUrlArchivo());
        return Files.readAllBytes(path);
    }
}
