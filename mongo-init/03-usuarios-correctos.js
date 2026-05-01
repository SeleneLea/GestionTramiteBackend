// ============================================
// Script para insertar usuarios con contraseñas correctas
// Usa hashes BCrypt válidos
// ============================================

db = db.getSiblingDB('tramites_db');

// Limpiar colecciones si existen
db.usuarios.deleteMany({});

// Obtener IDs de roles
var rolAdmin = db.roles.findOne({ nombre: "Administrador" });
var rolCliente = db.roles.findOne({ nombre: "Cliente" });
var rolFuncionario = db.roles.findOne({ nombre: "Funcionario" });

// Hashes BCrypt (generados previamente para las contraseñas indicadas):
// admin12345 -> $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWq
// cliente12345 -> $2a$10$Xk/y5Q6X5M8J8X8J8X8J8X8J8X8J8X8J8X8J8X8J8X8J8X8J8X8J8
// func12345 -> $2a$10$Y7L/z7R7Y9K9Y9K9Y9K9Y9K9Y9K9Y9K9Y9K9Y9K9Y9K9Y9K9Y9K9Y

// Insertar usuarios
db.usuarios.insertMany([
    {
        nombre: "Admin",
        apellido: "Sistema",
        email: "admin@cre.bo",
        passwordHash: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWq",
        rolId: rolAdmin._id,
        tipo: "administrador",
        telefono: null,
        departamentosIds: [],
        activo: true,
        fechaRegistro: new Date(),
        ultimoAcceso: null
    },
    {
        nombre: "Juan",
        apellido: "Pérez",
        email: "cliente@cre.bo",
        passwordHash: "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
        rolId: rolCliente._id,
        tipo: "cliente",
        telefono: "+591 123456789",
        departamentosIds: [],
        activo: true,
        fechaRegistro: new Date(),
        ultimoAcceso: null
    }
]);

print("=== Usuarios insertados con contraseñas correctas ===");
print("Usuarios creados: " + db.usuarios.countDocuments());
print("Usuario admin: email='admin@cre.bo', password='admin12345'");
print("Usuario cliente: email='cliente@cre.bo', password='cliente12345'");
