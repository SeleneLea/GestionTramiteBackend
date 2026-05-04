// ============================================
// Script para insertar Actividades y Políticas
// Se ejecuta después del seed inicial
// ============================================

db = db.getSiblingDB('tramites_db');

// ------------ ACTIVIDADES (CRE) ------------
// La siembra de actividades se eliminó de mongo-init (no-op):
// ActividadSeeder.java es ahora la única fuente de verdad y crea las 12 actividades
// con sus nombres canónicos, SLA, salidasPosibles y documentoIds.
// Si mongo-init insertaba aquí con nombres distintos, bajo reset=false ActividadSeeder
// se saltaba (count>0) y DiagramaSeeder no lograba resolver los actividadId por nombre.

// ------------ POLÍTICA DE NEGOCIO (Nueva conexion residencial) ----
// La siembra de esta política se eliminó de mongo-init para evitar duplicados:
// PoliticaSeeder.java es ahora la única fuente y la crea como 'Nueva conexion residencial' (sin tilde).
// db.politicas_negocio.insertOne({
//     nombre: "Nueva conexion residencial",
//     descripcion: "Política para solicitudes de nueva conexión de energía eléctrica en zona residencial",
//     version: "1.0",
//     estado: "activa",
//     slaMaximoDias: 15,
//     diagramaId: null,  // Se asignará cuando se cree el diagrama en G5
//     activo: true,
//     fechaCreacion: new Date(),
//     fechaActivacion: new Date()
// });

print("=== Actividades: no-op en mongo-init (las siembra ActividadSeeder.java) ===");
print("Actividades existentes: " + db.actividades.countDocuments());
print("Políticas creadas (las siembra PoliticaSeeder.java): " + db.politicas_negocio.countDocuments());
