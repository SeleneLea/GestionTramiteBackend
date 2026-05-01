package com.example.demo.config.seeders;

import com.example.demo.models.Departamento;
import com.example.demo.repositories.DepartamentoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class DepartamentoSeeder {

    @Autowired private DepartamentoRepository departamentoRepository;

    public void seed() {
        crearDepto("ATC", "Atencion al Cliente",
                "Recepcion y gestion de solicitudes de clientes");
        crearDepto("TEC", "Area Tecnica",
                "Inspecciones tecnicas y elaboracion de presupuestos");
        crearDepto("LEG", "Area Legal",
                "Revision y aprobacion de contratos");
        crearDepto("OPE", "Operaciones",
                "Ejecucion y cierre de trabajos de campo");
        log.info("[Seeder] Departamentos OK");
    }

    private void crearDepto(String codigo, String nombre, String descripcion) {
        if (departamentoRepository.findByCodigo(codigo).isEmpty()) {
            Departamento d = new Departamento();
            d.setCodigo(codigo);
            d.setNombre(nombre);
            d.setDescripcion(descripcion);
            d.setActivo(true);
            d.setFechaCreacion(LocalDateTime.now());
            departamentoRepository.save(d);
        }
    }
}
