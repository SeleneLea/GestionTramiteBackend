package com.example.demo.services;

import com.example.demo.dto.AuditoriaItemResponse;
import com.example.demo.models.AuditoriaDocumento;
import com.example.demo.repositories.AuditoriaDocumentoRepository;
import com.example.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuditoriaDocumentoService {

    public static final String LECTURA          = "LECTURA";
    public static final String DESCARGA         = "DESCARGA";
    public static final String SUBIDA           = "SUBIDA";
    public static final String NUEVA_VERSION    = "NUEVA_VERSION";
    public static final String EDICION_EN_VIVO  = "EDICION_EN_VIVO";
    public static final String EDICION_GUARDADA = "EDICION_GUARDADA";
    public static final String BLOQUEO          = "BLOQUEO";
    public static final String DESBLOQUEO       = "DESBLOQUEO";
    public static final String BORRADO          = "BORRADO";

    @Autowired private AuditoriaDocumentoRepository repo;
    @Autowired private UsuarioRepository usuarioRepository;

    public AuditoriaDocumento registrar(String documentoArchivoId,
                                        String versionId,
                                        String usuarioId,
                                        String rol,
                                        String accion,
                                        String ip,
                                        String userAgent,
                                        Map<String, Object> detalle) {
        AuditoriaDocumento a = new AuditoriaDocumento();
        a.setDocumentoArchivoId(documentoArchivoId);
        a.setVersionId(versionId);
        a.setUsuarioId(usuarioId);
        a.setRol(rol);
        a.setAccion(accion);
        a.setIp(ip);
        a.setUserAgent(userAgent);
        a.setDetalle(detalle);
        a.setTimestamp(LocalDateTime.now());

        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(u -> {
                String nombre = (u.getNombre() != null ? u.getNombre() : "")
                        + (u.getApellido() != null ? " " + u.getApellido() : "");
                a.setUsuarioNombre(nombre.trim());
            });
        }

        return repo.save(a);
    }

    public Page<AuditoriaItemResponse> listarPorDocumento(String documentoArchivoId,
                                                           int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return repo.findByDocumentoArchivoIdOrderByTimestampDesc(documentoArchivoId, pageable)
                .map(this::toDto);
    }

    public Page<AuditoriaItemResponse> listarPorDocumentoEntreFechas(String documentoArchivoId,
                                                                      LocalDateTime desde,
                                                                      LocalDateTime hasta,
                                                                      int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return repo.buscarPorDocumentoEntreFechas(documentoArchivoId, desde, hasta, pageable)
                .map(this::toDto);
    }

    private AuditoriaItemResponse toDto(AuditoriaDocumento a) {
        return new AuditoriaItemResponse(
                a.getId(),
                a.getDocumentoArchivoId(),
                a.getVersionId(),
                a.getUsuarioId(),
                a.getUsuarioNombre(),
                a.getRol(),
                a.getAccion(),
                a.getIp(),
                a.getUserAgent(),
                a.getTimestamp(),
                a.getDetalle()
        );
    }
}
