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

@Service
public class SseService {

    private static final long TIMEOUT_MS = 60L * 60L * 1000L;

    private static final long HEARTBEAT_SEC = 25L;

    private final Map<String, List<SseEmitter>> emittersPorUsuario = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseService() {
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

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("ok", true)));
        } catch (IOException ex) {
            remover(userId, emitter);
        }
        return emitter;
    }

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
