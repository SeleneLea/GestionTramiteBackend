// ============================================
// Script de inicialización — Sistema de Trámites
// Se ejecuta al crear el contenedor MongoDB por primera vez
// ============================================

db = db.getSiblingDB('tramites_db');

// ------------ PERMISOS ------------
db.permisos.insertMany([
    { codigo: "CREAR_FLUJO", modulo: "flujos", descripcion: "Crear y editar flujos de trabajo" },
    { codigo: "GESTIONAR_USUARIOS", modulo: "usuarios", descripcion: "Administrar usuarios del sistema" },
    { codigo: "VER_REPORTES", modulo: "reportes", descripcion: "Ver dashboards y reportes" },
    { codigo: "EJECUTAR_TRAMITE", modulo: "tramites", descripcion: "Ejecutar actividades asignadas" },
    { codigo: "VER_HISTORIAL_DEPARTAMENTO", modulo: "tramites", descripcion: "Ver historial del departamento" },
    { codigo: "SOLICITAR_TRAMITE", modulo: "tramites", descripcion: "Iniciar nuevos trámites" },
    { codigo: "CONSULTAR_MIS_TRAMITES", modulo: "tramites", descripcion: "Consultar estado de trámites propios" },
    { codigo: "APROBAR_TRAMITE", modulo: "tramites", descripcion: "Aprobar o rechazar trámites" },
    { codigo: "CONFIGURAR_SISTEMA", modulo: "configuracion", descripcion: "Configurar parámetros del sistema" }
]);

// ------------ ROLES ------------
db.roles.insertMany([
    {
        nombre: "SuperUser",
        descripcion: "Acceso total al sistema",
        permisos: ["*"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Administrador",
        descripcion: "Crea flujos y gestiona configuración",
        permisos: ["CREAR_FLUJO", "GESTIONAR_USUARIOS", "VER_REPORTES", "CONFIGURAR_SISTEMA"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Funcionario",
        descripcion: "Ejecuta trámites asignados",
        permisos: ["EJECUTAR_TRAMITE", "VER_HISTORIAL_DEPARTAMENTO", "APROBAR_TRAMITE"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Cliente",
        descripcion: "Solicita y consulta sus trámites",
        permisos: ["SOLICITAR_TRAMITE", "CONSULTAR_MIS_TRAMITES"],
        esSistema: true,
        fechaCreacion: new Date()
    }
]);

// ------------ DEPARTAMENTOS (ejemplo CRE) ------------
db.departamentos.insertMany([
    { nombre: "Atención al Cliente", codigo: "ATC", descripcion: "Recepción de solicitudes", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Técnica", codigo: "TEC", descripcion: "Inspección y presupuesto", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Legal", codigo: "LEG", descripcion: "Revisión de contratos", activo: true, fechaCreacion: new Date() },
    { nombre: "Operaciones", codigo: "OPE", descripcion: "Ejecución y cierre", activo: true, fechaCreacion: new Date() }
]);

// ------------ CANALES DE ENVÍO ------------
db.canales_envio.insertMany([
    { tipo: "push_flutter", configuracion: { proveedor: "FCM" }, activo: true },
    { tipo: "web_internal", configuracion: { websocket: true }, activo: true },
    { tipo: "email", configuracion: { smtp: "configurar" }, activo: false }
]);

// ------------ USUARIO ADMIN INICIAL (contraseña: admin12345) ----
var rolAdmin = db.roles.findOne({ nombre: "Administrador" });
db.usuarios.insertOne({
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
});

print("=== Datos iniciales insertados correctamente ===");
print("Roles creados: " + db.roles.countDocuments());
print("Permisos creados: " + db.permisos.countDocuments());
print("Departamentos creados: " + db.departamentos.countDocuments());
print("Canales de envío creados: " + db.canales_envio.countDocuments());
print("Usuarios creados: " + db.usuarios.countDocuments());
