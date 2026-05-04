// ============================================
// Script para insertar usuarios con contraseñas correctas
// Usa hashes BCrypt válidos
// ============================================

db = db.getSiblingDB('tramites_db');

// B7: este script se neutralizó. A pesar del nombre "usuarios-correctos",
// los hashes BCrypt que insertaba eran INVÁLIDOS (el de admin no correspondía a
// 'admin12345' y el de cliente '$2a$10$abcdef...' es un salt corrupto que bcrypt
// rechaza), por lo que rompía el login con las credenciales documentadas.
// La fuente de verdad de usuarios es UsuarioSeeder.java (passwordEncoder.encode).
// NO se borra ni se inserta nada aquí.

print("=== 03-usuarios-correctos.js: no-op (usuarios los crea UsuarioSeeder.java) ===");
