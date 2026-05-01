package com.example.demo.config;

import com.example.demo.config.seeders.MasterSeeder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada del seed automatico al iniciar la aplicacion.
 * Delega toda la logica a MasterSeeder y sus seeders individuales.
 *
 * Para poblar la base de datos en produccion basta con ejecutar:
 *   java -jar backend.jar
 *
 * Todos los seeders verifican si sus datos ya existen antes de insertar,
 * por lo que es seguro ejecutar multiples veces sin generar duplicados.
 */
@Component
public class DataSeeder {

    @Autowired
    private MasterSeeder masterSeeder;

    @PostConstruct
    public void seed() {
        masterSeeder.seedAll();
    }
}
