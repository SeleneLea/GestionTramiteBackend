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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

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

    // B5: timeouts para no bloquear hilos de Tomcat si el microservicio cuelga.
    private final RestTemplate restTemplate = crearRestTemplate();

    private static RestTemplate crearRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

    public TranscripcionVoz transcribirAudio(String seccionId, MultipartFile archivo, String funcionarioId) {
        String texto;
        float confianza;

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
            // B4: deserializar el cuerpo y extraer el texto real, no guardar el JSON crudo.
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(transcripcionUrl, requestEntity, Map.class);
            if (resp == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "IA_NO_DISPONIBLE: respuesta vacia del servicio de voz");
            }
            Object textoObj = resp.getOrDefault("texto_transcrito", resp.get("texto"));
            texto = textoObj != null ? textoObj.toString() : "";
            Object confObj = resp.get("confianza");
            confianza = confObj instanceof Number ? ((Number) confObj).floatValue() : 0.9f;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // B4: NO persistir el error como si fuera una transcripción válida; degradar con 503.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "IA_NO_DISPONIBLE: " + e.getMessage(), e);
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
            // BK-M2: deserializar el cuerpo de n8n y extraer el texto real (respuesta/output),
            // no guardar el JSON crudo (mismo bug corregido en transcribirAudio).
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(agenteUrl, input, Map.class);
            Object textoObj = body != null ? body.getOrDefault("respuesta", body.get("output")) : null;
            String texto = textoObj != null ? textoObj.toString() : null;
            if (texto != null && !texto.isBlank()) {
                resp = new AgenteResponse();
                resp.setRespuesta(texto);
                resp.setFuente("n8n");
                Object accionObj = body.get("accion");
                if (accionObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> accionMap = (Map<String, Object>) accionObj;
                    AgenteResponse.AccionDirecta accion = new AgenteResponse.AccionDirecta();
                    Object label = accionMap.get("label");
                    Object ruta = accionMap.get("ruta");
                    Object tipo = accionMap.get("tipo");
                    accion.setLabel(label != null ? label.toString() : null);
                    accion.setRuta(ruta != null ? ruta.toString() : null);
                    accion.setTipo(tipo != null ? tipo.toString() : null);
                    resp.setAccion(accion);
                }
            } else {
                // n8n no respondió válido: degradar a la base de conocimiento local.
                resp = agenteKb.responderInteligente(input, rolId);
            }
        } catch (Exception e) {
            resp = agenteKb.responderInteligente(input, rolId);
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
