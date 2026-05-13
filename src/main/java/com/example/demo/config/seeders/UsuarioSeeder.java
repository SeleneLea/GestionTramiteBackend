package com.example.demo.config.seeders;

import com.example.demo.models.Usuario;
import com.example.demo.repositories.DepartamentoRepository;
import com.example.demo.repositories.RolRepository;
import com.example.demo.repositories.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class UsuarioSeeder {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    public void seed() {
        String rolAdmin    = rolId("Administrador");
        String rolSuper    = rolId("SuperUser");
        String rolFuncio   = rolId("Funcionario");
        String rolCliente  = rolId("Cliente");

        String atcId = deptoId("ATC");
        String tecId = deptoId("TEC");
        String legId = deptoId("LEG");
        String opeId = deptoId("OPE");

        // --- Usuarios base del sistema ---
        crear("Admin",  "Sistema",   "admin@cre.bo",        "admin12345",
                "administrador", rolAdmin,   null);
        crear("Super",  "Usuario",   "superuser@cre.bo",    "super12345",
                "administrador", rolSuper,   null);
        // 2º admin: sirve para la demo de "Compartidos conmigo" (invita al admin principal)
        crear("Ana",    "Gestora",   "admin2@cre.bo",       "admin12345",
                "administrador", rolAdmin,   null);

        // --- Funcionarios por departamento ---
        crear("Carlos",  "Lima",      "funcionario@cre.bo",  "func12345",
                "funcionario", rolFuncio, tecId != null ? List.of(tecId) : null);
        crear("Maria",   "Rodriguez", "func_atc@cre.bo",     "func12345",
                "funcionario", rolFuncio, atcId != null ? List.of(atcId) : null);
        crear("Pedro",   "Gomez",     "func_leg@cre.bo",     "func12345",
                "funcionario", rolFuncio, legId != null ? List.of(legId) : null);
        crear("Ana",     "Torres",    "func_ope@cre.bo",     "func12345",
                "funcionario", rolFuncio, opeId != null ? List.of(opeId) : null);

        // --- Clientes ---
        crear("Juan",    "Perez",     "cliente@cre.bo",      "cliente12345",
                "cliente", rolCliente, null);
        crear("Luis",    "Mamani",    "cliente2@cre.bo",     "cliente12345",
                "cliente", rolCliente, null);
        crear("Rosa",    "Flores",    "cliente3@cre.bo",     "cliente12345",
                "cliente", rolCliente, null);

        log.info("[Seeder] Usuarios OK");
    }

    private void crear(String nombre, String apellido, String email, String password,
                       String tipo, String rolId, List<String> departamentosIds) {
        if (!usuarioRepository.existsByEmail(email)) {
            Usuario u = new Usuario();
            u.setNombre(nombre);
            u.setApellido(apellido);
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setTipo(tipo);
            u.setRolId(rolId);
            u.setDepartamentosIds(departamentosIds);
            u.setActivo(true);
            u.setFechaRegistro(LocalDateTime.now());
            usuarioRepository.save(u);
        }
    }

    private String rolId(String nombre) {
        return rolRepository.findByNombre(nombre).map(r -> r.getId()).orElse(null);
    }

    private String deptoId(String codigo) {
        return departamentoRepository.findByCodigo(codigo).map(d -> d.getId()).orElse(null);
    }
}
