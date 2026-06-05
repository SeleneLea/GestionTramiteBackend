package com.example.demo.config;

import com.example.demo.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/v3/api-docs"
                ).permitAll()

                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()

                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/usuarios/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/usuarios/funcionarios").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")

                .requestMatchers(HttpMethod.GET, "/api/departamentos/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/actividades/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/politicas/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/roles/**").authenticated()

                .requestMatchers(HttpMethod.POST, "/api/departamentos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/departamentos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/departamentos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/actividades/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/actividades/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/actividades/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/politicas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/politicas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/politicas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/roles/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/roles/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PATCH, "/api/roles/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/roles/**").hasRole("ADMINISTRADOR")

                .requestMatchers(HttpMethod.GET, "/api/tramites/mis-tramites").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/tramites/mis-pendientes").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET, "/api/tramites/*/estado").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/tramites/*/resolucion").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/tramites/iniciar").hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/completar-nodo").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/aceptar").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/reasignar").hasRole("FUNCIONARIO")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/derivar").hasRole("FUNCIONARIO")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/devolver").hasRole("FUNCIONARIO")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/decision-final").hasRole("FUNCIONARIO")

                .requestMatchers(HttpMethod.GET, "/api/expedientes/tramite/**").hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/expedientes/seccion/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/expedientes/secciones/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                .requestMatchers(HttpMethod.POST, "/api/colaboracion/diagrama/*/invitar").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/colaboracion/*/responder").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                .requestMatchers(HttpMethod.GET, "/api/diagramas/**").authenticated()

                .requestMatchers(HttpMethod.POST, "/api/diagramas/*/nodos").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.POST, "/api/diagramas/*/transiciones").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.POST, "/api/diagramas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/diagramas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PATCH, "/api/diagramas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/diagramas/**").hasRole("ADMINISTRADOR")

                .requestMatchers(HttpMethod.GET, "/api/nodos/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/nodos/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.PUT, "/api/nodos/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.PATCH, "/api/nodos/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.DELETE, "/api/nodos/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.GET, "/api/transiciones/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/transiciones/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.PUT, "/api/transiciones/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                .requestMatchers(HttpMethod.DELETE, "/api/transiciones/**").hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")

                .requestMatchers("/api/workflow-design/**").hasRole("ADMINISTRADOR")

                .requestMatchers(HttpMethod.GET, "/api/permisos/**").hasRole("ADMINISTRADOR")

                .requestMatchers("/api/notificaciones/**").authenticated()

                .requestMatchers(HttpMethod.GET, "/api/metricas/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                .requestMatchers("/api/reportes/**").hasRole("ADMINISTRADOR")

                .requestMatchers("/api/agente/**").authenticated()

                .requestMatchers("/api/trazabilidad/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/historial/**").authenticated()

                .requestMatchers(HttpMethod.GET,  "/api/repositorios/**").authenticated()
                .requestMatchers(HttpMethod.GET,  "/api/tramites/*/repositorio").authenticated()

                .requestMatchers(HttpMethod.POST, "/api/tramites/*/documentos").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR", "CLIENTE")

                .requestMatchers(HttpMethod.POST, "/api/documentos/*/versiones").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                .requestMatchers(HttpMethod.POST, "/api/documentos/*/onlyoffice/callback").permitAll()

                .requestMatchers(HttpMethod.GET,  "/api/documentos/*/auditoria").hasRole("ADMINISTRADOR")

                .requestMatchers(HttpMethod.GET,  "/api/documentos/**").authenticated()
                .requestMatchers(HttpMethod.GET,  "/api/tramites/*/documentos").authenticated()

                .requestMatchers(HttpMethod.PUT,  "/api/actividades/*/permiso-documental").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET,  "/api/politicas/*/permisos-documentales").authenticated()
                .requestMatchers(HttpMethod.GET,  "/api/politicas/*/actividades/*/permiso-documental").authenticated()

                .requestMatchers(HttpMethod.POST, "/api/expedientes/secciones/*/dictar").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                .requestMatchers(HttpMethod.POST, "/api/tramites/sugerir-politica").hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/sugerencias/**").hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")

                .requestMatchers(HttpMethod.POST, "/api/reportes/consulta-natural").hasRole("ADMINISTRADOR")

                .requestMatchers(HttpMethod.POST, "/api/tramites/*/ruta-optima").authenticated()

                .requestMatchers(HttpMethod.GET,  "/api/tramites/en-riesgo").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                .requestMatchers("/api/alertas-anomalias/**").hasRole("ADMINISTRADOR")

                .anyRequest().authenticated()
            )

            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                (request, response, authEx) ->
                    response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED,
                        "No autenticado o sesión expirada")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedOriginPatterns(List.of(
            "http://localhost:4200",
            "http://localhost:4300",
            "http://localhost:8100",
            "http://localhost:3000",
            "https://*.vercel.app",
            "https://ficctuagrmbolivia.online",
            "https://*.ficctuagrmbolivia.online"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
