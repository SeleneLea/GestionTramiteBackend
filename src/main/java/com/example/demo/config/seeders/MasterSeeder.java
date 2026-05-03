package com.example.demo.config.seeders;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Orquestador de todos los seeders del sistema.
 *
 * Orden de ejecucion (respeta dependencias entre colecciones):
 *  1. Permisos
 *  2. Roles         (ref permisos)
 *  3. Departamentos
 *  4. Documentos
 *  5. Actividades   (ref departamentos, documentos)
 *  6. Usuarios      (ref roles, departamentos)
 *  6. Canales envio
 *  7. Politicas     (ref usuarios)
 *  8. Diagramas     (ref politicas, actividades, departamentos, usuarios)
 *  9. Formularios   (ref nodos del diagrama)
 * 10. Tramites      (ref clientes, politica, nodos)
 * 11. Estados       (ref tramites, nodos, usuarios)
 * 12. Expedientes   (ref tramites, nodos, departamentos, usuarios)
 * 13. Adjuntos      (ref secciones, usuarios)
 * 14. Trazabilidad  (ref tramites, nodos, usuarios)
 * 15. Notificaciones(ref usuarios, tramites)
 * 16. Metricas      (ref tramites, actividades, departamentos)
 * 17. Reportes      (ref usuarios)
 * 18. Logs agente   (ref usuarios, tramites)
 * 19. Colaboraciones(ref diagramas, usuarios)
 * 20. Versiones     (ref politicas, diagramas, usuarios)
 *
 * Uso en produccion:
 *   java -jar backend.jar
 *   (el seeder corre al inicio via @PostConstruct en DataSeeder, todos los seeders
 *    verifican si los datos ya existen antes de insertar, por lo que es seguro
 *    ejecutar multiples veces sin generar duplicados)
 */
@Component
@Slf4j
public class MasterSeeder {

    @Autowired private PermisoSeeder      permisoSeeder;
    @Autowired private RolSeeder          rolSeeder;
    @Autowired private DepartamentoSeeder departamentoSeeder;
    @Autowired private DocumentoSeeder    documentoSeeder;
    @Autowired private ActividadSeeder    actividadSeeder;
    @Autowired private UsuarioSeeder      usuarioSeeder;
    @Autowired private CanalEnvioSeeder   canalEnvioSeeder;
    @Autowired private PoliticaSeeder     politicaSeeder;
    @Autowired private DiagramaSeeder     diagramaSeeder;
    @Autowired private FormularioSeeder   formularioSeeder;
    @Autowired private TramiteSeeder      tramiteSeeder;
    @Autowired private EstadoSeeder       estadoSeeder;
    @Autowired private ExpedienteSeeder   expedienteSeeder;
    @Autowired private AdjuntoSeeder      adjuntoSeeder;
    @Autowired private TrazabilidadSeeder trazabilidadSeeder;
    @Autowired private NotificacionSeeder notificacionSeeder;
    @Autowired private MetricaSeeder      metricaSeeder;
    @Autowired private ReporteSeeder      reporteSeeder;
    @Autowired private LogAgenteSeeder    logAgenteSeeder;
    @Autowired private ColaboracionSeeder colaboracionSeeder;
    @Autowired private VersionSeeder      versionSeeder;

    // ── Parte 2 ──────────────────────────────────────────────────────────
    @Autowired private PermisoPuntoAtencionSeeder permisoPuntoAtencionSeeder;
    @Autowired private RepositorioDocumentalSeeder repositorioDocumentalSeeder;
    @Autowired private AlertaAnomaliaSeeder       alertaAnomaliaSeeder;
    @Autowired private TramiteIaPatchSeeder       tramiteIaPatchSeeder;

    public void seedAll() {
        log.info("========================================");
        log.info("  INICIANDO SEED COMPLETO DEL SISTEMA  ");
        log.info("========================================");

        permisoSeeder.seed();
        rolSeeder.seed();
        departamentoSeeder.seed();
        documentoSeeder.seed();
        actividadSeeder.seed();
        usuarioSeeder.seed();
        canalEnvioSeeder.seed();
        politicaSeeder.seed();
        diagramaSeeder.seed();
        formularioSeeder.seed();
        tramiteSeeder.seed();
        estadoSeeder.seed();
        expedienteSeeder.seed();
        adjuntoSeeder.seed();
        trazabilidadSeeder.seed();
        notificacionSeeder.seed();
        metricaSeeder.seed();
        reporteSeeder.seed();
        logAgenteSeeder.seed();
        colaboracionSeeder.seed();
        versionSeeder.seed();

        // ── Parte 2 ──────────────────────────────────────────────────────
        // Requiere políticas + actividades sembradas.
        permisoPuntoAtencionSeeder.seed();
        // Crea el contenedor de repositorio por política (sin S3, sin docs).
        // Cierra el 404 en GET /api/politicas/{id}/repositorio.
        repositorioDocumentalSeeder.seed();
        // Patch sobre trámites existentes: rellena riesgoDemora, probSuperarSla
        // y rutaSugerida (CU-42/CU-43) si están vacíos. Requiere trámites +
        // diagramas + nodos sembrados.
        tramiteIaPatchSeeder.seed();
        // Requiere trámites sembrados (apunta a tramiteId reales).
        alertaAnomaliaSeeder.seed();

        log.info("========================================");
        log.info("       SEED COMPLETADO EXITOSAMENTE    ");
        log.info("========================================");
    }
}
