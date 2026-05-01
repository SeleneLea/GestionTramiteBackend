package com.example.demo.config.seeders;

import com.example.demo.models.Adjunto;
import com.example.demo.models.SeccionExpediente;
import com.example.demo.models.TranscripcionVoz;
import com.example.demo.repositories.AdjuntoRepository;
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

    @Autowired private AdjuntoRepository adjuntoRepository;
    @Autowired private TranscripcionVozRepository transcripcionRepository;
    @Autowired private SeccionExpedienteRepository seccionRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public void seed() {
        if (adjuntoRepository.count() > 0) {
            log.info("[Seeder] Adjuntos ya existen, omitiendo");
            return;
        }

        String funcAtcId = userId("func_atc@cre.bo");
        String funcTecId = userId("funcionario@cre.bo");
        String funcOpeId = userId("func_ope@cre.bo");

        // Adjuntos en secciones "completado" (primera seccion de cada expediente activo)
        List<SeccionExpediente> secciones = seccionRepository.findAll();

        secciones.stream()
                .filter(s -> "completado".equals(s.getEstado()) && s.getOrdenSeccion() == 1)
                .forEach(s -> {
                    crearAdjunto(s.getId(), "cedula_identidad.pdf",
                            "application/pdf", "/uploads/ci/cedula_7654321.pdf",
                            245_760L, funcAtcId, s.getFechaCompletado());
                    crearAdjunto(s.getId(), "plano_inmueble.jpg",
                            "image/jpeg", "/uploads/planos/plano_245.jpg",
                            1_024_000L, funcAtcId, s.getFechaCompletado());
                });

        secciones.stream()
                .filter(s -> "completado".equals(s.getEstado()) && s.getOrdenSeccion() == 2)
                .forEach(s -> {
                    crearAdjunto(s.getId(), "informe_inspeccion_tecnica.pdf",
                            "application/pdf", "/uploads/inspecciones/insp_" + s.getId() + ".pdf",
                            512_000L, funcTecId, s.getFechaCompletado());
                    crearAdjunto(s.getId(), "foto_sitio_01.jpg",
                            "image/jpeg", "/uploads/fotos/foto_" + s.getId() + "_01.jpg",
                            2_048_000L, funcTecId, s.getFechaCompletado());
                    crearTranscripcion(s.getId(), funcTecId,
                            "La instalacion presenta condiciones optimas, distancia a la red aproximadamente 12 metros, "
                            + "no requiere obra civil previa. Recomiendo proceder con la conexion monofasica.",
                            45.5f, 0.94f, s.getFechaCompletado());
                });

        secciones.stream()
                .filter(s -> "completado".equals(s.getEstado()) && s.getOrdenSeccion() == 5)
                .forEach(s -> {
                    crearAdjunto(s.getId(), "acta_cierre_conexion.pdf",
                            "application/pdf", "/uploads/actas/acta_cierre_" + s.getId() + ".pdf",
                            380_000L, funcOpeId, s.getFechaCompletado());
                    crearAdjunto(s.getId(), "foto_medidor_instalado.jpg",
                            "image/jpeg", "/uploads/medidores/med_MED20240421.jpg",
                            1_536_000L, funcOpeId, s.getFechaCompletado());
                });

        log.info("[Seeder] Adjuntos y transcripciones OK");
    }

    private void crearAdjunto(String seccionId, String nombre, String mime,
                               String url, long tamano, String subidoPorId, LocalDateTime fecha) {
        Adjunto a = new Adjunto();
        a.setSeccionId(seccionId);
        a.setNombreArchivo(nombre);
        a.setTipoMime(mime);
        a.setUrlAlmacenamiento(url);
        a.setTamanoBytes(tamano);
        a.setSubidoPorId(subidoPorId);
        a.setFechaSubida(fecha != null ? fecha : LocalDateTime.now());
        adjuntoRepository.save(a);
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
