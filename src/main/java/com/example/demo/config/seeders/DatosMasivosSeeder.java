package com.example.demo.config.seeders;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Slf4j
public class DatosMasivosSeeder {

    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PoliticaNegocioRepository politicaRepository;
    @Autowired private DiagramaWorkflowRepository diagramaRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private FlujoTransicionRepository transicionRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private AlertaAnomaliaRepository alertaRepo;
    @Autowired private TramiteRepository tramiteRepo;

    private final Random rnd = new Random(20260611L);

    private static final int TOTAL_DEPARTAMENTOS = 50;
    private static final int TOTAL_USUARIOS = 100;

    public void seed() {
        if (departamentoRepository.findByCodigo("RED-C").isPresent()) {
            log.info("[Seeder] DatosMasivos ya existen, se omite");
            return;
        }

        String adminId = usuarioRepository.findByEmail("admin@cre.bo")
                .map(Usuario::getId).orElse("system");

        List<String> codigosNuevos = seedDepartamentos();
        seedUsuarios();
        seedPoliticasConDiagrama(adminId, codigosNuevos);
        seedAnomaliasAleatorias();

        log.info("[Seeder] DatosMasivos OK (departamentos={}, usuarios={})",
                departamentoRepository.count(), usuarioRepository.count());
    }

    // ── Departamentos (temática redes/telecom/jurídico) ──────────────
    private List<String> seedDepartamentos() {
        String[][] areas = {
                {"RED", "Redes y Conectividad"},
                {"FIB", "Fibra Óptica"},
                {"WIF", "Redes WiFi"},
                {"NOC", "Centro de Operaciones de Red"},
                {"INF", "Infraestructura"},
                {"INS", "Instalaciones"},
                {"MNT", "Mantenimiento"},
                {"SOP", "Soporte Técnico"},
                {"COM", "Comercial"},
                {"VEN", "Ventas"},
                {"FAC", "Facturación"},
                {"COB", "Cobranzas"},
                {"JUR", "Jurídico"},
                {"CTR", "Contratos"},
                {"CAL", "Calidad"},
                {"SEG", "Seguridad"},
                {"AUD", "Auditoría"},
                {"LOG", "Logística"},
                {"POS", "Postventa"},
                {"PYI", "Proyectos e Ingeniería"},
        };
        String[][] zonas = {{"C", "Central"}, {"N", "Norte"}, {"S", "Sur"}};

        List<String> creados = new ArrayList<>();
        for (String[] z : zonas) {
            for (String[] a : areas) {
                if (departamentoRepository.count() >= TOTAL_DEPARTAMENTOS) {
                    return creados;
                }
                String codigo = a[0] + "-" + z[0];
                String nombre = a[1] + " " + z[1];
                if (departamentoRepository.findByCodigo(codigo).isEmpty()) {
                    Departamento d = new Departamento();
                    d.setCodigo(codigo);
                    d.setNombre(nombre);
                    d.setDescripcion("Departamento de " + a[1].toLowerCase() + " — zona " + z[1].toLowerCase() + ".");
                    d.setActivo(true);
                    d.setFechaCreacion(LocalDateTime.now());
                    departamentoRepository.save(d);
                    creados.add(codigo);
                }
            }
        }
        return creados;
    }

