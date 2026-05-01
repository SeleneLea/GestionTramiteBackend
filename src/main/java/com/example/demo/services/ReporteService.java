package com.example.demo.services;

import com.example.demo.dto.ReporteRequest;
import com.example.demo.models.Reporte;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.ReporteRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public Reporte generarReporte(ReporteRequest request, String adminId) throws Exception {
        Path dirPath = Paths.get(STORAGE_DIR);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        String formato = request.getFormato() != null ? request.getFormato() : "CSV";
        String fileName = UUID.randomUUID() + "." + formato.toLowerCase();
        Path filePath = dirPath.resolve(fileName);

        List<Tramite> tramites = filtrarTramites(request.getFiltros());

        if ("CSV".equalsIgnoreCase(formato)) {
            generarCSV(tramites, filePath);
        } else {
            throw new IllegalArgumentException("Formato no implementado aun en esta fase");
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
        csv.append("ID,Estado,ClienteID,FechaInicio\n");
        for (Tramite t : tramites) {
            csv.append(t.getId()).append(",")
                    .append(t.getEstadoActual()).append(",")
                    .append(t.getClienteId()).append(",")
                    .append(t.getFechaInicio() != null ? t.getFechaInicio() : "")
                    .append("\n");
        }
        Files.writeString(filePath, csv.toString());
    }

    public byte[] descargarReporte(String reporteId) throws Exception {
        Reporte reporte = reporteRepository.findById(reporteId)
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado"));

        Path path = Paths.get(reporte.getUrlArchivo());
        return Files.readAllBytes(path);
    }
}
