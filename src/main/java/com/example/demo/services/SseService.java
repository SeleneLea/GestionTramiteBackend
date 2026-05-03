package com.example.demo.services;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Backplane de Server-Sent Events para empujar notificaciones a los clientes
 * en cuanto se crean en el backend (CU-28). Un usuario puede tener varias
 * conexiones abiertas (web + móvil), por eso mantenemos una lista por userId.
 *
 * Reemplaza el polling cada 30s del cliente móvil: el push llega en <1s.
 */
@Service
public class SseService {

    /** Tiempo sin actividad antes de que el emitter se considere muerto (1 hora). */
    private static final long TIMEOUT_MS = 60L * 60L * 1000L;

    /** Intervalo del heartbeat para mantener viva la conexión a través de proxies. */
    private static final long HEARTBEAT_SEC = 25L;

    private final Map<String, List<SseEmitter>> emittersPorUsuario = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseService() {
        // Comentario / ping cada 25s — no llega al payload de eventos pero mantiene viva la conexión.
        heartbeat.scheduleAtFixedRate(this::ping, HEARTBEAT_SEC, HEARTBEAT_SEC, TimeUnit.SECONDS);
    }

    public SseEmitter abrirStream(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersPorUsuario.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remover(userId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remover(userId, emitter);
        });
        emitter.onError(ex -> remover(userId, emitter));

        // Evento inicial — el cliente sabe que el stream está vivo.
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("ok", true)));
        } catch (IOException ex) {
            remover(userId, emitter);
        }
        return emitter;
    }

    /**
     * Empuja un evento a todas las conexiones abiertas del usuario indicado.
     * Si un emitter falla al enviar, se descarta silenciosamente.
     */
    public void enviar(String userId, String evento, Object payload) {
        List<SseEmitter> lista = emittersPorUsuario.get(userId);
        if (lista == null || lista.isEmpty()) return;
        for (SseEmitter emitter : lista) {
            try {
                emitter.send(SseEmitter.event().name(evento).data(payload));
            } catch (Exception ex) {
                remover(userId, emitter);
            }
        }
    }

    private void ping() {
        for (Map.Entry<String, List<SseEmitter>> entry : emittersPorUsuario.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (Exception ex) {
                    remover(entry.getKey(), emitter);
                }
            }
        }
    }

    private void remover(String userId, SseEmitter emitter) {
        List<SseEmitter> lista = emittersPorUsuario.get(userId);
        if (lista == null) return;
        lista.remove(emitter);
        if (lista.isEmpty()) {
            emittersPorUsuario.remove(userId);
        }
    }

    @PreDestroy
    public void cerrarTodo() {
        heartbeat.shutdownNow();
        emittersPorUsuario.values().forEach(list -> list.forEach(SseEmitter::complete));
        emittersPorUsuario.clear();
    }
}
