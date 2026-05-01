package com.example.demo.services;

import com.example.demo.dto.AgenteRequest;
import com.example.demo.dto.AgenteResponse;
import com.example.demo.models.LogAgente;
import com.example.demo.models.TranscripcionVoz;
import com.example.demo.repositories.LogAgenteRepository;
import com.example.demo.repositories.TranscripcionVozRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class AiIntegrationService {

    @Autowired
    private TranscripcionVozRepository vozRepo;

    @Autowired
    private LogAgenteRepository agenteRepo;

    @Autowired
    private AgenteAsistenciaService agenteKb;

    @Value("${app.ai.voz-url:http://localhost:8001/api/transcribir}")
    private String transcripcionUrl;

    @Value("${app.ai.agente-url:http://localhost:8002/api/chat}")
    private String agenteUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public TranscripcionVoz transcribirAudio(String seccionId, MultipartFile archivo, String funcionarioId) {
        String texto;
        float confianza = 0.0f;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(archivo.getBytes()) {
                @Override
                public String getFilename() {
                    return archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "audio.wav";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(transcripcionUrl, requestEntity, String.class);
            texto = response != null && !response.isBlank()
                    ? response
                    : "Respuesta vacia del servicio de transcripcion";
            confianza = 0.95f;
        } catch (Exception e) {
            texto = "[Error en conexion con microservicio de voz] " + e.getMessage();
        }

        TranscripcionVoz tv = new TranscripcionVoz();
        tv.setSeccionId(seccionId);
        tv.setFuncionarioId(funcionarioId);
        tv.setTextoTranscrito(texto);
        tv.setDuracionSegundos(0.0f);
        tv.setConfianzaTranscripcion(confianza);
        tv.setFechaTranscripcion(LocalDateTime.now());

        return vozRepo.save(tv);
    }

    public AgenteResponse consultarAgente(AgenteRequest input, String usuarioId, String rolId) {
        long start = System.currentTimeMillis();
        AgenteResponse resp;

        try {
            String response = restTemplate.postForObject(agenteUrl, input, String.class);
            if (response != null && !response.isBlank()) {
                resp = new AgenteResponse();
                resp.setRespuesta(response);
                resp.setFuente("n8n");
                if (resp.getAccion() == null) {
                    AgenteResponse local = agenteKb.responder(input, rolId);
                    resp.setAccion(local.getAccion());
                }
            } else {
                resp = agenteKb.responder(input, rolId);
            }
        } catch (Exception e) {
            resp = agenteKb.responder(input, rolId);
        }

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
