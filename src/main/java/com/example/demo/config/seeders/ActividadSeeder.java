package com.example.demo.config.seeders;

import com.example.demo.models.Actividad;
import com.example.demo.repositories.ActividadRepository;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.DocumentoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ActividadSeeder {

    @Autowired private ActividadRepository actividadRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private DocumentoRepository documentoRepository;

    public void seed() {
        if (actividadRepository.count() > 0) {
            log.info("[Seeder] Actividades ya existen, omitiendo");
            return;
        }

        String atcId = depto("ATC");
        String tecId = depto("TEC");
        String legId = depto("LEG");
        String opeId = depto("OPE");

        crearActividad("Recepción y validación de solicitud",
                "Validación inicial de solicitud, verificación de completitud del formulario e identidad del solicitante",
                atcId, 4, List.of("derivar", "observar"), true,
                Arrays.asList("Cédula de Identidad", "Formulario Oficial Completo", "Fotografía Reciente (3x4)"));

        crearActividad("Verificación de documentos del cliente",
                "Revision exhaustiva y validacion de documentos presentados por el solicitante, revisión de autenticidad",
                atcId, 8, List.of("derivar", "observar"), true,
                Arrays.asList("Cédula de Identidad", "Comprobante de Domicilio", "Certificado de Antecedentes Penales",
                        "Estados Bancarios (últimos 3 meses)", "Autorización de Consulta de Datos"));

        crearActividad("Evaluación de capacidad financiera",
                "Análisis de estados financieros, historial crediticio y capacidad de pago del solicitante",
                atcId, 12, List.of("derivar", "rechazar", "observar"), true,
                Arrays.asList("Declaración de Impuestos (IMP)", "Certificado de Ingresos",
                        "Estados Bancarios (últimos 3 meses)", "Consulta a Centrales de Riesgo",
                        "Certificado de No Adeudo", "Declaración de Patrimonio"));

        crearActividad("Inspección técnica en campo",
                "Visita técnica in situ para evaluar condiciones del inmueble, infraestructura y factibilidad técnica",
                tecId, 16, List.of("completar", "observar"), true,
                Arrays.asList("Título de Propiedad", "Comprobante de Domicilio", "Formulario Oficial Completo"));

        crearActividad("Elaboración de presupuesto técnico",
                "Preparación de presupuesto detallado con especificaciones técnicas, materiales y costo final",
                tecId, 8, List.of("completar"), true,
                Arrays.asList());

        crearActividad("Revisión y aprobación del contrato",
                "Revisión legal exhaustiva del contrato de servicio y aprobación formal de términos y condiciones",
                legId, 24, List.of("aprobar", "rechazar", "observar"), true,
                Arrays.asList("Poder Notarial", "Estatutos Sociales", "Certificado de Cámara de Comercio"));

        crearActividad("Verificación de antecedentes y referencias",
                "Validación de antecedentes penales, comerciales y laborales del solicitante",
                atcId, 6, List.of("derivar", "rechazar"), true,
                Arrays.asList("Certificado de Antecedentes Penales", "Certificado de Antecedentes Comerciales",
                        "Carta de Referencia Laboral"));

        crearActividad("Confirmación de autorizaciones y consentimientos",
                "Verificación de que todas las autorizaciones y consentimientos necesarios están firmados",
                legId, 4, List.of("aprobar", "observar"), true,
                Arrays.asList("Autorización de Consulta de Datos", "Poder Notarial"));

        crearActividad("Ejecución de trabajos técnicos",
                "Realización de trabajos técnicos especificados en el presupuesto bajo supervisión",
                opeId, 48, List.of("completar"), true,
                Arrays.asList());

        crearActividad("Inspección y validación de trabajos realizados",
                "Inspección de calidad de trabajos ejecutados y validación de cumplimiento de especificaciones",
                tecId, 8, List.of("completar", "observar"), true,
                Arrays.asList());

        crearActividad("Cierre del trámite y generación de documentación final",
                "Registro de cierre del trámite, generación de certificados y documentación de finalización",
                atcId, 4, List.of("completar"), true,
                Arrays.asList());

        crearActividad("Notificación formal de resolución al cliente",
                "Comunicación formal del resultado del trámite y entrega de documentación final al solicitante",
                atcId, 2, List.of("completar"), true,
                Arrays.asList());

        log.info("[Seeder] Actividades OK");
    }

    private String depto(String codigo) {
        return departamentoRepository.findByCodigo(codigo).map(d -> d.getId()).orElse(null);
    }

    private String getDocId(String nombre) {
        return documentoRepository.findByNombre(nombre).map(d -> d.getId()).orElse(null);
    }

    private void crearActividad(String nombre, String descripcion, String departamentoId,
                                 int slaHoras, List<String> salidasPosibles, boolean reutilizable,
                                 List<String> documentosNombres) {
        Actividad a = new Actividad();
        a.setNombre(nombre);
        a.setDescripcion(descripcion);
        a.setDepartamentoId(departamentoId);
        a.setSlaHoras(slaHoras);
        a.setSalidasPosibles(salidasPosibles);
        a.setReutilizable(reutilizable);
        a.setFechaCreacion(LocalDateTime.now());

        List<String> documentoIds = documentosNombres.stream()
                .map(this::getDocId)
                .filter(id -> id != null)
                .toList();
        a.setDocumentoIds(documentoIds);

        a.setDocumentosRequeridos(documentoIds.stream()
                .map(id -> new com.example.demo.models.RequisitoDocumento(
                        id, com.example.demo.models.RequisitoDocumento.CLIENTE, true))
                .toList());

        actividadRepository.save(a);
    }
}
