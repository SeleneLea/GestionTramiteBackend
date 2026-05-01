// Limpiar TODOS los datos y recrear desde cero
db = db.getSiblingDB('tramites_db');

// Limpiar TODAS las colecciones
db.usuarios.deleteMany({});
db.roles.deleteMany({});
db.departamentos.deleteMany({});
db.permisos.deleteMany({});
db.actividades.deleteMany({});
db.politicas_negocio.deleteMany({});
db.canales_envio.deleteMany({});

// ============ PERMISOS ============
db.permisos.insertMany([
    { codigo: "CREAR_FLUJO", modulo: "flujos", descripcion: "Crear y editar flujos" },
    { codigo: "GESTIONAR_USUARIOS", modulo: "usuarios", descripcion: "Administrar usuarios" },
    { codigo: "VER_REPORTES", modulo: "reportes", descripcion: "Ver reportes" },
    { codigo: "EJECUTAR_TRAMITE", modulo: "tramites", descripcion: "Ejecutar actividades" },
    { codigo: "VER_HISTORIAL_DEPARTAMENTO", modulo: "tramites", descripcion: "Ver historial" },
    { codigo: "SOLICITAR_TRAMITE", modulo: "tramites", descripcion: "Solicitar tramites" },
    { codigo: "CONSULTAR_MIS_TRAMITES", modulo: "tramites", descripcion: "Consultar mis tramites" },
    { codigo: "APROBAR_TRAMITE", modulo: "tramites", descripcion: "Aprobar tramites" },
    { codigo: "CONFIGURAR_SISTEMA", modulo: "configuracion", descripcion: "Configurar sistema" }
]);

// ============ ROLES ============
db.roles.insertMany([
    {
        nombre: "SuperUser",
        descripcion: "Acceso total",
        permisos: ["*"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Administrador",
        descripcion: "Crea flujos y gestiona",
        permisos: ["CREAR_FLUJO", "GESTIONAR_USUARIOS", "VER_REPORTES", "CONFIGURAR_SISTEMA"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Funcionario",
        descripcion: "Ejecuta tramites",
        permisos: ["EJECUTAR_TRAMITE", "VER_HISTORIAL_DEPARTAMENTO", "APROBAR_TRAMITE"],
        esSistema: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Cliente",
        descripcion: "Solicita y consulta",
        permisos: ["SOLICITAR_TRAMITE", "CONSULTAR_MIS_TRAMITES"],
        esSistema: true,
        fechaCreacion: new Date()
    }
]);

// ============ DEPARTAMENTOS ============
db.departamentos.insertMany([
    { nombre: "Atención al Cliente", codigo: "ATC", descripcion: "Recepción", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Técnica", codigo: "TEC", descripcion: "Inspección", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Legal", codigo: "LEG", descripcion: "Revisión legal", activo: true, fechaCreacion: new Date() },
    { nombre: "Operaciones", codigo: "OPE", descripcion: "Ejecución", activo: true, fechaCreacion: new Date() }
]);

// ============ CANALES DE ENVIO ============
db.canales_envio.insertMany([
    { tipo: "push_flutter", configuracion: { proveedor: "FCM" }, activo: true },
    { tipo: "web_internal", configuracion: { websocket: true }, activo: true },
    { tipo: "email", configuracion: { smtp: "configurar" }, activo: false }
]);

// ============ ACTIVIDADES ============
var dptosATC = db.departamentos.findOne({ codigo: "ATC" });
var dptosTEC = db.departamentos.findOne({ codigo: "TEC" });
var dptosLEG = db.departamentos.findOne({ codigo: "LEG" });
var dptosOPE = db.departamentos.findOne({ codigo: "OPE" });

db.actividades.insertMany([
    { nombre: "Recibir solicitud", descripcion: "Recepción inicial", departamentoId: dptosATC._id, slaHoras: 24, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Inspección técnica", descripcion: "Revisar factibilidad", departamentoId: dptosTEC._id, slaHoras: 72, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Elaborar presupuesto", descripcion: "Crear presupuesto", departamentoId: dptosTEC._id, slaHoras: 48, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Revisar legalidad", descripcion: "Validar documentos", departamentoId: dptosLEG._id, slaHoras: 24, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Aprobar o rechazar", descripcion: "Decisión final", departamentoId: dptosLEG._id, slaHoras: 12, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Ejecutar conexión", descripcion: "Instalación física", departamentoId: dptosOPE._id, slaHoras: 120, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() }
]);

// ============ POLITICAS ============
db.politicas_negocio.insertOne({
    nombre: "Nueva conexión residencial",
    descripcion: "Política para nuevas conexiones",
    version: "1.0",
    estado: "activa",
    slaMaximoDias: 15,
    diagramaId: null,
    activo: true,
    fechaCreacion: new Date(),
    fechaActivacion: new Date()
});

print("=== BASE DE DATOS RECREADACORRECTAMENTE ===");
print("Permisos: " + db.permisos.countDocuments());
print("Roles: " + db.roles.countDocuments());
print("Departamentos: " + db.departamentos.countDocuments());
print("Canales: " + db.canales_envio.countDocuments());
print("Actividades: " + db.actividades.countDocuments());
print("Políticas: " + db.politicas_negocio.countDocuments());
