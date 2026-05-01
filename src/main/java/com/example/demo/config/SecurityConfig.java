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
                // Swagger
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/v3/api-docs"
                ).permitAll()
                
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/api/usuarios/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/usuarios/funcionarios").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                // Lecturas abiertas a cualquier autenticado
                .requestMatchers(HttpMethod.GET, "/api/departamentos/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/actividades/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/politicas/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/roles/**").authenticated()
                // Escrituras solo admin
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
                // Motor de Workflow y tramites (C2)
                .requestMatchers(HttpMethod.GET, "/api/tramites/mis-tramites").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/tramites/mis-pendientes").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET, "/api/tramites/*/estado").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/tramites/iniciar").hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/completar-nodo").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/derivar").hasRole("FUNCIONARIO")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/devolver").hasRole("FUNCIONARIO")
                .requestMatchers(HttpMethod.POST, "/api/tramites/*/decision-final").hasRole("FUNCIONARIO")

                // Expediente digital (C2)
                .requestMatchers(HttpMethod.GET, "/api/expedientes/tramite/**").hasAnyRole("CLIENTE", "FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/expedientes/seccion/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/expedientes/secciones/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                // Colaboracion en diagramas (C2)
                .requestMatchers(HttpMethod.POST, "/api/colaboracion/diagrama/*/invitar").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/colaboracion/*/responder").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                // Diagramas de Workflow (G5)
                .requestMatchers(HttpMethod.GET, "/api/diagramas/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/diagramas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/diagramas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PATCH, "/api/diagramas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/diagramas/**").hasRole("ADMINISTRADOR")
                // Nodos y Transiciones (G5)
                .requestMatchers(HttpMethod.GET, "/api/nodos/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/nodos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/nodos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/nodos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET, "/api/transiciones/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/transiciones/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/transiciones/**").hasRole("ADMINISTRADOR")
                // Diseño por IA (G5)
                .requestMatchers("/api/workflow-design/**").hasRole("ADMINISTRADOR")
                // Permisos (G5)
                .requestMatchers(HttpMethod.GET, "/api/permisos/**").hasRole("ADMINISTRADOR")

                // Notificaciones
                .requestMatchers("/api/notificaciones/**").authenticated()

                // Métricas y cuellos de botella
                .requestMatchers(HttpMethod.GET, "/api/metricas/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")

                // Reportes
                .requestMatchers("/api/reportes/**").hasRole("ADMINISTRADOR")

                // Agente IA
                .requestMatchers("/api/agente/**").authenticated()

                // Trazabilidad e historial
                .requestMatchers("/api/trazabilidad/**").hasAnyRole("FUNCIONARIO", "ADMINISTRADOR")
                .requestMatchers("/api/historial/**").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
            "http://localhost:4200",
            "http://localhost:4300",
            "http://localhost:8100",
            "http://localhost:3000",
            "https://web-five-kappa-99.vercel.app",
            "http://44.213.74.152:8080",
            "https://44.213.74.152:8443"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
