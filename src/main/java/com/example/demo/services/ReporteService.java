package com.example.demo.services;

import com.example.demo.dto.ReporteRequest;
import com.example.demo.models.Reporte;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.UsuarioRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReporteService {

    @Autowired private ReporteRepository reporteRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PoliticaNegocioRepository politicaRepository;

    private static final String STORAGE_DIR =
            System.getProperty("java.io.tmpdir") + "/tramites-reportes/";

    private static final String[] COLUMNAS = {"#", "Código", "Política", "Estado", "Cliente", "Inicio"};

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final DeviceRgb PDF_BRAND   = new DeviceRgb(79, 70, 229);
    private static final DeviceRgb PDF_WHITE   = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb PDF_TEXT    = new DeviceRgb(20, 28, 46);
    private static final DeviceRgb PDF_MUTED   = new DeviceRgb(120, 130, 150);
    private static final DeviceRgb PDF_ZEBRA   = new DeviceRgb(243, 245, 250);
    private static final DeviceRgb PDF_BORDER  = new DeviceRgb(226, 230, 240);

    private static String extensionPara(String formato) {
        if ("EXCEL".equalsIgnoreCase(formato) || "XLSX".equalsIgnoreCase(formato)) return "xlsx";
        if ("PDF".equalsIgnoreCase(formato)) return "pdf";
        return "csv";
    }

    public Reporte generarReporte(ReporteRequest request, String adminId) throws Exception {
        Path dirPath = Paths.get(STORAGE_DIR);
        if (!Files.exists(dirPath)) Files.createDirectories(dirPath);

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
        if (filtros == null) return base;
        Object estado = filtros.get("estado");
        if (estado instanceof String estadoStr && !estadoStr.isBlank()) {
            base.removeIf(t -> !estadoStr.equalsIgnoreCase(t.getEstadoActual()));
        }
        return base;
    }

    private Map<String, String> mapaPoliticas() {
        Map<String, String> m = new HashMap<>();
        politicaRepository.findAll().forEach(p -> m.put(p.getId(), p.getNombre()));
        return m;
    }

    private Map<String, String> mapaClientes() {
        Map<String, String> m = new HashMap<>();
        usuarioRepository.findAll().forEach(u -> {
            String nombre = ((u.getNombre() != null ? u.getNombre() : "")
                    + " " + (u.getApellido() != null ? u.getApellido() : "")).trim();
            m.put(u.getId(), nombre.isBlank() ? u.getEmail() : nombre);
        });
        return m;
    }

    private String nv(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private String[] valoresFila(Tramite t, int indice, Map<String, String> pol, Map<String, String> cli) {
        return new String[]{
                String.valueOf(indice),
                nv(t.getCodigo()),
                nv(pol.getOrDefault(t.getPoliticaId(), t.getPoliticaId())),
                nv(t.getEstadoActual()),
                nv(cli.getOrDefault(t.getClienteId(), t.getClienteId())),
                t.getFechaInicio() != null ? t.getFechaInicio().format(FMT) : "—",
        };
    }

    private void generarCSV(List<Tramite> tramites, Path filePath) throws Exception {
        Map<String, String> pol = mapaPoliticas();
        Map<String, String> cli = mapaClientes();
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", COLUMNAS)).append("\n");
        int i = 1;
        for (Tramite t : tramites) {
            String[] fila = valoresFila(t, i++, pol, cli);
            for (int c = 0; c < fila.length; c++) {
                if (c > 0) csv.append(",");
                csv.append("\"").append(fila[c].replace("\"", "'")).append("\"");
            }
            csv.append("\n");
        }
        Files.writeString(filePath, csv.toString());
    }

    private void generarExcel(List<Tramite> tramites, Path filePath) throws Exception {
        Map<String, String> pol = mapaPoliticas();
        Map<String, String> cli = mapaClientes();

        try (Workbook wb = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(filePath)) {
            Sheet sheet = wb.createSheet("Reporte");
            sheet.setDisplayGridlines(false);

            XSSFColor brand = new XSSFColor(new byte[]{(byte) 79, (byte) 70, (byte) 229}, null);
            XSSFColor zebra = new XSSFColor(new byte[]{(byte) 243, (byte) 245, (byte) 250}, null);

            // Título
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            Row tRow = sheet.createRow(0);
            Cell tCell = tRow.createCell(0);
            tCell.setCellValue("MOTOR · Reporte de Trámites");
            tCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLUMNAS.length - 1));

            Font subFont = wb.createFont();
            subFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subFont.setFontHeightInPoints((short) 9);
            CellStyle subStyle = wb.createCellStyle();
            subStyle.setFont(subFont);
            Row sRow = sheet.createRow(1);
            Cell sCell = sRow.createCell(0);
            sCell.setCellValue("Generado: " + LocalDateTime.now().format(FMT) + "  ·  Total: " + tramites.size());
            sCell.setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, COLUMNAS.length - 1));

            // Encabezado de tabla
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle headerStyle = (XSSFCellStyle) wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(brand);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            aplicarBordes(headerStyle);

            int headerRowIdx = 3;
            Row header = sheet.createRow(headerRowIdx);
            header.setHeightInPoints(20);
            for (int c = 0; c < COLUMNAS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(COLUMNAS[c]);
                cell.setCellStyle(headerStyle);
            }

            XSSFCellStyle cellStyle = (XSSFCellStyle) wb.createCellStyle();
            aplicarBordes(cellStyle);
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle zebraStyle = (XSSFCellStyle) wb.createCellStyle();
            aplicarBordes(zebraStyle);
            zebraStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            zebraStyle.setFillForegroundColor(zebra);
            zebraStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = headerRowIdx + 1;
            int i = 1;
            for (Tramite t : tramites) {
                Row row = sheet.createRow(rowIdx++);
                String[] fila = valoresFila(t, i++, pol, cli);
                XSSFCellStyle st = (i % 2 == 0) ? zebraStyle : cellStyle;
                for (int c = 0; c < fila.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(fila[c]);
                    cell.setCellStyle(st);
                }
            }

            sheet.createFreezePane(0, headerRowIdx + 1);
            int[] anchoFallback = {2200, 4200, 9000, 4200, 7000, 5200};
            for (int c = 0; c < COLUMNAS.length; c++) {
                try {
                    sheet.autoSizeColumn(c);
                    int w = sheet.getColumnWidth(c);
                    sheet.setColumnWidth(c, Math.min(w + 800, 12000));
                } catch (Exception ignore) {
                    sheet.setColumnWidth(c, c < anchoFallback.length ? anchoFallback[c] : 5000);
                }
            }

            wb.write(out);
        }
    }

    private void aplicarBordes(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private void generarPDF(List<Tramite> tramites, Path filePath) throws Exception {
        Map<String, String> pol = mapaPoliticas();
        Map<String, String> cli = mapaClientes();

        try (PdfWriter writer = new PdfWriter(Files.newOutputStream(filePath));
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf, PageSize.A4)) {

            document.setMargins(40, 36, 44, 36);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            document.add(new Paragraph("MOTOR · Gestión de Trámites")
                    .setFont(bold).setFontSize(17).setFontColor(PDF_BRAND).setMarginBottom(0));
            document.add(new Paragraph("Reporte de Trámites — Historial")
                    .setFont(bold).setFontSize(12).setFontColor(PDF_TEXT).setMarginTop(2).setMarginBottom(0));
            document.add(new Paragraph("Generado: " + LocalDateTime.now().format(FMT) + "   ·   Total de registros: " + tramites.size())
                    .setFont(regular).setFontSize(9).setFontColor(PDF_MUTED).setMarginTop(2).setMarginBottom(12));

            Table table = new Table(UnitValue.createPercentArray(new float[]{6, 17, 27, 17, 21, 18}))
                    .useAllAvailableWidth();

            for (String col : COLUMNAS) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(col).setFont(bold).setFontSize(9).setFontColor(PDF_WHITE))
                        .setBackgroundColor(PDF_BRAND)
                        .setBorder(Border.NO_BORDER)
                        .setPaddingTop(6).setPaddingBottom(6).setPaddingLeft(6).setPaddingRight(6));
            }

            int i = 1;
            for (Tramite t : tramites) {
                String[] fila = valoresFila(t, i, pol, cli);
                DeviceRgb bg = (i % 2 == 0) ? PDF_ZEBRA : PDF_WHITE;
                for (int c = 0; c < fila.length; c++) {
                    PdfFont f = (c == 1) ? bold : regular;
                    table.addCell(new com.itextpdf.layout.element.Cell()
                            .add(new Paragraph(fila[c]).setFont(f).setFontSize(8.5f).setFontColor(PDF_TEXT))
                            .setBackgroundColor(bg)
                            .setBorder(new SolidBorder(PDF_BORDER, 0.5f))
                            .setPaddingTop(5).setPaddingBottom(5).setPaddingLeft(6).setPaddingRight(6));
                }
                i++;
            }

            document.add(table);
            document.add(new Paragraph("Documento generado automáticamente por el sistema MOTOR.")
                    .setFont(regular).setFontSize(8).setFontColor(PDF_MUTED)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(16));
        }
    }

    public byte[] descargarReporte(String reporteId) throws Exception {
        Reporte reporte = reporteRepository.findById(reporteId)
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado"));
        Path path = Paths.get(reporte.getUrlArchivo());
        return Files.readAllBytes(path);
    }
}
