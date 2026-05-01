package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Sistema de Gestión de Trámites — API")
                        .description("""
                                Backend del sistema de gestión de trámites.
                                Motor de Workflow + Expediente Digital + IA.
                                
                                **Roles del sistema:**
                                - `Administrador` — diseña flujos, gestiona configuración
                                - `Funcionario` — ejecuta actividades asignadas
                                - `Cliente` — solicita y consulta trámites
                                
                                **Autenticación:** JWT Bearer token.
                                Obtener token en `POST /api/auth/login` y pegarlo en el botón Authorize.
                                """)
                        .version("1.0.0 — Ciclo 1")
                        .contact(new Contact()
                                .name("Luis David Guzmán Rojas")
                                .email("admin@cre.bo")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Pega aquí el token obtenido en /api/auth/login")));
    }
}
