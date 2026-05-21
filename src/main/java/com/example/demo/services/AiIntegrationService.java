package com.example.demo.services;

import com.example.demo.dto.AgenteRequest;
import com.example.demo.dto.AgenteResponse;
import com.example.demo.models.LogAgente;
import com.example.demo.models.TranscripcionVoz;
import com.example.demo.repositories.LogAgenteRepository;
import com.example.demo.repositories.TranscripcionVozRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Integración IA del expediente (CU-30 transcripción de voz) y del asistente
 * (CU-31).
 *
 * <p>UNIFICADO contra el microservicio FastAPI real: ya NO existen las dos rutas
 * legadas/muertas ({@code 8001/api/transcribir}, {@code 8002/api/chat} de n8n).
 * La transcripción va por {@link IaProxyService} (mismo micro que el resto de la
 * IA) y el asistente usa directamente {@link AgenteAsistenciaService}, que es el
 * CU-31 híbrido (clasificador TensorFlow + LLM + base de conocimiento local).
 */
@Service
public class AiIntegrationService {

    @Autowired private TranscripcionVozRepository vozRepo;
    @Autowired private LogAgenteRepository agenteRepo;
    @Autowired private AgenteAsistenciaService agenteKb;
    @Autowired private IaProxyService iaProxy;

    /**
     * Transcribe el audio dictado (CU-30) vía el microservicio real
     * ({@code /nlp/voz-a-formulario} con schema vacío, del que solo se usa
     * {@code texto_transcrito}). Si el micro cae, el proxy lanza
     * {@code 503 IA_NO_DISPONIBLE} y no se persiste nada inválido.
     */
    public TranscripcionVoz transcribirAudio(String seccionId, MultipartFile archivo, String funcionarioId) {
        Map<String, Object> resp = iaProxy.vozAFormulario(archivo, "[]");
        Object textoObj = resp.get("texto_transcrito");
        String texto = textoObj != null ? textoObj.toString() : "";

        TranscripcionVoz tv = new TranscripcionVoz();
        tv.setSeccionId(seccionId);
        tv.setFuncionarioId(funcionarioId);
        tv.setTextoTranscrito(texto);
        tv.setDuracionSegundos(0.0f);
        tv.setConfianzaTranscripcion(0.9f);
        tv.setFechaTranscripcion(LocalDateTime.now());
        return vozRepo.save(tv);
    }

    /**
     * Responde al asistente conversacional (CU-31) con el motor híbrido
     * (TensorFlow + LLM + KB local) y registra la interacción en el log.
     */
    public AgenteResponse consultarAgente(AgenteRequest input, String usuarioId, String rolId) {
        long start = System.currentTimeMillis();
        AgenteResponse resp = agenteKb.responderInteligente(input, rolId);
        long end = System.currentTimeMillis();

        LogAgente lg = new LogAgente();
        lg.setUsuarioId(usuarioId);
        lg.setContextoModulo(input.getModuloActivo());
        lg.setContextoRol(rolId);
        lg.setContextoTramiteId(input.getTramiteIdOpcional());
        lg.setConsultaUsuario(input.getConsulta());
        lg.setRespuestaAgente(resp.getRespuesta());
        lg.setTiempoRespuestaMs((float) (end - start));
        lg.setFueUtil(false);
        lg.setTimestamp(LocalDateTime.now());
        lg = agenteRepo.save(lg);

        resp.setIdLogBaseDatos(lg.getId());
        return resp;
    }
}
