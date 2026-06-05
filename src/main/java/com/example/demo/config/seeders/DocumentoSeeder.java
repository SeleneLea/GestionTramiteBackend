package com.example.demo.config.seeders;

import com.example.demo.models.Documento;
import com.example.demo.repositories.DocumentoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class DocumentoSeeder {

    @Autowired private DocumentoRepository documentoRepository;

    public void seed() {
        if (documentoRepository.count() > 0) {
            log.info("[Seeder] Documentos ya existen, omitiendo");
            return;
        }

        crearDocumento("Cédula de Identidad",
                "Documento oficial de identificación personal expedido por la autoridad competente");
        crearDocumento("Pasaporte",
                "Documento de viaje internacional que certifica la identidad del portador");
        crearDocumento("Licencia de Conducir",
                "Permiso oficial para conducir vehículos automotores");

        crearDocumento("Comprobante de Domicilio",
                "Documento que acredita el lugar de residencia (servicios públicos, contrato de arrendamiento)");
        crearDocumento("Título de Propiedad",
                "Documento que acredita la propiedad de un inmueble o bien");
        crearDocumento("Contrato de Arrendamiento",
                "Acuerdo legal entre arrendador y arrendatario para ocupación de inmueble");
        crearDocumento("Certificado de Deuda Predial",
                "Documento que certifica los impuestos prediales pagados o adeudados");

        crearDocumento("Declaración de Impuestos (IMP)",
                "Reporte anual de ingresos y gastos presentado ante la autoridad tributaria");
        crearDocumento("Certificado de Ingresos",
                "Documento expedido por el empleador o entidad que certifica los ingresos del solicitante");
        crearDocumento("Estados Bancarios (últimos 3 meses)",
                "Extractos de cuentas bancarias que demuestran movimientos financieros");
        crearDocumento("Balance General",
                "Estado financiero que muestra activos, pasivos y patrimonio de una empresa");
        crearDocumento("Declaración de Patrimonio",
                "Documento que lista todos los bienes y deudas de una persona física");

        crearDocumento("Constancia Laboral",
                "Documento expedido por el empleador que certifica relación laboral vigente");
        crearDocumento("Certificado de Cotización",
                "Documento que acredita aportes a seguridad social o pensión");
        crearDocumento("Carta de Referencia Laboral",
                "Documento de recomendación de empleador anterior o actual");

        crearDocumento("Certificado de Antecedentes Penales",
                "Documento oficial que certifica historial penal (o ausencia del mismo)");
        crearDocumento("Certificado de Antecedentes Comerciales",
                "Documento que verifica historial de crédito y obligaciones comerciales");
        crearDocumento("Consulta a Centrales de Riesgo",
                "Reporte de calificación crediticia y deudas de una persona");

        crearDocumento("Diploma o Título Profesional",
                "Documento que acredita conclusión de estudios académicos");
        crearDocumento("Certificado de Estudios",
                "Documento oficial de institución educativa verificando asistencia y calificaciones");
        crearDocumento("Cédula de Profesional",
                "Documento que acredita calificación para ejercer profesión regulada");

        crearDocumento("RIF o Registro Comercial",
                "Documento de identificación tributaria o mercantil de la empresa");
        crearDocumento("Estatutos Sociales",
                "Documento que define estructura legal y funcionamiento de la empresa");
        crearDocumento("Acta Constitutiva",
                "Documento legal que formaliza la creación de la sociedad mercantil");
        crearDocumento("Certificado de Cámara de Comercio",
                "Documento que certifica registro y estado actual de la empresa");
        crearDocumento("Poder Notarial",
                "Documento notarizado que autoriza a tercero a actuar en nombre del otorgante");

        crearDocumento("Certificado de Salud",
                "Documento expedido por profesional médico certificando estado de salud");
        crearDocumento("Historial Médico",
                "Registro de consultas, diagnósticos y tratamientos médicos");
        crearDocumento("Carnet de Vacunación",
                "Documento que acredita vacunas aplicadas");

        crearDocumento("Formulario Oficial Completo",
                "Formulario de solicitud diligenciado según requisitos institucionales");
        crearDocumento("Fotografía Reciente (3x4)",
                "Fotografía a color de rostro del solicitante para identificación");
        crearDocumento("Autorización de Consulta de Datos",
                "Documento que autoriza consulta de información personal en bases de datos");
        crearDocumento("Certificado de No Adeudo",
                "Documento que certifica ausencia de obligaciones financieras");

        log.info("[Seeder] Documentos OK");
    }

    private void crearDocumento(String nombre, String descripcion) {
        if (documentoRepository.findByNombre(nombre).isEmpty()) {
            Documento d = new Documento();
            d.setNombre(nombre);
            d.setDescripcion(descripcion);
            d.setActivo(true);
            d.setFechaCreacion(LocalDateTime.now());
            documentoRepository.save(d);
        }
    }
}
