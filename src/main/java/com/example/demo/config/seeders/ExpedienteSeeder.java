package com.example.demo.config.seeders;

import com.example.demo.models.*;
import com.example.demo.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ExpedienteSeeder {

    @Autowired private ExpedienteDigitalRepository expedienteRepository;
    @Autowired private SeccionExpedienteRepository seccionRepository;
    @Autowired private CampoSeccionRepository campoSeccionRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {

        if (expedienteRepository.count() > 0) {
            fixNullNodoIds();
        }

        List<NodoDiagrama> nodos = nodoRepository.findAll();
        String nAtcVer   = nodoId(nodos, "actividad", "Verificar Documentos");
        String nTecInsp  = nodoId(nodos, "actividad", "Inspeccion en Campo");
        String nTecPres  = nodoId(nodos, "actividad", "Elaborar Presupuesto");
        String nLegContr = nodoId(nodos, "actividad", "Revisar Contrato");
        String nOpeCierre= nodoId(nodos, "actividad", "Cierre y Conexion");

        String atcId = deptoId("ATC");
        String tecId = deptoId("TEC");
        String legId = deptoId("LEG");
        String opeId = deptoId("OPE");

        String funcAtcId = userId("func_atc@cre.bo");
        String funcTecId = userId("funcionario@cre.bo");
        String funcLegId = userId("func_leg@cre.bo");
        String funcOpeId = userId("func_ope@cre.bo");

        tramiteRepository.findAll().forEach(tramite -> {

            if (expedienteRepository.findByTramiteId(tramite.getId()).isPresent()) {
                return;
            }
            switch (tramite.getCodigo()) {
                case "TRM-2024-001", "TRM-2024-014" -> crearExpedienteCompleto(tramite,
                        nAtcVer, nTecInsp, nTecPres, nLegContr, nOpeCierre,
                        atcId, tecId, legId, opeId,
                        funcAtcId, funcTecId, funcLegId, funcOpeId);
                case "TRM-2024-002" -> crearExpedienteRechazado(tramite,
                        nAtcVer, nTecInsp, nTecPres, nLegContr,
                        atcId, tecId, legId,
                        funcAtcId, funcTecId, funcLegId);
                case "TRM-2024-003", "TRM-2024-009" -> crearExpedienteParcial(tramite,
                        nAtcVer, nTecInsp, nTecPres,
                        atcId, tecId, funcAtcId, funcTecId);
                case "TRM-2024-004" -> crearExpedienteInicial(tramite,
                        nAtcVer, atcId, funcAtcId);

                case "TRM-2024-008" -> crearExpedienteCompuerta(tramite,
                        nAtcVer, atcId, funcAtcId);
                case "TRM-2024-005", "TRM-2024-011" -> crearExpedienteObservado(tramite,
                        nAtcVer, nTecInsp, nTecPres, nLegContr,
                        atcId, tecId, legId, funcAtcId, funcTecId, funcLegId);
                case "TRM-2024-007", "TRM-2024-010" -> crearExpedienteListoParaAprobar(tramite,
                        nAtcVer, nTecInsp, nTecPres, nLegContr, nOpeCierre,
                        atcId, tecId, legId, opeId,
                        funcAtcId, funcTecId, funcLegId, funcOpeId);
                case "TRM-2024-012" -> crearExpedienteCierreEnCurso(tramite,
                        nAtcVer, nTecInsp, nTecPres, nLegContr, nOpeCierre,
                        atcId, tecId, legId, opeId,
                        funcAtcId, funcTecId, funcLegId, funcOpeId);
                case "TRM-2024-006", "TRM-2024-013" -> crearExpedienteCancelado(tramite,
                        nAtcVer, atcId, funcAtcId);
            }
        });

        log.info("[Seeder] Expedientes OK");
    }

    private void crearExpedienteCompleto(Tramite t,
            String nAtcVer, String nTecInsp, String nTecPres, String nLegContr, String nOpeCierre,
            String atcId, String tecId, String legId, String opeId,
            String funcAtcId, String funcTecId, String funcLegId, String funcOpeId) {

        LocalDateTime base = t.getFechaInicio();
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Derivada",
                funcAtcId, base, base.plusHours(6));
        poblarCamposVerificacion(sAtc.getId());

        SeccionExpediente sTecInsp = crearSeccion(exp.getId(), nTecInsp, tecId, 2, "Derivada",
                funcTecId, base.plusDays(1), base.plusDays(5));
        poblarCamposInspeccion(sTecInsp.getId());

        SeccionExpediente sTecPres = crearSeccion(exp.getId(), nTecPres, tecId, 3, "Derivada",
                funcTecId, base.plusDays(1), base.plusDays(8));
        poblarCamposPresupuesto(sTecPres.getId());

        SeccionExpediente sLeg = crearSeccion(exp.getId(), nLegContr, legId, 4, "Derivada",
                funcLegId, base.plusDays(9), base.plusDays(18));
        poblarCamposContrato(sLeg.getId());

        SeccionExpediente sOpe = crearSeccion(exp.getId(), nOpeCierre, opeId, 5, "Derivada",
                funcOpeId, base.plusDays(20), base.plusDays(25));
        poblarCamposCierre(sOpe.getId());

        exp.setSeccionesIds(List.of(sAtc.getId(), sTecInsp.getId(), sTecPres.getId(), sLeg.getId(), sOpe.getId()));
        exp.setUltimaActualizacion(base.plusDays(25));
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteParcial(Tramite t, String nAtcVer, String nTecInsp, String nTecPres,
                                         String atcId, String tecId, String funcAtcId, String funcTecId) {
        LocalDateTime base = t.getFechaInicio();
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Derivada",
                funcAtcId, base, base.plusHours(4));
        poblarCamposVerificacion(sAtc.getId());

        SeccionExpediente sTecInsp = crearSeccion(exp.getId(), nTecInsp, tecId, 2, "En ejecucion",
                funcTecId, base.plusDays(2), null);

        SeccionExpediente sTecPres = crearSeccion(exp.getId(), nTecPres, tecId, 3, "En ejecucion",
                funcTecId, base.plusDays(2), null);

        exp.setSeccionesIds(List.of(sAtc.getId(), sTecInsp.getId(), sTecPres.getId()));
        exp.setUltimaActualizacion(LocalDateTime.now());
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteInicial(Tramite t, String nAtcVer, String atcId, String funcAtcId) {
        LocalDateTime base = t.getFechaInicio();
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Pendiente de recepcion",
                funcAtcId, base, null);

        exp.setSeccionesIds(List.of(sAtc.getId()));
        exp.setUltimaActualizacion(base);
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteCompuerta(Tramite t, String nAtcVer, String atcId, String funcAtcId) {
        LocalDateTime base = t.getFechaInicio();
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Pendiente de documentos",
                funcAtcId, base, null);

        exp.setSeccionesIds(List.of(sAtc.getId()));
        exp.setUltimaActualizacion(base);
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteObservado(Tramite t, String nAtcVer, String nTecInsp, String nTecPres,
                                           String nLegContr, String atcId, String tecId, String legId,
                                           String funcAtcId, String funcTecId, String funcLegId) {
        LocalDateTime base = t.getFechaInicio();
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Derivada",
                funcAtcId, base, base.plusHours(5));
        poblarCamposVerificacion(sAtc.getId());

        SeccionExpediente sTecInsp = crearSeccion(exp.getId(), nTecInsp, tecId, 2, "Derivada",
                funcTecId, base.plusDays(2), base.plusDays(6));
        poblarCamposInspeccion(sTecInsp.getId());

        SeccionExpediente sTecPres = crearSeccion(exp.getId(), nTecPres, tecId, 3, "Derivada",
                funcTecId, base.plusDays(2), base.plusDays(7));
        poblarCamposPresupuesto(sTecPres.getId());

        SeccionExpediente sLeg = crearSeccion(exp.getId(), nLegContr, legId, 4, "Observado",
                funcLegId, base.plusDays(8), null);

        exp.setSeccionesIds(List.of(sAtc.getId(), sTecInsp.getId(), sTecPres.getId(), sLeg.getId()));
        exp.setUltimaActualizacion(LocalDateTime.now());
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteListoParaAprobar(Tramite t,
            String nAtcVer, String nTecInsp, String nTecPres, String nLegContr, String nOpeCierre,
            String atcId, String tecId, String legId, String opeId,
            String funcAtcId, String funcTecId, String funcLegId, String funcOpeId) {
        LocalDateTime base = t.getFechaInicio();
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Derivada",
                funcAtcId, base, base.plusHours(4));
        poblarCamposVerificacion(sAtc.getId());

        SeccionExpediente sTecInsp = crearSeccion(exp.getId(), nTecInsp, tecId, 2, "Derivada",
                funcTecId, base.plusDays(1), base.plusDays(4));
        poblarCamposInspeccion(sTecInsp.getId());

        SeccionExpediente sTecPres = crearSeccion(exp.getId(), nTecPres, tecId, 3, "Derivada",
                funcTecId, base.plusDays(1), base.plusDays(5));
        poblarCamposPresupuesto(sTecPres.getId());

        SeccionExpediente sLeg = crearSeccion(exp.getId(), nLegContr, legId, 4, "En ejecucion",
                funcLegId, base.plusDays(6), null);

        SeccionExpediente sOpe = crearSeccion(exp.getId(), nOpeCierre, opeId, 5, "Bloqueada",
                funcOpeId, null, null);

        exp.setSeccionesIds(List.of(sAtc.getId(), sTecInsp.getId(), sTecPres.getId(), sLeg.getId(), sOpe.getId()));
        exp.setUltimaActualizacion(LocalDateTime.now());
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteRechazado(Tramite t,
            String nAtcVer, String nTecInsp, String nTecPres, String nLegContr,
            String atcId, String tecId, String legId,
            String funcAtcId, String funcTecId, String funcLegId) {
        LocalDateTime base = t.getFechaInicio();
        LocalDateTime cierre = t.getFechaCierreReal() != null
                ? t.getFechaCierreReal() : base.plusDays(15);
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Derivada",
                funcAtcId, base, base.plusHours(5));
        poblarCamposVerificacion(sAtc.getId());

        SeccionExpediente sTecInsp = crearSeccion(exp.getId(), nTecInsp, tecId, 2, "Derivada",
                funcTecId, base.plusDays(1), base.plusDays(5));
        poblarCamposInspeccion(sTecInsp.getId());

        SeccionExpediente sTecPres = crearSeccion(exp.getId(), nTecPres, tecId, 3, "Derivada",
                funcTecId, base.plusDays(1), base.plusDays(6));
        poblarCamposPresupuesto(sTecPres.getId());

        SeccionExpediente sLeg = crearSeccion(exp.getId(), nLegContr, legId, 4, "Derivada",
                funcLegId, base.plusDays(7), cierre);
        poblarCamposContratoRechazado(sLeg.getId());

        exp.setSeccionesIds(List.of(sAtc.getId(), sTecInsp.getId(), sTecPres.getId(), sLeg.getId()));
        exp.setUltimaActualizacion(cierre);
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteCierreEnCurso(Tramite t,
            String nAtcVer, String nTecInsp, String nTecPres, String nLegContr, String nOpeCierre,
            String atcId, String tecId, String legId, String opeId,
            String funcAtcId, String funcTecId, String funcLegId, String funcOpeId) {
        LocalDateTime base = t.getFechaInicio();
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Derivada",
                funcAtcId, base, base.plusHours(5));
        poblarCamposVerificacion(sAtc.getId());

        SeccionExpediente sTecInsp = crearSeccion(exp.getId(), nTecInsp, tecId, 2, "Derivada",
                funcTecId, base.plusDays(2), base.plusDays(6));
        poblarCamposInspeccion(sTecInsp.getId());

        SeccionExpediente sTecPres = crearSeccion(exp.getId(), nTecPres, tecId, 3, "Derivada",
                funcTecId, base.plusDays(2), base.plusDays(7));
        poblarCamposPresupuesto(sTecPres.getId());

        SeccionExpediente sLeg = crearSeccion(exp.getId(), nLegContr, legId, 4, "Derivada",
                funcLegId, base.plusDays(8), base.plusDays(15));
        poblarCamposContrato(sLeg.getId());

        SeccionExpediente sOpe = crearSeccion(exp.getId(), nOpeCierre, opeId, 5, "En ejecucion",
                funcOpeId, base.plusDays(16), null);

        exp.setSeccionesIds(List.of(sAtc.getId(), sTecInsp.getId(), sTecPres.getId(), sLeg.getId(), sOpe.getId()));
        exp.setUltimaActualizacion(LocalDateTime.now());
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private void crearExpedienteCancelado(Tramite t, String nAtcVer, String atcId, String funcAtcId) {
        LocalDateTime base = t.getFechaInicio();
        LocalDateTime cierre = t.getFechaCierreReal() != null
                ? t.getFechaCierreReal() : base.plusDays(2);
        ExpedienteDigital exp = crearExpediente(t.getId(), base);

        SeccionExpediente sAtc = crearSeccion(exp.getId(), nAtcVer, atcId, 1, "Derivada",
                funcAtcId, base, cierre);
        poblarCamposVerificacion(sAtc.getId());

        exp.setSeccionesIds(List.of(sAtc.getId()));
        exp.setUltimaActualizacion(cierre);
        expedienteRepository.save(exp);

        t.setExpedienteId(exp.getId());
        tramiteRepository.save(t);
    }

    private ExpedienteDigital crearExpediente(String tramiteId, LocalDateTime fechaCreacion) {
        ExpedienteDigital exp = new ExpedienteDigital();
        exp.setTramiteId(tramiteId);
        exp.setSeccionesIds(new ArrayList<>());
        exp.setFechaCreacion(fechaCreacion);
        exp.setUltimaActualizacion(fechaCreacion);
        return expedienteRepository.save(exp);
    }

    private SeccionExpediente crearSeccion(String expedienteId, String nodoId, String departamentoId,
                                            int orden, String estado, String funcionarioId,
                                            LocalDateTime asignacion, LocalDateTime completado) {
        SeccionExpediente s = new SeccionExpediente();
        s.setExpedienteId(expedienteId);
        s.setNodoId(nodoId);
        s.setDepartamentoId(departamentoId);
        s.setOrdenSeccion(orden);
        s.setEstado(estado);
        s.setFuncionarioId(funcionarioId);
        s.setFechaAsignacion(asignacion);
        s.setFechaCompletado(completado);
        return seccionRepository.save(s);
    }

    private void campo(String seccionId, String campoPlantillaId, String nombre, String valor, String tipo) {
        CampoSeccion c = new CampoSeccion();
        c.setSeccionId(seccionId);
        c.setCampoPlantillaId(campoPlantillaId);
        c.setNombre(nombre);
        c.setValor(valor);
        c.setTipo(tipo);
        c.setFueDictado(false);
        c.setFechaGuardado(LocalDateTime.now());
        campoSeccionRepository.save(c);
    }

    private void poblarCamposVerificacion(String seccionId) {
        campo(seccionId, null, "nombre_solicitante", "Juan Perez Mamani", "texto");
        campo(seccionId, null, "numero_ci",          "7654321",           "texto");
        campo(seccionId, null, "domicilio",          "Av. America Nro. 245, Zona Central, Cochabamba", "textarea");
        campo(seccionId, null, "telefono",           "71234567",          "texto");
        campo(seccionId, null, "tipo_solicitud",     "Monofasica",        "select");
        campo(seccionId, null, "documentos_completos","true",             "checkbox");
    }

    private void poblarCamposInspeccion(String seccionId) {
        campo(seccionId, null, "fecha_inspeccion",       "2024-03-28", "fecha");
        campo(seccionId, null, "descripcion_sitio",      "Vivienda unifamiliar de 2 pisos, acceso directo a la calle", "textarea");
        campo(seccionId, null, "condiciones_electricas", "Optimas",    "select");
        campo(seccionId, null, "requiere_obra_civil",    "false",      "checkbox");
        campo(seccionId, null, "distancia_red_m",        "12",         "numero");
        campo(seccionId, null, "observaciones_tecnicas", "Sin observaciones adicionales", "textarea");
    }

    private void poblarCamposPresupuesto(String seccionId) {
        campo(seccionId, null, "descripcion_trabajos", "Instalacion de medidor monofasico 10A + cometida aerea 12m", "textarea");
        campo(seccionId, null, "materiales_bs",        "850.00",  "numero");
        campo(seccionId, null, "mano_obra_bs",         "350.00",  "numero");
        campo(seccionId, null, "total_bs",             "1200.00", "numero");
        campo(seccionId, null, "plazo_ejecucion_dias", "3",       "numero");
        campo(seccionId, null, "validez_dias",         "30",      "numero");
    }

    private void poblarCamposContrato(String seccionId) {
        campo(seccionId, null, "numero_contrato",    "CONT-2024-0421",  "texto");
        campo(seccionId, null, "fecha_revision",     "2024-04-10",      "fecha");
        campo(seccionId, null, "clausulas_ok",       "true",            "checkbox");
        campo(seccionId, null, "resultado_revision", "Aprobado",        "select");
        campo(seccionId, null, "observaciones_legales", "",             "textarea");
    }

    private void poblarCamposContratoRechazado(String seccionId) {
        campo(seccionId, null, "numero_contrato",    "CONT-2024-0388",  "texto");
        campo(seccionId, null, "fecha_revision",     "2024-04-08",      "fecha");
        campo(seccionId, null, "clausulas_ok",       "false",           "checkbox");
        campo(seccionId, null, "resultado_revision", "Rechazado",       "select");
        campo(seccionId, null, "observaciones_legales",
                "Documentacion de respaldo insuficiente. El titulo de propiedad presentado no cumple con los requisitos legales vigentes.",
                "textarea");
    }

    private void poblarCamposCierre(String seccionId) {
        campo(seccionId, null, "fecha_ejecucion",      "2024-04-20",    "fecha");
        campo(seccionId, null, "tecnico_nombre",       "Roberto Vargas", "texto");
        campo(seccionId, null, "numero_medidor",       "MED20240421",   "texto");
        campo(seccionId, null, "potencia_kw",          "5",             "numero");
        campo(seccionId, null, "prueba_funcionamiento","true",          "checkbox");
        campo(seccionId, null, "observaciones_finales","Conexion exitosa, cliente informado", "textarea");
    }

    private void fixNullNodoIds() {
        List<NodoDiagrama> nodos = nodoRepository.findAll();
        Map<Integer, String> ordenToNodoId = Map.of(
            1, orEmpty(nodoId(nodos, "actividad", "Verificar Documentos")),
            2, orEmpty(nodoId(nodos, "actividad", "Inspeccion en Campo")),
            3, orEmpty(nodoId(nodos, "actividad", "Elaborar Presupuesto")),
            4, orEmpty(nodoId(nodos, "actividad", "Revisar Contrato")),
            5, orEmpty(nodoId(nodos, "actividad", "Cierre y Conexion"))
        );
        seccionRepository.findAll().forEach(s -> {
            boolean changed = false;
            if (s.getNodoId() == null) {
                String nodoId = ordenToNodoId.get(s.getOrdenSeccion());
                if (nodoId != null && !nodoId.isEmpty()) {
                    s.setNodoId(nodoId);
                    changed = true;
                    log.info("[Seeder] Sección {} (orden {}) nodoId reparado → {}",
                            s.getId(), s.getOrdenSeccion(), nodoId);
                }
            }

            String estadoNorm = normalizarEstadoSeccion(s.getEstado());
            if (estadoNorm != null && !estadoNorm.equals(s.getEstado())) {
                s.setEstado(estadoNorm);
                changed = true;
            }
            if (changed) {
                seccionRepository.save(s);
            }
        });

        tramiteRepository.findAll().forEach(t -> {
            if (t.getNodoActualId() != null
                    && nodoRepository.findById(t.getNodoActualId()).isEmpty()) {

                expedienteRepository.findByTramiteId(t.getId()).ifPresent(exp -> {
                    seccionRepository.findByExpedienteIdOrderByOrdenSeccionAsc(exp.getId())
                        .stream()
                        .filter(s -> s.getNodoId() != null
                                && !EstadoSeccion.esDerivada(s.getEstado()))
                        .findFirst()
                        .ifPresent(s -> {
                            t.setNodoActualId(s.getNodoId());
                            tramiteRepository.save(t);
                            log.info("[Seeder] Tramite {} nodoActualId reparado → {}",
                                    t.getCodigo(), s.getNodoId());
                        });
                });
            }
        });
    }

    private String orEmpty(String s) { return s != null ? s : ""; }

    private String normalizarEstadoSeccion(String estado) {

        EstadoSeccion e = EstadoSeccion.from(estado);
        return e != null ? e.getValor() : estado;
    }

    private String nodoId(List<NodoDiagrama> nodos, String tipo, String nombre) {
        return nodos.stream()
                .filter(n -> tipo.equals(n.getTipo()) && (nombre == null || nombre.equals(n.getNombre())))
                .findFirst().map(NodoDiagrama::getId).orElse(null);
    }

    private String deptoId(String codigo) {
        return departamentoRepository.findByCodigo(codigo).map(d -> d.getId()).orElse(null);
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
