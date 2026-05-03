package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern(
            "dd/MMM/yyyy HH:mm:ss", new Locale("es"));

    private static final String ESC = String.valueOf((char) 27);
    private static final String ANSI_RESET = ESC + "[0m";
    private static final String ANSI_GREEN = ESC + "[32m";
    private static final String ANSI_YELLOW = ESC + "[33m";
    private static final String ANSI_RED = ESC + "[31m";
    private static final String ANSI_GRAY = ESC + "[90m";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long inicio = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long duracion = System.currentTimeMillis() - inicio;
            int status = response.getStatus();
            String metodo = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            String rutaCompleta = query != null ? uri + "?" + query : uri;
            String protocolo = request.getProtocol();
            String timestamp = LocalDateTime.now().format(FORMATO);

            String color = colorParaStatus(status);

            log.info("{}[{}]{} \"{} {} {}\" {}{}{} {}ms",
                    ANSI_GRAY, timestamp, ANSI_RESET,
                    metodo, rutaCompleta, protocolo,
                    color, status, ANSI_RESET,
                    duracion);
        }
    }

    private String colorParaStatus(int status) {
        if (status >= 500) return ANSI_RED;
        if (status >= 400) return ANSI_YELLOW;
        if (status >= 300) return ANSI_GRAY;
        if (status >= 200) return ANSI_GREEN;
        return ANSI_GRAY;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/ws")
                || uri.startsWith("/actuator")
                || uri.equals("/favicon.ico");
    }
}
