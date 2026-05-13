package com.example.demo.services;

import com.example.demo.dto.ActualizarPerfilRequest;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.RolRepository;
import com.example.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RolRepository rolRepository;

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

        // El rol SIEMPRE sigue al tipo (no se elige a mano): administrador→Administrador,
        // funcionario→Funcionario. Solo se recalcula si el tipo CAMBIÓ, para no pisar
        // roles especiales (p.ej. SuperUser) en ediciones que no tocan el tipo.
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

        // Solo datos personales editables por el propio usuario.
        // NO se toca email, tipo, rolId, password ni departamentos.
        u.setNombre(req.getNombre());
        u.setApellido(req.getApellido());
        u.setTelefono(req.getTelefono());
        u.setDni(req.getDni());
        u.setDireccion(req.getDireccion());

        return usuarioRepository.save(u);
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
