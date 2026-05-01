package com.example.demo.services;

import com.example.demo.dto.RolRequest;
import com.example.demo.models.Rol;
import com.example.demo.repositories.PermisoRepository;
import com.example.demo.repositories.RolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RolService {

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private PermisoRepository permisoRepository;

    public List<Rol> listarTodos() {
        return rolRepository.findAll();
    }

    public Optional<Rol> buscarPorId(String id) {
        return rolRepository.findById(id);
    }

    public Optional<Rol> buscarPorNombre(String nombre) {
        return rolRepository.findByNombre(nombre);
    }

    public Rol crear(RolRequest req) {
        if (rolRepository.findByNombre(req.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un rol con el nombre: " + req.getNombre());
        }
        validarPermisos(req.getPermisos());

        Rol r = new Rol();
        r.setNombre(req.getNombre());
        r.setDescripcion(req.getDescripcion());
        r.setPermisos(req.getPermisos());
        r.setEsSistema(false);
        r.setFechaCreacion(LocalDateTime.now());
        return rolRepository.save(r);
    }

    public Rol actualizar(String id, RolRequest req) {
        Rol r = rolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));

        if (r.isEsSistema() && !r.getNombre().equals(req.getNombre())) {
            throw new IllegalArgumentException("No se puede renombrar un rol del sistema");
        }
        validarPermisos(req.getPermisos());

        r.setDescripcion(req.getDescripcion());
        r.setPermisos(req.getPermisos());
        return rolRepository.save(r);
    }

    public Rol asignarPermisos(String rolId, List<String> permisos) {
        validarPermisos(permisos);
        Rol r = rolRepository.findById(rolId)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        r.setPermisos(permisos);
        return rolRepository.save(r);
    }

    public void eliminar(String id) {
        Rol r = rolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        if (r.isEsSistema()) {
            throw new IllegalArgumentException(
                    "No se puede eliminar un rol del sistema (Cliente, Funcionario, Administrador, SuperUser)");
        }
        rolRepository.deleteById(id);
    }

    private void validarPermisos(List<String> permisos) {
        if (permisos == null) return;
        for (String codigo : permisos) {
            if ("*".equals(codigo)) continue;  // wildcard del SuperUser
            permisoRepository.findByCodigo(codigo)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Permiso no existe: " + codigo));
        }
    }
}
