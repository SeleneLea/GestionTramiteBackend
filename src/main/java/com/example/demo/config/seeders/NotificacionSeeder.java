package com.example.demo.config.seeders;

import com.example.demo.models.Notificacion;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.NotificacionRepository;
import com.example.demo.repositories.TramiteRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class NotificacionSeeder {

    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (notificacionRepository.count() > 0) {
            log.info("[Seeder] Notificaciones ya existen, omitiendo");
            return;
        }

        String clienteId  = userId("cliente@cre.bo");
        String cliente2Id = userId("cliente2@cre.bo");
        String cliente3Id = userId("cliente3@cre.bo");
        String funcAtcId  = userId("func_atc@cre.bo");
        String funcTecId  = userId("funcionario@cre.bo");
        String funcLegId  = userId("func_leg@cre.bo");

        List<Tramite> tramites = tramiteRepository.findAll();
        String trm001 = tramiteId(tramites, "TRM-2024-001");
        String trm002 = tramiteId(tramites, "TRM-2024-002");
        String trm003 = tramiteId(tramites, "TRM-2024-003");
        String trm004 = tramiteId(tramites, "TRM-2024-004");
        String trm005 = tramiteId(tramites, "TRM-2024-005");
        String trm006 = tramiteId(tramites, "TRM-2024-006");

        LocalDateTime now = LocalDateTime.now();

        // --- Tramite 001 COMPLETADO ---
        notif(clienteId, trm001, "EMAIL", "INFO",
                "Tramite TRM-2024-001 completado",
                "Su solicitud de nueva conexion electrica ha sido procesada y aprobada. El medidor fue instalado correctamente.",
                true, "ENVIADO", 1, now.minusDays(5), now.minusDays(5));
        notif(clienteId, trm001, "IN_APP", "INFO",
                "Conexion electrica instalada",
                "El tecnico Roberto Vargas ha completado la instalacion. Numero de medidor: MED20240421.",
                true, "ENVIADO", 1, now.minusDays(5), now.minusDays(4));

        // --- Tramite 002 RECHAZADO ---
        notif(clienteId, trm002, "EMAIL", "ALERTA",
                "Tramite TRM-2024-002 rechazado",
                "Su tramite ha sido rechazado. Motivo: documentacion insuficiente. Por favor, presente CI y plano actualizado.",
                true, "ENVIADO", 1, now.minusDays(10), now.minusDays(10));
        notif(clienteId, trm002, "SMS", "ALERTA",
                "Tramite rechazado",
                "TRM-2024-002 rechazado. Revise su correo para mas detalles.",
                true, "ENVIADO", 1, now.minusDays(10), now.minusDays(9));

        // --- Tramite 003 EN PROCESO ---
        notif(cliente2Id, trm003, "IN_APP", "ACCION_REQUERIDA",
                "Inspeccion tecnica programada",
                "El tecnico Carlos Lima visitara su domicilio el dia de manana para realizar la inspeccion tecnica.",
                false, "ENVIADO", 1, now.minusDays(3), null);
        notif(funcTecId, trm003, "IN_APP", "RECORDATORIO",
                "Inspeccion pendiente TRM-2024-003",
                "Recuerde completar el formulario de inspeccion y subir las fotos del sitio.",
                false, "ENVIADO", 1, now.minusDays(1), null);

        // --- Tramite 004 INICIADO ---
        notif(cliente3Id, trm004, "EMAIL", "INFO",
                "Tramite TRM-2024-004 recibido",
                "Hemos recibido su solicitud de nueva conexion electrica. En breve un funcionario revisara su documentacion.",
                false, "ENVIADO", 1, now, null);
        notif(funcAtcId, trm004, "IN_APP", "ACCION_REQUERIDA",
                "Nueva solicitud asignada",
                "Se le ha asignado el tramite TRM-2024-004 de Rosa Flores. Por favor revise la documentacion.",
                false, "ENVIADO", 1, now, null);

        // --- Tramite 005 OBSERVADO ---
        notif(cliente2Id, trm005, "EMAIL", "ALERTA",
                "Observaciones en su tramite TRM-2024-005",
                "El area legal ha registrado observaciones en el contrato de su tramite. Por favor, contacte a la oficina.",
                false, "ENVIADO", 1, now.minusDays(7), null);
        notif(funcLegId, trm005, "IN_APP", "ACCION_REQUERIDA",
                "Contrato con observaciones pendientes",
                "El tramite TRM-2024-005 tiene observaciones sin resolver. Requiere firma notarial del solicitante.",
                false, "ENVIADO", 1, now.minusDays(7), null);

        // --- Tramite 006 CANCELADO ---
        notif(cliente3Id, trm006, "EMAIL", "INFO",
                "Tramite TRM-2024-006 cancelado",
                "Su tramite ha sido cancelado segun su solicitud. Si desea reiniciar el proceso puede hacerlo en cualquier momento.",
                true, "ENVIADO", 1, now.minusDays(18), now.minusDays(17));

        // --- Notificacion con envio FALLIDO (para probar el estado) ---
        notif(cliente2Id, trm003, "SMS", "RECORDATORIO",
                "Actualizacion de su tramite",
                "Su tramite TRM-2024-003 avanza correctamente. Lo mantendemos informado.",
                false, "FALLIDO", 3, now.minusDays(2), null);

        log.info("[Seeder] Notificaciones OK (todos canales, tipos y estados)");
    }

    private void notif(String destinatarioId, String tramiteId, String canal, String tipo,
                       String titulo, String mensaje, boolean leida, String estadoEnvio,
                       int intentos, LocalDateTime creacion, LocalDateTime fechaLeida) {
        Notificacion n = new Notificacion();
        n.setDestinatarioId(destinatarioId);
        n.setTramiteId(tramiteId);
        n.setCanal(canal);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        n.setLeida(leida);
        n.setEstadoEnvio(estadoEnvio);
        n.setIntentosEnvio(intentos);
        n.setFechaCreacion(creacion);
        n.setFechaLeida(fechaLeida);
        notificacionRepository.save(n);
    }

    private String tramiteId(List<Tramite> tramites, String codigo) {
        return tramites.stream().filter(t -> codigo.equals(t.getCodigo()))
                .findFirst().map(Tramite::getId).orElse(null);
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
