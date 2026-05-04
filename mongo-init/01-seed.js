// ============================================
// Script de inicialización — Sistema de Trámites
// Se ejecuta al crear el contenedor MongoDB por primera vez
// ============================================

db = db.getSiblingDB('tramites_db');

// ------------ PERMISOS ------------
// SEED-MONGOINIT-02: NO se siembran permisos aquí. Los códigos que vivían en este
// script (CREAR_FLUJO, EJECUTAR_TRAMITE, APROBAR_TRAMITE, etc.) divergían de los
// definidos en PermisoSeeder.java (INICIAR_TRAMITE, COMPLETAR_NODO, VER_TODOS_TRAMITES, ...).
// La fuente de verdad de permisos es PermisoSeeder.java (no-op aquí).

// ------------ ROLES ------------
// SEED-MONGOINIT-02: NO se siembran roles aquí. Bajo reset=false, los permisos
// sembrados por este script dejaban al rol Funcionario con permisos equivocados
// (EJECUTAR_TRAMITE/VER_HISTORIAL_DEPARTAMENTO/APROBAR_TRAMITE en lugar de
// CONSULTAR_MIS_TRAMITES/COMPLETAR_NODO/VER_TODOS_TRAMITES). La fuente de verdad
// de roles es RolSeeder.java (no-op aquí).

// ------------ DEPARTAMENTOS (ejemplo CRE) ------------
// SEED-MONGOINIT-02: NO se siembran departamentos aquí. La fuente de verdad de
// departamentos es DepartamentoSeeder.java (no-op aquí).

// ------------ CANALES DE ENVÍO ------------
// SEED-MONGOINIT-02: NO se siembran canales de envío aquí. Los tipos que vivían en
// este script (push_flutter/web_internal/email) divergían de los de CanalEnvioSeeder.java
// (EMAIL/SMS/PUSH/IN_APP). La fuente de verdad de canales es CanalEnvioSeeder.java (no-op aquí).

// ------------ USUARIOS ------------
// B7: NO se siembran usuarios aquí. El hash BCrypt que vivía en este script no
// correspondía a 'admin12345' y rompía el login. La fuente de verdad de usuarios
// es UsuarioSeeder.java (usa passwordEncoder.encode(...), hashes válidos garantizados).

print("=== mongo-init: sin siembra de datos de dominio ===");
print("Permisos: gestionados por PermisoSeeder.java (no por mongo-init)");
print("Roles: gestionados por RolSeeder.java (no por mongo-init)");
print("Departamentos: gestionados por DepartamentoSeeder.java (no por mongo-init)");
print("Canales de envío: gestionados por CanalEnvioSeeder.java (no por mongo-init)");
print("Usuarios: gestionados por UsuarioSeeder.java (no por mongo-init)");
