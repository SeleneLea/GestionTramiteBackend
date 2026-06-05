package com.example.demo.services;

import com.example.demo.dto.DocumentoEvento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class DocumentoCollabBroadcaster {

    @Autowired
    private SimpMessagingTemplate messaging;

    public void edicion(String documentoId, String tipo, Object payload, String autorId) {
        messaging.convertAndSend(
                "/topic/documento/" + documentoId + "/edicion",
                DocumentoEvento.of(tipo, documentoId, payload, autorId));
    }

    public void presencia(String documentoId, String tipo, Object payload, String autorId) {
        messaging.convertAndSend(
                "/topic/documento/" + documentoId + "/presencia",
                DocumentoEvento.of(tipo, documentoId, payload, autorId));
    }
}