    // ── Usuarios funcionarios (nombres bolivianos aleatorios) ────────
    private void seedUsuarios() {
        String rolFuncio = rolRepository.findByNombre("Funcionario").map(Rol::getId).orElse(null);
        List<String> deptos = new ArrayList<>();
        departamentoRepository.findAll().forEach(d -> deptos.add(d.getId()));
        if (deptos.isEmpty()) return;

        String[] nombres = {"Juan", "Maria", "Pedro", "Ana", "Luis", "Rosa", "Carlos", "Elena",
                "Jorge", "Sofia", "Miguel", "Lucia", "Diego", "Carmen", "Raul", "Patricia",
                "Fernando", "Gabriela", "Hugo", "Daniela", "Marco", "Valeria", "Ivan", "Andrea",
                "Ruben", "Paola", "Oscar", "Veronica", "Sergio", "Natalia"};
        String[] apellidos = {"Perez", "Gomez", "Mamani", "Flores", "Quispe", "Choque", "Vargas",
                "Rojas", "Cruz", "Torres", "Lima", "Condori", "Ramirez", "Fernandez", "Gutierrez",
                "Apaza", "Calle", "Mendoza", "Cabrera", "Vega", "Salazar", "Ticona", "Aguilar",
                "Ledezma", "Camacho", "Nina", "Sandoval", "Loayza", "Padilla", "Yujra"};

        int objetivo = Math.max(0, TOTAL_USUARIOS - (int) usuarioRepository.count());
        int creados = 0, n = 1;
        while (creados < objetivo && n < 1000) {
            String email = "func" + n + "@motor.bo";
            n++;
            if (usuarioRepository.existsByEmail(email)) continue;

            Usuario u = new Usuario();
            u.setNombre(nombres[rnd.nextInt(nombres.length)]);
            u.setApellido(apellidos[rnd.nextInt(apellidos.length)]);
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode("func12345"));
            u.setTipo("funcionario");
            u.setRolId(rolFuncio);
            u.setDepartamentosIds(List.of(deptos.get(rnd.nextInt(deptos.size()))));
            u.setActivo(true);
            u.setFechaRegistro(LocalDateTime.now());
            usuarioRepository.save(u);
            creados++;
        }
    }

    // ── Políticas de negocio + 1 diagrama por política ───────────────
    private void seedPoliticasConDiagrama(String adminId, List<String> codigosNuevos) {
        List<Actividad> acts = actividadRepository.findAll();

        Object[][] politicas = {
                {"Instalación de internet por fibra óptica (FTTH)", "comercial",
                        "Solicitud, inspección de factibilidad e instalación de internet por fibra al hogar.",
                        new String[]{"ATC", "RED-C", "FIB-C", "INS-C"}},
                {"Contratación de plan de internet hogar", "comercial",
                        "Alta de un nuevo plan de internet residencial con validación de cobertura.",
                        new String[]{"ATC", "COM-C", "CTR-C", "INS-C"}},
                {"Contratación de plan de internet empresarial", "comercial",
                        "Alta de servicio dedicado para empresas con SLA y enlace redundante.",
                        new String[]{"VEN-C", "PYI-C", "JUR-C", "NOC-C"}},
                {"Instalación de red WiFi en condominios", "instalaciones",
                        "Despliegue de puntos de acceso WiFi en edificios y condominios.",
                        new String[]{"COM-C", "WIF-C", "INS-C", "CAL-C"}},
                {"Soporte y reparación de avería", "soporte",
                        "Registro y atención de una avería del servicio: diagnóstico, visita y cierre.",
                        new String[]{"SOP-C", "RED-C", "MNT-C"}},
                {"Ampliación de ancho de banda", "comercial",
                        "Upgrade del plan a mayor velocidad con revisión de la red del cliente.",
                        new String[]{"ATC", "COM-C", "NOC-C"}},
                {"Portabilidad de servicio de internet", "administrativo",
                        "Portabilidad del servicio desde otro operador, con baja coordinada.",
                        new String[]{"ATC", "JUR-C", "CTR-C", "INS-C"}},
                {"Baja del servicio y retiro de equipos", "administrativo",
                        "Cancelación del servicio, retiro de equipos y liquidación final.",
                        new String[]{"ATC", "COB-C", "LOG-C", "MNT-C"}},
                {"Reclamo de facturación", "facturacion",
                        "Atención de un reclamo de facturación: revisión, ajuste y notificación.",
                        new String[]{"ATC", "FAC-C", "AUD-C"}},
                {"Reconexión por mora", "facturacion",
                        "Reconexión del servicio tras la regularización de la deuda.",
                        new String[]{"COB-C", "FAC-C", "NOC-C"}},
                {"Contrato de servicio corporativo", "juridico",
                        "Elaboración y firma de contrato de servicios para clientes corporativos.",
                        new String[]{"VEN-C", "JUR-C", "CTR-C"}},
                {"Convenio de pago", "juridico",
                        "Negociación y formalización de un convenio de pago de deuda.",
                        new String[]{"COB-C", "JUR-C", "CTR-C"}},
                {"Migración a fibra óptica", "instalaciones",
                        "Migración de un cliente de cobre/inalámbrico a fibra óptica.",
                        new String[]{"ATC", "FIB-C", "RED-C", "INS-C"}},
                {"Instalación de antena WiFi rural", "instalaciones",
                        "Provisión de internet inalámbrico en zonas rurales sin fibra.",
                        new String[]{"PYI-C", "WIF-C", "INS-C", "SOP-C"}},
        };

        String[] nombresNodo = {"Recepción de solicitud", "Evaluación técnica", "Aprobación",
                "Ejecución", "Verificación y cierre", "Validación", "Coordinación"};

        int idxAct = 0;
        for (Object[] pol : politicas) {
            String nombre = (String) pol[0];
            String categoria = (String) pol[1];
            String descripcion = (String) pol[2];
            String[] deptCodes = (String[]) pol[3];

            boolean existe = politicaRepository.findAll().stream()
                    .anyMatch(p -> nombre.equals(p.getNombre()));
            if (existe) continue;

            PoliticaNegocio p = new PoliticaNegocio();
            p.setNombre(nombre);
            p.setDescripcion(descripcion);
            p.setCategoria(categoria);
            p.setCreadorId(adminId);
            p.setVersionActual(1);
            p.setEstado("activa");
            p.setFechaCreacion(LocalDateTime.now());
            p.setFechaActivacion(LocalDateTime.now());
            p = politicaRepository.save(p);

            List<String> swimlanes = new ArrayList<>();
            for (String c : deptCodes) {
                if (departamentoRepository.findByCodigo(c).isPresent()) swimlanes.add(c);
            }
            if (swimlanes.isEmpty()) swimlanes.add("ATC");

            DiagramaWorkflow diag = new DiagramaWorkflow();
            diag.setNombre("Flujo - " + nombre);
            diag.setPoliticaId(p.getId());
            diag.setCreadorId(adminId);
            diag.setSwimlanes(swimlanes);
            diag.setVersionActual(1);
            diag.setEstado("publicado");
            diag.setGeneradoPorIa(false);
            diag.setFechaCreacion(LocalDateTime.now());
            diag.setUltimaModificacion(LocalDateTime.now());
            diag = diagramaRepository.save(diag);
            String diagId = diag.getId();

            p.setDiagramaId(diagId);
            politicaRepository.save(p);

            int orden = 1;
            NodoDiagrama prev = nodo(diagId, "inicio", "Inicio", null, null, null, orden++);
            for (int i = 0; i < swimlanes.size(); i++) {
                String code = swimlanes.get(i);
                String deptId = departamentoRepository.findByCodigo(code).map(Departamento::getId).orElse(null);
                String actId = acts.isEmpty() ? null : acts.get(idxAct++ % acts.size()).getId();
                String nombreNodo = nombresNodo[i % nombresNodo.length];
                NodoDiagrama actNodo = nodo(diagId, "actividad", nombreNodo, actId, deptId, code, orden++);
                trans(diagId, prev.getId(), actNodo.getId(), "secuencial", null, null);
                prev = actNodo;
            }
            NodoDiagrama fin = nodo(diagId, "fin", "Fin", null, null, null, orden++);
            trans(diagId, prev.getId(), fin.getId(), "secuencial", null, null);
        }
    }

    // ── Anomalías aleatorias adicionales ─────────────────────────────
    private void seedAnomaliasAleatorias() {
        List<Tramite> tramites = tramiteRepo.findAll();
        if (tramites.isEmpty()) return;

        String[] categorias = {"tiempo_atipico", "secuencia_inusual", "loop_derivaciones", "salto_no_autorizado"};
        String[] descripciones = {
                "Tiempo de etapa muy por encima del promedio histórico.",
                "Orden de actividades atípico respecto al flujo estándar.",
                "Derivaciones repetidas entre departamentos por encima del umbral.",
                "Avance de etapa sin completar un paso obligatorio previo.",
                "SLA superado en la etapa actual del trámite.",
                "Patrón de actividad inusual detectado por el modelo.",
        };

        int extra = 10;
        for (int i = 0; i < extra; i++) {
            Tramite t = tramites.get(rnd.nextInt(tramites.size()));
            AlertaAnomalia a = new AlertaAnomalia();
            a.setTramiteId(t.getId());
            a.setCategoria(categorias[rnd.nextInt(categorias.length)]);
            a.setScore(0.5f + rnd.nextFloat() * 0.49f);
            a.setDescripcion(descripciones[rnd.nextInt(descripciones.length)]);
            a.setFalsoPositivo(false);
            a.setFechaDeteccion(LocalDateTime.now().minusHours(rnd.nextInt(72)));
            alertaRepo.save(a);
        }
    }

    private NodoDiagrama nodo(String diagId, String tipo, String nombre, String actividadId,
                              String departamentoId, String swimlane, int orden) {
        NodoDiagrama n = new NodoDiagrama();
        n.setDiagramaId(diagId);
        n.setTipo(tipo);
        n.setNombre(nombre);
        n.setActividadId(actividadId);
        n.setDepartamentoId(departamentoId);
        n.setSwimlane(swimlane);
        n.setOrden(orden);
        return nodoRepository.save(n);
    }

    private void trans(String diagId, String origenId, String destinoId,
                       String tipo, String condicion, String etiqueta) {
        FlujoTransicion t = new FlujoTransicion();
        t.setDiagramaId(diagId);
        t.setNodoOrigenId(origenId);
        t.setNodoDestinoId(destinoId);
        t.setTipo(tipo);
        t.setCondicion(condicion);
        t.setEtiqueta(etiqueta);
        transicionRepository.save(t);
    }
}
