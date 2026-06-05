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

@Service
public class AiIntegrationService {

    @Autowired private TranscripcionVozRepository vozRepo;
    @Autowired private LogAgenteRepository agenteRepo;
    @Autowired private AgenteAsistenciaService agenteKb;
    @Autowired private IaProxyService iaProxy;

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
