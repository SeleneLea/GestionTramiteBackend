// Limpiar toda la BD y recrear con ObjectIds CORRECTOS
db.usuarios.deleteMany({});
db.roles.deleteMany({});
db.departamentos.deleteMany({});
db.actividades.deleteMany({});
db.permisos.deleteMany({});
db.politicas_negocio.deleteMany({});
db.canales_envio.deleteMany({});

// Los _ids se crean automáticamente como ObjectId si no los especificamos
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

// Departamentos con ObjectIds
db.departamentos.insertMany([
    { nombre: "Atención al Cliente", codigo: "ATC", descripcion: "Recepción", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Técnica", codigo: "TEC", descripcion: "Inspección", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Legal", codigo: "LEG", descripcion: "Revisión legal", activo: true, fechaCreacion: new Date() },
    { nombre: "Operaciones", codigo: "OPE", descripcion: "Ejecución", activo: true, fechaCreacion: new Date() }
]);

// Actividades vinculadas a departamentos por su ID
var dptATC = db.departamentos.findOne({codigo: "ATC"})._id;
var dptTEC = db.departamentos.findOne({codigo: "TEC"})._id;
var dptLEG = db.departamentos.findOne({codigo: "LEG"})._id;
var dptOPE = db.departamentos.findOne({codigo: "OPE"})._id;

db.actividades.insertMany([
    { nombre: "Recibir solicitud", descripcion: "Recepción inicial", departamentoId: dptATC, slaHoras: 24, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Inspección técnica", descripcion: "Revisar factibilidad", departamentoId: dptTEC, slaHoras: 72, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Elaborar presupuesto", descripcion: "Crear presupuesto", departamentoId: dptTEC, slaHoras: 48, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Revisar legalidad", descripcion: "Validar documentos", departamentoId: dptLEG, slaHoras: 24, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Aprobar o rechazar", descripcion: "Decisión final", departamentoId: dptLEG, slaHoras: 12, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Ejecutar conexión", descripcion: "Instalación física", departamentoId: dptOPE, slaHoras: 120, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() }
]);

// Política de negocio
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

print("=== RECARGA COMPLETADA ===");
print("Roles: " + db.roles.countDocuments());
print("Departamentos: " + db.departamentos.countDocuments());
print("Actividades: " + db.actividades.countDocuments());
print("Políticas: " + db.politicas_negocio.countDocuments());
