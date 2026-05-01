package com.example.demo.services;

import com.example.demo.dto.AgenteRequest;
import com.example.demo.dto.AgenteResponse;
import com.example.demo.models.PoliticaNegocio;
import com.example.demo.models.Tramite;
import com.example.demo.repositories.PoliticaNegocioRepository;
import com.example.demo.repositories.TramiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Base de conocimiento local del agente CU-31. Construye respuestas usando
 * datos REALES del sistema (políticas activas, trámite del usuario, rol y módulo).
 * Se usa como fallback cuando el microservicio n8n/RAG no responde, y para
 * detectar intenciones simples sin salir del backend.
 */
@Service
public class AgenteAsistenciaService {

    @Autowired
    private PoliticaNegocioRepository politicaRepo;

    @Autowired
    private TramiteRepository tramiteRepo;

    public AgenteResponse responder(AgenteRequest req, String rol) {
        String consulta = req.getConsulta() == null ? "" : req.getConsulta().toLowerCase(Locale.ROOT);
        String modulo = req.getModuloActivo() == null ? "" : req.getModuloActivo().toLowerCase(Locale.ROOT);
        String tramiteId = req.getTramiteIdOpcional();

        AgenteResponse resp = new AgenteResponse();
        resp.setFuente("kb-local");

        if (esSaludo(consulta)) {
            resp.setRespuesta(saludoContextual(modulo, rol));
            resp.setAccion(sugerirAccion(modulo, rol));
            return resp;
        }

        if (tramiteId != null && !tramiteId.isBlank() &&
            (consulta.contains("estado") || consulta.contains("mi tramite") || consulta.contains("mi trámite") || consulta.contains("seguim"))) {
            Optional<Tramite> t = tramiteRepo.findById(tramiteId);
            if (t.isPresent()) {
                Tramite tr = t.get();
                resp.setRespuesta(String.format(
                    "Tu trámite %s está en estado: %s. Nodo actual: %s. Para más detalle, abre la línea de tiempo.",
                    tr.getCodigo(), tr.getEstadoActual(),
                    tr.getNodoActualId() != null ? tr.getNodoActualId() : "(sin asignar)"));
                AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
                a.setLabel("Ver línea de tiempo");
                a.setRuta("/cliente/tramites/" + tr.getId() + "/timeline");
                a.setTipo("navegar");
                resp.setAccion(a);
                return resp;
            }
        }

        if (consulta.contains("politica") || consulta.contains("política") || consulta.contains("flujo") || consulta.contains("workflow")) {
            List<PoliticaNegocio> activas = politicaRepo.findByEstado("ACTIVA");
            if (activas.isEmpty()) {
                activas = politicaRepo.findAll();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Políticas de negocio disponibles (").append(activas.size()).append("): ");
            int max = Math.min(5, activas.size());
            for (int i = 0; i < max; i++) {
                sb.append(activas.get(i).getNombre());
                if (i < max - 1) sb.append(", ");
            }
            if (activas.size() > max) sb.append(", …");
            sb.append(". Cada política tiene su propio flujo de actividades (swimlanes).");
            resp.setRespuesta(sb.toString());
            if (esAdmin(rol)) {
                AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
                a.setLabel("Diseñar nuevo flujo");
                a.setRuta("/admin/diagramas");
                a.setTipo("navegar");
                resp.setAccion(a);
            }
            return resp;
        }

        if (consulta.contains("expediente") || consulta.contains("seccion") || consulta.contains("sección") || consulta.contains("formulario")) {
            resp.setRespuesta("El expediente digital es único por trámite. Cada nodo tiene su sección y solo el funcionario del nodo activo puede editarla. Puedes dictar por voz para rellenar campos.");
            if (esFuncionario(rol)) {
                AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
                a.setLabel("Ir a mi bandeja");
                a.setRuta("/funcionario/bandeja-entrada");
                a.setTipo("navegar");
                resp.setAccion(a);
            }
            return resp;
        }

        if (consulta.contains("nodo") || consulta.contains("decis") || consulta.contains("fork") || consulta.contains("join") || consulta.contains("paralelo")) {
            resp.setRespuesta("El motor soporta nodos: actividad, decisión (sí/no), fork (separa), join (espera todos) y nodos de inicio/fin. Para flujos iterativos puedes regresar a un nodo anterior.");
            if (esAdmin(rol)) {
                AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
                a.setLabel("Abrir editor de diagramas");
                a.setRuta("/admin/diagramas");
                a.setTipo("navegar");
                resp.setAccion(a);
            }
            return resp;
        }

        if (consulta.contains("voz") || consulta.contains("dict") || consulta.contains("transcrib")) {
            resp.setRespuesta("Dentro del expediente, presiona el ícono de micrófono para dictar. La IA transcribirá y rellenará solo los campos de tu sección activa. Puedes editar antes de guardar.");
            return resp;
        }

        if (consulta.contains("cuello") || consulta.contains("metrica") || consulta.contains("métrica") || consulta.contains("reporte")) {
            resp.setRespuesta("En Métricas verás trámites activos por estado, tiempos por actividad y los cuellos de botella detectados automáticamente cuando superan el SLA.");
            if (esAdmin(rol) || esFuncionario(rol)) {
                AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
                a.setLabel("Abrir dashboard");
                a.setRuta("/admin/metricas");
                a.setTipo("navegar");
                resp.setAccion(a);
            }
            return resp;
        }

        if (consulta.contains("notif")) {
            resp.setRespuesta("Recibirás una notificación cuando tu trámite cambie de estado o cuando se te asigne uno nuevo. Los clientes las reciben push en la app móvil; los funcionarios, en la plataforma web.");
            return resp;
        }

        if (consulta.contains("colabor")) {
            resp.setRespuesta("Como administrador puedes invitar a otros administradores o funcionarios a co-diseñar un diagrama. Cada cambio queda versionado.");
            if (esAdmin(rol)) {
                AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
                a.setLabel("Ver diagramas");
                a.setRuta("/admin/diagramas");
                a.setTipo("navegar");
                resp.setAccion(a);
            }
            return resp;
        }

        // Fuera de alcance
        resp.setRespuesta("Solo puedo asistirte con funcionalidades de esta plataforma (trámites, expedientes, flujos y métricas). Reformula tu pregunta o consulta el manual.");
        return resp;
    }

    private boolean esSaludo(String c) {
        return c.isBlank() || c.contains("hola") || c.contains("ayuda") || c.contains("ayúdame") ||
               c.contains("buenas") || c.contains("buenos") || c.contains("hi") || c.contains("hello");
    }

    private boolean esAdmin(String rol) {
        return rol != null && rol.toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private boolean esFuncionario(String rol) {
        return rol != null && rol.toUpperCase(Locale.ROOT).contains("FUNCIONARIO");
    }

    private String saludoContextual(String modulo, String rol) {
        if (modulo.contains("diagrama") && esAdmin(rol)) {
            return "Estás en diseño de flujos. ¿Necesitas ayuda para agregar un nodo, una decisión o un fork paralelo?";
        }
        if (modulo.contains("expediente") && esFuncionario(rol)) {
            return "Estás revisando un expediente. Solo puedes editar tu sección activa; usa la voz para llenar campos rápido.";
        }
        if (modulo.contains("metrica") || modulo.contains("dashboard")) {
            return "Estás en el dashboard. Puedo explicarte los cuellos de botella detectados o las métricas por departamento.";
        }
        if (modulo.contains("tramite") || modulo.contains("trámite")) {
            return "Estás en trámites. ¿Quieres consultar el estado de uno, iniciar uno nuevo o entender su flujo?";
        }
        return "Hola, soy tu asistente del sistema de trámites. Puedo guiarte por políticas, expedientes, métricas o flujos.";
    }

    private AgenteResponse.AccionDirecta sugerirAccion(String modulo, String rol) {
        AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
        a.setTipo("navegar");
        if (esAdmin(rol)) {
            a.setLabel("Ir a dashboard");
            a.setRuta("/admin/dashboard");
        } else if (esFuncionario(rol)) {
            a.setLabel("Abrir mi bandeja");
            a.setRuta("/funcionario/bandeja-entrada");
        } else {
            a.setLabel("Ver mis trámites");
            a.setRuta("/cliente/tramites");
        }
        return a;
    }
}
