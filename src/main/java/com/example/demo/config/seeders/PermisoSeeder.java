package com.example.demo.config.seeders;

import com.example.demo.models.Permiso;
import com.example.demo.repositories.PermisoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PermisoSeeder {

    @Autowired private PermisoRepository permisoRepository;

    public void seed() {
        crearPermiso("VER_REPORTES",            "reportes", "Ver reportes del sistema");
        crearPermiso("EXPORTAR_REPORTES",       "reportes", "Exportar reportes");
        crearPermiso("CONSULTAR_MIS_TRAMITES",  "tramites", "Ver los propios tramites");
        crearPermiso("INICIAR_TRAMITE",         "tramites", "Iniciar un nuevo tramite");
        crearPermiso("COMPLETAR_NODO",          "tramites", "Completar nodo de workflow");
        crearPermiso("VER_TODOS_TRAMITES",      "tramites", "Ver todos los tramites");
        crearPermiso("GESTIONAR_USUARIOS",      "usuarios", "CRUD de usuarios");
        crearPermiso("GESTIONAR_DEPARTAMENTOS", "config",   "CRUD de departamentos");
        crearPermiso("GESTIONAR_POLITICAS",     "config",   "CRUD de politicas de negocio");
        crearPermiso("GESTIONAR_DIAGRAMAS",     "workflow", "CRUD de diagramas de workflow");
        crearPermiso("GESTIONAR_ROLES",         "config",   "CRUD de roles y permisos");
        log.info("[Seeder] Permisos OK");
    }

    private void crearPermiso(String codigo, String modulo, String descripcion) {
        if (permisoRepository.findByCodigo(codigo).isEmpty()) {
            Permiso p = new Permiso();
            p.setCodigo(codigo);
            p.setModulo(modulo);
            p.setDescripcion(descripcion);
            permisoRepository.save(p);
        }
    }
}
