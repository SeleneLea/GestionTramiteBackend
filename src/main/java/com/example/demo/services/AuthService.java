package com.example.demo.services;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.CrearUsuarioAdminRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterClienteRequest;
import com.example.demo.models.Rol;
import com.example.demo.models.Usuario;
import com.example.demo.repositories.RolRepository;
import com.example.demo.repositories.UsuarioRepository;
import com.example.demo.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtils jwtUtils;

    public AuthResponse registrarCliente(RegisterClienteRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        Rol rol = rolRepository.findByNombre("Cliente")
                .orElseThrow(() -> new IllegalStateException("Rol 'Cliente' no encontrado"));

        Usuario u = new Usuario();
        u.setNombre(req.getNombre());
        u.setApellido(req.getApellido());
        u.setEmail(req.getEmail());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRolId(rol.getId());
        u.setTipo("cliente");
        u.setTelefono(req.getTelefono());
        u.setActivo(true);
        u.setFechaRegistro(LocalDateTime.now());

        return construirAuthResponse(usuarioRepository.save(u), rol);
    }

    public Usuario crearUsuarioPorAdmin(CrearUsuarioAdminRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        String nombreRol = capitalizar(req.getTipo());
        Rol rol = rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new IllegalStateException("Rol no encontrado: " + nombreRol));

        Usuario u = new Usuario();
        u.setNombre(req.getNombre());
        u.setApellido(req.getApellido());
        u.setEmail(req.getEmail());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRolId(rol.getId());
        u.setDepartamentosIds(req.getDepartamentosIds());
        u.setTipo(req.getTipo());
        u.setTelefono(req.getTelefono());
        u.setActivo(true);
        u.setFechaRegistro(LocalDateTime.now());

        return usuarioRepository.save(u);
    }

    public AuthResponse login(LoginRequest req) {
        Usuario u = usuarioRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!u.isActivo()) {
            throw new IllegalArgumentException("Usuario inactivo. Contacte al administrador");
        }

        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        Rol rol = rolRepository.findById(u.getRolId())
                .orElseThrow(() -> new IllegalStateException("Rol del usuario no existe"));

        u.setUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(u);

        return construirAuthResponse(u, rol);
    }

    private AuthResponse construirAuthResponse(Usuario u, Rol rol) {
        String token = jwtUtils.generarToken(u.getId(), u.getEmail(), rol.getNombre());
        return new AuthResponse(token, "Bearer", u.getEmail(),
                u.getNombre() + " " + u.getApellido(), rol.getNombre(), u.getId());
    }

    private String capitalizar(String tipo) {
        if (tipo == null || tipo.isEmpty()) return tipo;
        return Character.toUpperCase(tipo.charAt(0)) + tipo.substring(1).toLowerCase();
    }
}
