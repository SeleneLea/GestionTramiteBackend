package com.example.demo.services;

import com.example.demo.dto.DiagramaEvento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Centraliza el broadcast de eventos del diagramador colaborativo (CU-15).
 * Los controladores REST llaman a este componente tras cada mutación exitosa
 * para que todos los clientes suscritos al diagrama reciban el cambio.
 */
@Service
public class DiagramaCollabBroadcaster {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void broadcast(String diagramaId, String tipo, Object payload, String autorId) {
        if (diagramaId == null) return;
        DiagramaEvento evento = DiagramaEvento.of(tipo, diagramaId, payload, autorId);
        messagingTemplate.convertAndSend("/topic/diagramas/" + diagramaId, evento);
    }
}
