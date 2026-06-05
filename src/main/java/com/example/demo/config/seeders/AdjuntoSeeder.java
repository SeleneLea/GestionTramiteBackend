package com.example.demo.config.seeders;

import com.example.demo.models.EstadoSeccion;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.TranscripcionVoz;
import com.example.demo.repositories.SeccionExpedienteRepository;
import com.example.demo.repositories.TranscripcionVozRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class AdjuntoSeeder {

    @Autowired private TranscripcionVozRepository transcripcionRepository;
    @Autowired private SeccionExpedienteRepository seccionRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (transcripcionRepository.count() > 0) {
            log.info("[Seeder] Transcripciones ya existen, omitiendo");
            return;
        }

        String funcTecId = userId("funcionario@cre.bo");

        List<SeccionExpediente> secciones = seccionRepository.findAll();

        secciones.stream()
                .filter(s -> EstadoSeccion.esDerivada(s.getEstado()) && s.getOrdenSeccion() == 2)
                .forEach(s -> crearTranscripcion(s.getId(), funcTecId,
                        "La instalacion presenta condiciones optimas, distancia a la red aproximadamente 12 metros, "
                        + "no requiere obra civil previa. Recomiendo proceder con la conexion monofasica.",
                        45.5f, 0.94f, s.getFechaCompletado()));

        log.info("[Seeder] Transcripciones OK");
    }

    private void crearTranscripcion(String seccionId, String funcionarioId, String texto,
                                     float duracion, float confianza, LocalDateTime fecha) {
        TranscripcionVoz tv = new TranscripcionVoz();
        tv.setSeccionId(seccionId);
        tv.setFuncionarioId(funcionarioId);
        tv.setTextoTranscrito(texto);
        tv.setDuracionSegundos(duracion);
        tv.setConfianzaTranscripcion(confianza);
        tv.setFechaTranscripcion(fecha != null ? fecha : LocalDateTime.now());
        transcripcionRepository.save(tv);
    }

    private String userId(String email) {
        return usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
