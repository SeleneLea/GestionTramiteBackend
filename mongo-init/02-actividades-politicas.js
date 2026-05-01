// ============================================
// Script para insertar Actividades y Políticas
// Se ejecuta después del seed inicial
// ============================================

db = db.getSiblingDB('tramites_db');

// ------------ ACTIVIDADES (CRE) ------------
var dptosATC = db.departamentos.findOne({ codigo: "ATC" });
var dptosTEC = db.departamentos.findOne({ codigo: "TEC" });
var dptosLEG = db.departamentos.findOne({ codigo: "LEG" });
var dptosOPE = db.departamentos.findOne({ codigo: "OPE" });

db.actividades.insertMany([
    {
        nombre: "Recibir solicitud",
        descripcion: "Recepción inicial de nueva conexión",
        departamentoId: dptosATC._id,
        slaHoras: 24,
        slaMinutos: 0,
        reutilizable: false,
        activo: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Inspección técnica",
        descripcion: "Revisar factibilidad técnica de la conexión",
        departamentoId: dptosTEC._id,
        slaHoras: 72,
        slaMinutos: 0,
        reutilizable: false,
        activo: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Elaborar presupuesto",
        descripcion: "Crear presupuesto de obras",
        departamentoId: dptosTEC._id,
        slaHoras: 48,
        slaMinutos: 0,
        reutilizable: false,
        activo: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Revisar legalidad",
        descripcion: "Validar documentos y contratos",
        departamentoId: dptosLEG._id,
        slaHoras: 24,
        slaMinutos: 0,
        reutilizable: false,
        activo: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Aprobar o rechazar",
        descripcion: "Decisión final sobre la solicitud",
        departamentoId: dptosLEG._id,
        slaHoras: 12,
        slaMinutos: 0,
        reutilizable: false,
        activo: true,
        fechaCreacion: new Date()
    },
    {
        nombre: "Ejecutar conexión",
        descripcion: "Instalación física de la conexión",
        departamentoId: dptosOPE._id,
        slaHoras: 120,
        slaMinutos: 0,
        reutilizable: false,
        activo: true,
        fechaCreacion: new Date()
    }
]);

// ------------ POLÍTICA DE NEGOCIO (Nueva conexión residencial) ----
db.politicas_negocio.insertOne({
    nombre: "Nueva conexión residencial",
    descripcion: "Política para solicitudes de nueva conexión de energía eléctrica en zona residencial",
    version: "1.0",
    estado: "activa",
    slaMaximoDias: 15,
    diagramaId: null,  // Se asignará cuando se cree el diagrama en G5
    activo: true,
    fechaCreacion: new Date(),
    fechaActivacion: new Date()
});

print("=== Actividades y Políticas insertadas correctamente ===");
print("Actividades creadas: " + db.actividades.countDocuments());
print("Políticas creadas: " + db.politicas_negocio.countDocuments());
