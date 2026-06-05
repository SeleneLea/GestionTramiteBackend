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
import java.util.Map;
import java.util.Optional;

@Service
public class AgenteAsistenciaService {

    @Autowired
    private PoliticaNegocioRepository politicaRepo;

    @Autowired
    private TramiteRepository tramiteRepo;

    @Autowired
    private IaProxyService iaProxy;

    private static final double UMBRAL_CONFIANZA = 0.45;

    public AgenteResponse responderInteligente(AgenteRequest req, String rol) {
        try {
            Map<String, Object> r = iaProxy.clasificarIntencion(req.getConsulta());
            Object intObj = r.get("intencion");
            String intencion = intObj != null ? intObj.toString() : null;
            double confianza = (r.get("confianza") instanceof Number n) ? n.doubleValue() : 0.0;
            if (intencion != null && !intencion.isBlank()
                    && confianza >= UMBRAL_CONFIANZA
                    && !"fuera_de_alcance".equals(intencion)) {
                return responderPorIntencion(intencion, req, rol);
            }
        } catch (Exception e) {
        }
        AgenteResponse llm = responderConLlm(req, rol);
        return llm != null ? llm : responder(req, rol);
    }

    private AgenteResponse responderConLlm(AgenteRequest req, String rol) {
        try {
            Map<String, Object> r = iaProxy.asistenteLlm(req.getConsulta(), construirContexto(req, rol));
            Object t = r.get("respuesta");
            String texto = t != null ? t.toString() : null;
            if (texto != null && !texto.isBlank()) {
                AgenteResponse resp = new AgenteResponse();
                resp.setRespuesta(texto);
                resp.setFuente("llm-bedrock");
                String modulo = req.getModuloActivo() == null ? "" : req.getModuloActivo().toLowerCase(Locale.ROOT);
                resp.setAccion(sugerirAccion(modulo, rol));
                return resp;
            }
        } catch (Exception e) {
        }
        return null;
    }

