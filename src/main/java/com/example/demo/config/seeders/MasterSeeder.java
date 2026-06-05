package com.example.demo.config.seeders;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @Autowired private PermisoPuntoAtencionSeeder permisoPuntoAtencionSeeder;
    @Autowired private RepositorioDocumentalSeeder repositorioDocumentalSeeder;
    @Autowired private DocumentoArchivoSeeder     documentoArchivoSeeder;
    @Autowired private AlertaAnomaliaSeeder       alertaAnomaliaSeeder;
    @Autowired private TramiteIaPatchSeeder       tramiteIaPatchSeeder;
    @Autowired private DatosMasivosSeeder         datosMasivosSeeder;

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

        permisoPuntoAtencionSeeder.seed();

        repositorioDocumentalSeeder.seed();

        documentoArchivoSeeder.seed();

        tramiteIaPatchSeeder.seed();

        alertaAnomaliaSeeder.seed();

        datosMasivosSeeder.seed();

        log.info("========================================");
        log.info("       SEED COMPLETADO EXITOSAMENTE    ");
        log.info("========================================");
    }
}
