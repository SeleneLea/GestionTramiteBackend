// Limpiar y recrear datos correctamente
db = db.getSiblingDB('tramites_db');

// Limpiar colecciones
db.usuarios.deleteMany({});
db.actividades.deleteMany({});
db.politicas_negocio.deleteMany({});

// Obtener IDs de roles y departamentos
var rolAdmin = db.roles.findOne({ nombre: "Administrador" });
var rolCliente = db.roles.findOne({ nombre: "Cliente" });
var dptosATC = db.departamentos.findOne({ codigo: "ATC" });
var dptosTEC = db.departamentos.findOne({ codigo: "TEC" });
var dptosLEG = db.departamentos.findOne({ codigo: "LEG" });
var dptosOPE = db.departamentos.findOne({ codigo: "OPE" });

// Insertar actividades CRE
db.actividades.insertMany([
    { nombre: "Recibir solicitud", descripcion: "Recepción inicial", departamentoId: dptosATC._id, slaHoras: 24, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Inspección técnica", descripcion: "Revisar factibilidad", departamentoId: dptosTEC._id, slaHoras: 72, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Elaborar presupuesto", descripcion: "Crear presupuesto", departamentoId: dptosTEC._id, slaHoras: 48, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Revisar legalidad", descripcion: "Validar documentos", departamentoId: dptosLEG._id, slaHoras: 24, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Aprobar o rechazar", descripcion: "Decisión final", departamentoId: dptosLEG._id, slaHoras: 12, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() },
    { nombre: "Ejecutar conexión", descripcion: "Instalación física", departamentoId: dptosOPE._id, slaHoras: 120, slaMinutos: 0, reutilizable: false, activo: true, fechaCreacion: new Date() }
]);

// Insertar política de negocio
db.politicas_negocio.insertOne({
    nombre: "Nueva conexión residencial",
    descripcion: "Política para solicitudes de nueva conexión",
    version: "1.0",
    estado: "activa",
    slaMaximoDias: 15,
    diagramaId: null,
    activo: true,
    fechaCreacion: new Date(),
    fechaActivacion: new Date()
});

print("Datos recargados correctamente");
print("Actividades: " + db.actividades.countDocuments());
print("Políticas: " + db.politicas_negocio.countDocuments());
