package com.example.demo.services;

import com.example.demo.models.Trazabilidad;
import com.example.demo.repositories.TrazabilidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class TrazabilidadService {

    @Autowired
    private TrazabilidadRepository trazabilidadRepository;

    public Trazabilidad registrar(String tramiteId, String actorId, String accion,
                                  String nodoId, Map<String, Object> datosDespues) {
        Trazabilidad previa = trazabilidadRepository.findTopByTramiteIdOrderByTimestampDesc(tramiteId);
        String hashAnterior = previa != null ? previa.getHashActual()
                : "0000000000000000000000000000000000000000000000000000000000000000";

        Trazabilidad nueva = new Trazabilidad();
        nueva.setTramiteId(tramiteId);
        nueva.setActorId(actorId);
        nueva.setAccion(accion);
        nueva.setNodoId(nodoId);
        nueva.setDatosDespues(datosDespues);
        nueva.setTimestamp(LocalDateTime.now());
        nueva.setHashAnterior(hashAnterior);

        String inputToHash = tramiteId + accion + nueva.getTimestamp() + hashAnterior;
        nueva.setHashActual(generarHash(inputToHash));

        return trazabilidadRepository.save(nueva);
    }

    private String generarHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error de integridad: algoritmo SHA-256 no disponible.");
        }
    }
}
