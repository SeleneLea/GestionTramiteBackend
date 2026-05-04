package com.example.demo.config;

import com.example.demo.security.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * CU-15 / Colaboración en tiempo real sobre diagramas.
 * Expone /ws como endpoint STOMP. El JWT se pasa como query string `?token=...`
 * porque los navegadores no permiten setear cabeceras Authorization en WebSocket.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Misma lista que CorsConfigurationSource en SecurityConfig — mantenerlas sincronizadas.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:4200",
                        "http://localhost:4300",
                        "http://localhost:8100",
                        "http://localhost:3000",
                        "https://*.vercel.app",
                        "https://ficctuagrmbolivia.online",
                        "https://*.ficctuagrmbolivia.online"
                )
                .addInterceptors(new JwtHandshakeInterceptor(jwtUtils));
    }

    /**
     * Handshake interceptor que valida el JWT del query string y guarda el userId
     * en los atributos de la sesión WebSocket, accesibles luego en los controladores.
     */
    static class JwtHandshakeInterceptor implements HandshakeInterceptor {
        private final JwtUtils jwtUtils;

        JwtHandshakeInterceptor(JwtUtils jwtUtils) {
            this.jwtUtils = jwtUtils;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                return false;
            }
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token == null || token.isBlank() || !jwtUtils.esValido(token)) {
                return false;
            }
            try {
                Claims claims = jwtUtils.validarYExtraer(token);
                String userId = claims.getSubject();
                String rol = claims.get("rol", String.class);
                if (userId == null || rol == null) return false;

                attributes.put("userId", userId);
                attributes.put("rol", rol);
                // Asocia un Principal a la sesión por si se requiere routing dirigido.
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))
                );
                attributes.put("auth", auth);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // no-op
        }
    }
}
