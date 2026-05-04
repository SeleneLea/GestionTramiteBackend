package com.example.demo.services;

import com.example.demo.dto.VerificacionCadenaResponse;
import com.example.demo.models.Trazabilidad;
import com.example.demo.repositories.TrazabilidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class TrazabilidadService {

    private static final String HASH_GENESIS =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @Autowired
    private TrazabilidadRepository trazabilidadRepository;

    public Trazabilidad registrar(String tramiteId, String actorId, String accion,
                                  String nodoId, Map<String, Object> datosDespues) {
        Trazabilidad previa = trazabilidadRepository.findTopByTramiteIdOrderByTimestampDescIdDesc(tramiteId);
        String hashAnterior = previa != null ? previa.getHashActual() : HASH_GENESIS;

        Trazabilidad nueva = new Trazabilidad();
        nueva.setTramiteId(tramiteId);
        nueva.setActorId(actorId);
        nueva.setAccion(accion);
        nueva.setNodoId(nodoId);
        nueva.setDatosDespues(datosDespues);
        // Truncar a milisegundos: MongoDB persiste LocalDateTime como BSON Date (ms).
        // Sin esto, los nanosegundos de now() entran al hash pero se pierden al releer,
        // y verificarCadena marcaría una traza VÁLIDA como rota.
        nueva.setTimestamp(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        nueva.setHashAnterior(hashAnterior);

        nueva.setHashActual(generarHash(construirInputHash(nueva)));

        return trazabilidadRepository.save(nueva);
    }

    /**
     * Verifica la integridad de la cadena de hashes de un trámite recorriendo
     * los registros en orden ascendente por timestamp. Para cada eslabón:
     *  - recomputa el hash con el MISMO helper usado al registrar y lo compara
     *    con el hashActual almacenado (detecta manipulación de los campos);
     *  - comprueba que su hashAnterior coincide con el hashActual del eslabón
     *    previo (y que el primero parte del hash génesis de 64 ceros).
     * Devuelve el primer eslabón roto (si lo hay).
     */
    public VerificacionCadenaResponse verificarCadena(String tramiteId) {
        List<Trazabilidad> registros = trazabilidadRepository.findByTramiteIdOrderByTimestampAscIdAsc(tramiteId);

        String hashEsperadoAnterior = HASH_GENESIS;
        for (Trazabilidad t : registros) {
            String hashRecomputado = generarHash(construirInputHash(t));
            boolean encadenadoOk = hashEsperadoAnterior.equals(t.getHashAnterior());
            boolean contenidoOk = hashRecomputado.equals(t.getHashActual());

            if (!encadenadoOk || !contenidoOk) {
                return new VerificacionCadenaResponse(false, t.getId());
            }

            hashEsperadoAnterior = t.getHashActual();
        }

        return new VerificacionCadenaResponse(true, null);
    }

    // B6: el hash cubre TODOS los campos materiales (actor, nodo, datos) con
    // separadores y serialización canónica (claves ordenadas), para que la
    // cadena sea tamper-evident sobre lo que realmente importa auditar.
    private String construirInputHash(Trazabilidad t) {
        Map<String, Object> datos = t.getDatosDespues();
        String datosCanon = new java.util.TreeMap<>(
                datos != null ? datos : java.util.Map.<String, Object>of()).toString();
        return String.join("|",
                t.getTramiteId() == null ? "" : t.getTramiteId(),
                t.getActorId() == null ? "" : t.getActorId(),
                t.getAccion() == null ? "" : t.getAccion(),
                t.getNodoId() == null ? "" : t.getNodoId(),
                datosCanon,
                String.valueOf(t.getTimestamp()),
                t.getHashAnterior() == null ? "" : t.getHashAnterior());
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
