package com.example.demo.config.seeders;

import com.example.demo.models.Rol;
import com.example.demo.repositories.RolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class RolSeeder {

    @Autowired private RolRepository rolRepository;

    public void seed() {
        crearRol("Cliente",       "Ciudadano que inicia tramites",
                List.of("CONSULTAR_MIS_TRAMITES", "INICIAR_TRAMITE"), true);
        crearRol("Funcionario",   "Empleado que procesa tramites",
                List.of("CONSULTAR_MIS_TRAMITES", "COMPLETAR_NODO", "VER_TODOS_TRAMITES"), true);
        crearRol("Administrador", "Administrador del sistema",
                List.of("GESTIONAR_USUARIOS", "GESTIONAR_DEPARTAMENTOS", "GESTIONAR_POLITICAS",
                        "GESTIONAR_DIAGRAMAS", "GESTIONAR_ROLES", "VER_TODOS_TRAMITES",
                        "VER_REPORTES", "EXPORTAR_REPORTES"), true);
        crearRol("SuperUser",     "Acceso total al sistema", List.of("*"), true);
        log.info("[Seeder] Roles OK");
    }

    private void crearRol(String nombre, String descripcion, List<String> permisos, boolean esSistema) {
        if (rolRepository.findByNombre(nombre).isEmpty()) {
            Rol r = new Rol();
            r.setNombre(nombre);
            r.setDescripcion(descripcion);
            r.setPermisos(permisos);
            r.setEsSistema(esSistema);
            r.setFechaCreacion(LocalDateTime.now());
            rolRepository.save(r);
        }
    }
}
