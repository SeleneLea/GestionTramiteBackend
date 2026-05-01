package com.example.demo.config.seeders;

import com.example.demo.models.LogAgente;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.LogAgenteRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class LogAgenteSeeder {

    @Autowired private LogAgenteRepository logAgenteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TramiteRepository tramiteRepository;

    public void seed() {
        if (logAgenteRepository.count() > 0) {
            log.info("[Seeder] Logs de agente ya existen, omitiendo");
            return;
        }

        String clienteId  = userId("cliente@cre.bo");
        String cliente2Id = userId("cliente2@cre.bo");
        String funcAtcId  = userId("func_atc@cre.bo");
        String adminId    = userId("admin@cre.bo");

        List<Tramite> tramites = tramiteRepository.findAll();
        String trm003 = tramites.stream().filter(t -> "TRM-2024-003".equals(t.getCodigo()))
                .findFirst().map(Tramite::getId).orElse(null);
        String trm004 = tramites.stream().filter(t -> "TRM-2024-004".equals(t.getCodigo()))
                .findFirst().map(Tramite::getId).orElse(null);

        LocalDateTime now = LocalDateTime.now();

        // Cliente consulta sobre estado de su tramite
        log(clienteId, "tramites", "Cliente", null,
                "Como va mi tramite TRM-2024-001?",
                "Su tramite TRM-2024-001 ha sido completado exitosamente. La conexion electrica fue instalada el 2024-04-20. "
                + "El numero de medidor instalado es MED20240421.",
                320f, true, now.minusDays(4));

        // Cliente pregunta que documentos necesita
        log(clienteId, "tramites", "Cliente", null,
                "Que documentos necesito para nueva conexion electrica?",
                "Para solicitar una nueva conexion electrica residencial necesita: 1) Cedula de identidad vigente, "
                + "2) Plano del inmueble actualizado, 3) Certificado de domicilio, 4) Formulario de solicitud llenado. "
                + "Puede iniciar el tramite en nuestra plataforma.",
                450f, true, now.minusDays(20));

        // Funcionario ATC consulta sobre flujo del tramite
        log(funcAtcId, "workflow", "Funcionario", trm003,
                "Cuales son los pasos siguientes para el tramite TRM-2024-003?",
                "El tramite TRM-2024-003 se encuentra en fase de inspeccion tecnica paralela. "
                + "Los pasos siguientes son: 1) Completar la inspeccion en campo (TEC), "
                + "2) Elaborar presupuesto (TEC) - ambas en paralelo, "
                + "3) Una vez completadas, el tramite avanzara a revision legal (LEG).",
                280f, true, now.minusDays(8));

        // Cliente 2 consulta sobre su tramite observado
        log(cliente2Id, "tramites", "Cliente", null,
                "Por que mi tramite tiene observaciones?",
                "Su tramite TRM-2024-005 tiene observaciones en el area legal. La observacion indica que se requiere "
                + "la firma notarial del documento de domicilio. Por favor, presente el documento notariado en "
                + "oficinas del area legal o subalo digitalmente.",
                510f, true, now.minusDays(6));

        // Admin consulta sobre generacion de diagrama con IA
        log(adminId, "workflow", "Administrador", null,
                "Como genero un diagrama de workflow para el proceso de reconexion por mora?",
                "Para generar el diagrama de workflow de reconexion por mora, puede usar la opcion 'Generar con IA' "
                + "en el modulo de diagramas. Describa el proceso paso a paso y el sistema generara automaticamente "
                + "los nodos, swimlanes y transiciones. Sugerencia de prompt: 'Proceso de reconexion de servicio "
                + "electrico tras verificacion de pago de deuda morosa, con validacion del area legal y notificacion al cliente'.",
                680f, true, now.minusDays(2));

        // Consulta que no fue util
        log(funcAtcId, "expedientes", "Funcionario", trm004,
                "Cuantos tramites estan en espera hoy?",
                "No tengo acceso en tiempo real al conteo actual de tramites en espera. "
                + "Por favor consulte el panel de metricas del modulo de reportes para informacion actualizada.",
                190f, false, now.minusDays(1));

        log.info("[Seeder] Logs de agente IA OK (6 logs, distintos modulos y roles)");
    }

    private void log(String usuarioId, String modulo, String rol, String tramiteId,
                     String consulta, String respuesta, float tiempoMs, boolean fueUtil,
                     LocalDateTime timestamp) {
        LogAgente l = new LogAgente();
        l.setUsuarioId(usuarioId);
        l.setContextoModulo(modulo);
        l.setContextoRol(rol);
        l.setContextoTramiteId(tramiteId);
        l.setConsultaUsuario(consulta);
        l.setRespuestaAgente(respuesta);
        l.setTiempoRespuestaMs(tiempoMs);
        l.setFueUtil(fueUtil);
        l.setTimestamp(timestamp);
        logAgenteRepository.save(l);
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
