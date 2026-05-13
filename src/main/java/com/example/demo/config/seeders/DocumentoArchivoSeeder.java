package com.example.demo.config.seeders;

import com.example.demo.models.Actividad;
import com.example.demo.models.Documento;
import com.example.demo.models.DocumentoArchivo;
import com.example.demo.models.EstadoSeccion;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.RequisitoDocumento;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DocumentoArchivoRepository;
import com.example.demo.repositories.DocumentoRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.services.DocumentoArchivoService;
import com.example.demo.services.RequisitoDocumentoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parte 2 — Documentos de PRUEBA en el repositorio: sube un PDF por cada requisito
 * OBLIGATORIO del CLIENTE de las actividades de los primeros trámites, para que el
 * repositorio no esté vacío y "Ver" funcione. Requiere S3 (aws.enabled) + trámites +
 * actividades + repositorios sembrados. Idempotente (se omite si ya hay documentos).
 *
 * <p>Siembra los documentos según el PROGRESO real de cada trámite: por cada actividad
 * que ya alcanzó (sección no Bloqueada ni Pendiente-de-documentos) siembra los requisitos
 * del cliente, deduplicando por trámite (no-redundancia). Así un trámite avanzado/aprobado
 * tiene su repositorio acorde, y el de compuerta (008) queda sin documentos.
 */
@Component
@Slf4j
public class DocumentoArchivoSeeder {

    @Autowired private TramiteRepository tramiteRepo;
    @Autowired private NodoDiagramaRepository nodoRepo;
    @Autowired private ActividadRepository actividadRepo;
    @Autowired private DocumentoRepository documentoRepo;
    @Autowired private PoliticaNegocioRepository politicaRepo;
    @Autowired private DocumentoArchivoRepository docArchivoRepo;
    @Autowired private SeccionExpedienteRepository seccionRepo;
    @Autowired private RequisitoDocumentoService requisitoService;
    @Autowired private DocumentoArchivoService docService;

    @Value("${aws.enabled:false}") private boolean s3Enabled;

    public void seed() {
        if (docArchivoRepo.count() > 0) {
            log.info("[Seeder] DocumentoArchivo ya existen, se omite");
            return;
        }
        if (!s3Enabled) {
            log.info("[Seeder] DocumentoArchivo omitido (S3 deshabilitado)");
            return;
        }

        int creados = 0;
        // Sembrar según el PROGRESO real: por cada actividad que el trámite YA ALCANZÓ
        // (sección no Bloqueada ni Pendiente-de-documentos), sembrar los requisitos del
        // cliente, deduplicando por trámite (no-redundancia: un documento por requisito).
        // Así un trámite avanzado/aprobado tiene su repositorio acorde; el de compuerta
        // (ATC en "Pendiente de documentos") queda sin documentos automáticamente.
        for (Tramite t : tramiteRepo.findAll()) {
            if (t.getExpedienteId() == null) continue;
            Set<String> yaSembrados = new HashSet<>();
            for (SeccionExpediente s : seccionRepo.findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId())) {
                EstadoSeccion es = EstadoSeccion.from(s.getEstado());
                if (es == null || es == EstadoSeccion.BLOQUEADA || es == EstadoSeccion.PENDIENTE_DOCUMENTOS) continue;
                NodoDiagrama nodo = nodoRepo.findById(s.getNodoId()).orElse(null);
                if (nodo == null || nodo.getActividadId() == null) continue;
                Actividad act = actividadRepo.findById(nodo.getActividadId()).orElse(null);
                if (act == null) continue;
                for (RequisitoDocumento req : requisitoService.requisitosObligatoriosCliente(act)) {
                    if (req.getDocumentoId() == null || !yaSembrados.add(req.getDocumentoId())) continue;
                    Documento tipo = documentoRepo.findById(req.getDocumentoId()).orElse(null);
                    String nombre = tipo != null ? tipo.getNombre() : "Documento";
                    try {
                        docService.seedDocumento(t.getId(), t.getPoliticaId(), nodo.getActividadId(),
                                s.getNodoId(), req.getDocumentoId(), nombre, "PDF",
                                pdfPrueba(nombre, t.getCodigo()), "application/pdf");
                        creados++;
                    } catch (Exception e) {
                        log.warn("[Seeder] doc '{}' tramite {} fallo: {}", nombre, t.getCodigo(), e.getMessage());
                    }
                }
            }
        }
        log.info("[Seeder] DocumentoArchivo OK ({} documentos segun progreso)", creados);

        // Regla "observar requiere ≥1 documento": cada sección OBSERVADO debe tener
        // documentosObservados. Marcamos un documento EXISTENTE de su actividad (o sembramos
        // uno si no hubiera), así el cliente ve qué corregir (no "aún no hay documentos").
        int marcados = 0;
        for (Tramite t : tramiteRepo.findAll()) {
            if (t.getExpedienteId() == null) continue;
            for (SeccionExpediente s : seccionRepo.findByExpedienteIdOrderByOrdenSeccionAsc(t.getExpedienteId())) {
                if (EstadoSeccion.from(s.getEstado()) != EstadoSeccion.OBSERVADO) continue;
                if (s.getDocumentosObservados() != null && !s.getDocumentosObservados().isEmpty()) continue;
                NodoDiagrama nodo = nodoRepo.findById(s.getNodoId()).orElse(null);
                if (nodo == null || nodo.getActividadId() == null) continue;
                List<DocumentoArchivo> docs = docArchivoRepo
                        .findByTramiteIdAndActividadIdAndActivoTrue(t.getId(), nodo.getActividadId());
                String docId = docs.isEmpty() ? null : docs.get(0).getId();
                if (docId == null) {
                    Actividad act = actividadRepo.findById(nodo.getActividadId()).orElse(null);
                    List<RequisitoDocumento> reqs = requisitoService.requisitosObligatoriosCliente(act);
                    if (!reqs.isEmpty()) {
                        RequisitoDocumento req = reqs.get(0);
                        Documento tipo = documentoRepo.findById(req.getDocumentoId()).orElse(null);
                        String nombre = tipo != null ? tipo.getNombre() : "Documento";
                        try {
                            docId = docService.seedDocumento(t.getId(), t.getPoliticaId(), nodo.getActividadId(),
                                    s.getNodoId(), req.getDocumentoId(), nombre, "PDF",
                                    pdfPrueba(nombre, t.getCodigo() + "-obs"), "application/pdf");
                        } catch (Exception e) {
                            log.warn("[Seeder] observado tramite {} fallo: {}", t.getCodigo(), e.getMessage());
                        }
                    }
                }
                if (docId != null) {
                    s.setDocumentosObservados(new ArrayList<>(List.of(docId)));
                    seccionRepo.save(s);
                    marcados++;
                }
            }
        }
        log.info("[Seeder] Secciones OBSERVADO marcadas: {}", marcados);
    }

    /** PDF mínimo de prueba con texto; único por (nombre, código) para no chocar con el anti-duplicado por hash. */
    private byte[] pdfPrueba(String nombre, String codigo) {
        String txt = sanitizar(nombre);
        String pdf = "%PDF-1.4\n"
                + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 320 160]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
                + "4 0 obj<</Length 80>>stream\nBT /F1 13 Tf 20 110 Td (" + txt + ") Tj ET\nendstream endobj\n"
                + "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF\n"
                + "% uid:" + codigo + "-" + nombre + "\n";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }

    private String sanitizar(String s) {
        return s == null ? "Documento" : s.replaceAll("[()\\\\]", " ");
    }
}