    private String construirContexto(AgenteRequest req, String rol) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rol del usuario: ").append(rol != null ? rol : "CLIENTE").append(".\n");
        if (req.getModuloActivo() != null && !req.getModuloActivo().isBlank()) {
            sb.append("Modulo activo en la app: ").append(req.getModuloActivo()).append(".\n");
        }
        List<PoliticaNegocio> activas = politicaRepo.findByEstado("activa");
        if (activas.isEmpty()) activas = politicaRepo.findAll();
        if (!activas.isEmpty()) {
            sb.append("Tramites disponibles para iniciar: ");
            int max = Math.min(8, activas.size());
            for (int i = 0; i < max; i++) {
                sb.append(activas.get(i).getNombre());
                if (i < max - 1) sb.append(", ");
            }
            sb.append(".\n");
        }
        String tramiteId = req.getTramiteIdOpcional();
        if (tramiteId != null && !tramiteId.isBlank()) {
            Optional<Tramite> t = tramiteRepo.findById(tramiteId);
            if (t.isPresent()) {
                Tramite tr = t.get();
                sb.append("Tramite del usuario: ").append(tr.getCodigo())
                  .append(" (estado: ").append(tr.getEstadoActual()).append(").\n");
            }
        }
        return sb.toString();
    }

    public AgenteResponse responderPorIntencion(String intencion, AgenteRequest req, String rol) {
        String consulta = req.getConsulta() == null ? "" : req.getConsulta().toLowerCase(Locale.ROOT);
        String modulo = req.getModuloActivo() == null ? "" : req.getModuloActivo().toLowerCase(Locale.ROOT);
        String tramiteId = req.getTramiteIdOpcional();

        AgenteResponse resp = new AgenteResponse();
        resp.setFuente("ml-tf");

        switch (intencion) {
            case "recomendar_tramite":
                return recomendarTramite(consulta);

            case "listar_tramites":
                return listarTramitesDisponibles();

            case "saludo":
                resp.setRespuesta(saludoContextual(modulo, rol));
                resp.setAccion(sugerirAccion(modulo, rol));
                return resp;

            case "capacidades":
                resp.setRespuesta("Puedo ayudarte a: recomendarte el trámite que necesitas (descríbeme tu caso), ver el estado de tus trámites, decirte qué documentos te piden, cómo iniciar uno y tus notificaciones. ¿Con qué empezamos?");
                resp.setAccion(accionNavegar("Ver mis trámites", "/cliente/tramites"));
                return resp;

            case "estado_tramite":
                if (tramiteId != null && !tramiteId.isBlank()) {
                    Optional<Tramite> t = tramiteRepo.findById(tramiteId);
                    if (t.isPresent()) {
                        Tramite tr = t.get();
                        resp.setRespuesta(String.format(
                                "Tu trámite %s está en estado: %s. Abre su línea de tiempo para más detalle.",
                                tr.getCodigo(), tr.getEstadoActual()));
                        resp.setAccion(accionNavegar("Ver mis trámites", "/cliente/tramites"));
                        return resp;
                    }
                }
                resp.setRespuesta("Puedes ver el estado de todos tus trámites en \"Mis trámites\". Toca uno para ver su línea de tiempo.");
                resp.setAccion(accionNavegar("Ver mis trámites", "/cliente/tramites"));
                return resp;

            case "documentos":
                resp.setRespuesta("Si tu trámite pide documentos, los verás en \"Completar documentos\" o al abrir el trámite. Sube cada requisito (foto o archivo); cuando estén todos, el trámite avanza solo.");
                resp.setAccion(accionNavegar("Completar documentos", "/cliente/tramites"));
                return resp;

            case "como_iniciar":
                resp.setRespuesta("Para iniciar un trámite ve a \"Explorar\", elige el que necesitas y toca \"Iniciar\". O cuéntame qué necesitas y te recomiendo cuál.");
                resp.setAccion(accionNavegar("Explorar trámites", "/cliente/tramites"));
                return resp;

            case "notificaciones":
                resp.setRespuesta("Recibirás una notificación cuando tu trámite cambie de estado o se te asigne uno nuevo. Revísalas en la campana de la app.");
                resp.setAccion(accionNavegar("Ver notificaciones", "/cliente/notificaciones"));
                return resp;

            case "func_bandeja":
                resp.setRespuesta("Tu bandeja de entrada reúne los trámites asignados a tu departamento: 1) abre \"Bandeja de entrada\", 2) verás la lista con código, política y antigüedad, 3) ordénala por fecha de llegada o por la prioridad que sugiere la IA (más urgente arriba). Toca cualquier trámite para abrirlo y empezar a atenderlo.");
                resp.setAccion(accionNavegar("Ir a mi bandeja", "/funcionario/bandeja"));
                return resp;

            case "func_atender":
                resp.setRespuesta("Para atender un trámite: 1) ábrelo desde tu bandeja, 2) revisa el expediente y los documentos del cliente, 3) completa tu actividad llenando los campos de tu sección, 4) guarda. Al cerrar tu actividad el trámite avanza solo al siguiente nodo del flujo.");
                resp.setAccion(accionNavegar("Ir a mi bandeja", "/funcionario/bandeja"));
                return resp;

            case "func_decidir":
                resp.setRespuesta("Al cerrar tu actividad eliges la decisión: 1) Aprobar para que continúe, 2) Rechazar para detenerlo, 3) Derivar para enviarlo a otro departamento, u 4) Observar para devolverlo al cliente: marca los documentos a corregir y el sistema le notifica para que los reenvíe. Haz esto desde el trámite abierto en tu bandeja.");
                resp.setAccion(accionNavegar("Ir a mi bandeja", "/funcionario/bandeja"));
                return resp;

            case "func_voz":
                resp.setRespuesta("Para llenar la sección por voz: 1) dentro del expediente toca el ícono de micrófono, 2) dicta los datos con naturalidad (por ejemplo \"nombre Juan Pérez, monto 500\"), 3) la IA transcribe y rellena los campos correspondientes de tu sección activa, 4) revisa y corrige lo que haga falta antes de guardar.");
                return resp;

            case "admin_politicas":
                resp.setRespuesta("Para crear y activar una política de trámite: 1) ve a \"Políticas\" y pulsa \"Nueva política\", 2) define nombre, descripción, categoría y requisitos, 3) guárdala (queda en borrador), 4) cuando ya tenga su diagrama de flujo listo, cámbiala a estado \"Activa\" para que los clientes puedan iniciarla.");
                resp.setAccion(accionNavegar("Ir a Políticas", "/admin/politicas"));
                return resp;

            case "admin_diagramas":
                resp.setRespuesta("Para diseñar un flujo: 1) ve a Diagramas y abre o crea el de la política, 2) agrega nodos (actividad, decisión sí/no, fork para separar en paralelo, join para esperar a todas las ramas), 3) conéctalos y asigna cada actividad a un departamento. También puedes generarlo con IA describiendo el proceso en lenguaje natural con \"Diseño con IA\" por prompt.");
                resp.setAccion(accionNavegar("Ir a Diagramas", "/admin/diagramas"));
                return resp;

            case "admin_colaborar":
                resp.setRespuesta("Para co-editar un diagrama: 1) ábrelo y pulsa \"Compartir\", 2) escribe el correo del admin o funcionario que invitas, 3) elige el permiso: Editor (puede modificar) o Solo lectura (solo ve), 4) envía la invitación. La persona invitada lo encontrará en la sección \"Compartidos conmigo\" de Diagramas.");
                resp.setAccion(accionNavegar("Ir a Diagramas", "/admin/diagramas"));
                return resp;

            case "admin_usuarios":
                resp.setRespuesta("Para gestionar usuarios: 1) ve a \"Usuarios\", 2) pulsa \"Nuevo usuario\" y elige el rol (Administrador o Funcionario), 3) completa nombre, correo y departamento, 4) guarda. Desde la misma lista puedes editar o desactivar usuarios y administrar los departamentos a los que se asignan.");
                resp.setAccion(accionNavegar("Ir a Usuarios", "/admin/usuarios"));
                return resp;

            case "admin_analitica":
                resp.setRespuesta("En Métricas verás la analítica del sistema: 1) trámites activos por estado y tiempos por actividad, 2) los cuellos de botella detectados automáticamente cuando se supera el SLA, 3) anomalías que marca la IA, y 4) reportes que puedes pedir en lenguaje natural describiendo lo que quieres saber.");
                resp.setAccion(accionNavegar("Ir a Métricas", "/admin/metricas"));
                return resp;

            case "fuera_de_alcance":
            default:
                resp.setRespuesta("Soy el asistente de trámites; con eso no te puedo ayudar 😅. Pero sí puedo recomendarte un trámite, ver su estado o decirte qué documentos necesitas. ¿Te ayudo con alguno?");
                return resp;
        }
    }

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

        if (consulta.contains("recomien") || consulta.contains("sugier") ||
            consulta.contains("que tramite") || consulta.contains("qué trámite") ||
            consulta.contains("cual tramite") || consulta.contains("cuál trámite") ||
            consulta.contains("necesito") || consulta.contains("quiero") ||
            consulta.contains("requiero") || consulta.contains("deseo") ||
            consulta.contains("solicitar") || consulta.contains("tramitar")) {
            return recomendarTramite(consulta);
        }

        if ((consulta.contains("inici") || consulta.contains("empez") || consulta.contains("empiez") ||
             consulta.contains("nuevo tramite") || consulta.contains("nuevo trámite")) &&
            consulta.contains("tramit")) {
            resp.setRespuesta("Para iniciar un trámite ve a \"Explorar\", elige el que necesitas y toca \"Iniciar\". O cuéntame qué necesitas y te recomiendo cuál.");
            resp.setAccion(accionNavegar("Explorar trámites", "/cliente/tramites"));
            return resp;
        }

        if (consulta.contains("mis tramite") || consulta.contains("mis trámite") ||
            consulta.contains("estado de mi") || consulta.contains("seguim")) {
            resp.setRespuesta("Puedes ver el estado de todos tus trámites en \"Mis trámites\". Toca uno para ver su línea de tiempo.");
            resp.setAccion(accionNavegar("Ver mis trámites", "/cliente/tramites"));
            return resp;
        }

        if (consulta.contains("document") || consulta.contains("requisit") ||
            consulta.contains("adjunt") || consulta.contains("subir") || consulta.contains("falta")) {
            resp.setRespuesta("Si tu trámite pide documentos, los verás en \"Completar documentos\" o al abrir el trámite. Sube cada requisito (foto o archivo); cuando estén todos, el trámite avanza solo.");
            resp.setAccion(accionNavegar("Completar documentos", "/cliente/tramites"));
            return resp;
        }

        if (consulta.contains("politica") || consulta.contains("política") || consulta.contains("flujo") || consulta.contains("workflow")) {
            List<PoliticaNegocio> activas = politicaRepo.findByEstado("activa");
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

        return recomendarTramite(consulta);
    }

    private AgenteResponse.AccionDirecta accionNavegar(String label, String ruta) {
        AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
        a.setLabel(label);
        a.setRuta(ruta);
        a.setTipo("navegar");
        return a;
    }

    private AgenteResponse recomendarTramite(String consulta) {
        String c = quitarAcentos(consulta);
        List<PoliticaNegocio> activas = politicaRepo.findByEstado("activa");
        if (activas.isEmpty()) activas = politicaRepo.findAll();

        PoliticaNegocio mejor = null;
        int mejorScore = 0;
        for (PoliticaNegocio p : activas) {
            int s = puntajeCoincidencia(c, p);
            if (s > mejorScore) {
                mejorScore = s;
                mejor = p;
            }
        }

        AgenteResponse resp = new AgenteResponse();
        resp.setFuente("kb-local");

        if (mejor != null && mejorScore > 0) {
            String desc = (mejor.getDescripcion() != null && !mejor.getDescripcion().isBlank())
                    ? " " + mejor.getDescripcion() : "";
            resp.setRespuesta("Por lo que me cuentas, el trámite que necesitas es: \""
                    + mejor.getNombre() + "\"." + desc + " ¿Quieres iniciarlo ahora?");
            AgenteResponse.AccionDirecta a = new AgenteResponse.AccionDirecta();
            a.setLabel("Iniciar: " + mejor.getNombre());
            a.setRuta("/tramite-nuevo");
            a.setTipo("iniciar");
            a.setDato(mejor.getId());
            resp.setAccion(a);
            return resp;
        }

        StringBuilder sb = new StringBuilder("No identifiqué un trámite exacto para eso. ");
        if (!activas.isEmpty()) {
            sb.append("Estos son los disponibles: ");
            int max = Math.min(5, activas.size());
            for (int i = 0; i < max; i++) {
                sb.append(activas.get(i).getNombre());
                if (i < max - 1) sb.append(", ");
            }
            sb.append(". ");
        }
        sb.append("Puedes verlos en \"Explorar\" e iniciar el que necesites.");
        resp.setRespuesta(sb.toString());
        resp.setAccion(accionNavegar("Ver trámites disponibles", "/cliente/tramites"));
        return resp;
    }

    private AgenteResponse listarTramitesDisponibles() {
        List<PoliticaNegocio> activas = politicaRepo.findByEstado("activa");
        if (activas.isEmpty()) activas = politicaRepo.findAll();

        AgenteResponse resp = new AgenteResponse();
        resp.setFuente("ml-tf");

        if (activas.isEmpty()) {
            resp.setRespuesta("Por ahora no hay trámites disponibles para iniciar. Vuelve a intentarlo más tarde.");
            resp.setAccion(accionNavegar("Explorar trámites", "/cliente/tramites"));
            return resp;
        }

        StringBuilder sb = new StringBuilder("Estos son los trámites que puedes iniciar (");
        sb.append(activas.size()).append("): ");
        int max = Math.min(8, activas.size());
        for (int i = 0; i < max; i++) {
            sb.append("• ").append(activas.get(i).getNombre());
            if (i < max - 1) sb.append("  ");
        }
        if (activas.size() > max) sb.append("  …");
        sb.append(". Abre \"Explorar\" para ver el detalle e iniciar el que necesites, o descríbeme tu caso y te recomiendo cuál.");
        resp.setRespuesta(sb.toString());
        resp.setAccion(accionNavegar("Explorar trámites", "/cliente/tramites"));
        return resp;
    }

    private static final java.util.Set<String> STOP = java.util.Set.of(
            "para", "como", "esta", "este", "esto", "donde", "cuando", "cual",
            "pero", "todo", "todos", "cliente", "clientes", "solicitud",
            "tramite", "tramites", "nueva", "nuevo", "nuevos");

    private int puntajeCoincidencia(String consultaNorm, PoliticaNegocio p) {
        String texto = quitarAcentos(((p.getNombre() == null ? "" : p.getNombre()) + " "
                + (p.getDescripcion() == null ? "" : p.getDescripcion()) + " "
                + (p.getCategoria() == null ? "" : p.getCategoria())).toLowerCase(Locale.ROOT));
        int score = 0;
        for (String w : texto.split("[^a-z0-9]+")) {
            if (w.length() <= 3 || STOP.contains(w)) continue;
            if (consultaNorm.contains(w)) score++;
        }
        return score;
    }

    private String quitarAcentos(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
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
