package com.example.demo.services;

import com.example.demo.dto.ActualizarPerfilRequest;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.RolRepository;
import com.example.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private S3StorageService s3;

    public record FotoData(byte[] bytes, String contentType) {}

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public List<Usuario> listarActivos() {
        return usuarioRepository.findByActivoTrue();
    }

    public List<Usuario> listarPorTipo(String tipo) {
        return usuarioRepository.findByTipo(tipo);
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario actualizar(String id, Usuario datos) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        existente.setNombre(datos.getNombre());
        existente.setApellido(datos.getApellido());
        existente.setTelefono(datos.getTelefono());
        existente.setDepartamentosIds(datos.getDepartamentosIds());
        existente.setActivo(datos.isActivo());

        if (datos.getTipo() != null && !datos.getTipo().isBlank()
                && !datos.getTipo().equals(existente.getTipo())) {
            existente.setTipo(datos.getTipo());
            String nombreRol = capitalizar(datos.getTipo());
            rolRepository.findByNombre(nombreRol).ifPresent(r -> existente.setRolId(r.getId()));
        }

        return usuarioRepository.save(existente);
    }

    public Usuario actualizarPerfilPropio(String id, ActualizarPerfilRequest req) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        u.setNombre(req.getNombre());
        u.setApellido(req.getApellido());
        u.setTelefono(req.getTelefono());
        u.setDni(req.getDni());
        u.setDireccion(req.getDireccion());

        return usuarioRepository.save(u);
    }

    public void cambiarPassword(String id, String actual, String nueva) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (actual == null || !passwordEncoder.matches(actual, u.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres.");
        }
        u.setPasswordHash(passwordEncoder.encode(nueva));
        usuarioRepository.save(u);
    }

    public void subirFotoPerfil(String id, MultipartFile file) throws IOException {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (!s3.enabled()) {
            throw new IllegalStateException("El almacenamiento de archivos no está disponible.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "foto";
        String limpio = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "perfiles/" + id + "/" + UUID.randomUUID() + "-" + limpio;
        if (u.getFotoPerfil() != null && !u.getFotoPerfil().isBlank()) {
            try {
                if (s3.exists(u.getFotoPerfil())) s3.delete(u.getFotoPerfil());
            } catch (Exception ignore) {
            }
        }
        s3.upload(key, file.getInputStream(), file.getContentType(), file.getSize());
        u.setFotoPerfil(key);
        usuarioRepository.save(u);
    }

    public FotoData descargarFoto(String id) {
        Usuario u = usuarioRepository.findById(id).orElse(null);
        if (u == null || u.getFotoPerfil() == null || u.getFotoPerfil().isBlank() || !s3.enabled()) {
            return null;
        }
        try {
            return new FotoData(s3.download(u.getFotoPerfil()), s3.contentType(u.getFotoPerfil()));
        } catch (Exception e) {
            return null;
        }
    }

    private String capitalizar(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    public void desactivar(String id) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        u.setActivo(false);
        usuarioRepository.save(u);
    }
}
