// Limpiar TODOS los documentos
db.usuarios.deleteMany({});
db.roles.deleteMany({});
db.departamentos.deleteMany({});

// Recrear roles con ObjectId apropiado
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

// Recrear departamentos
db.departamentos.insertMany([
    { nombre: "Atención al Cliente", codigo: "ATC", descripcion: "Recepción", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Técnica", codigo: "TEC", descripcion: "Inspección", activo: true, fechaCreacion: new Date() },
    { nombre: "Área Legal", codigo: "LEG", descripcion: "Revisión legal", activo: true, fechaCreacion: new Date() },
    { nombre: "Operaciones", codigo: "OPE", descripcion: "Ejecución", activo: true, fechaCreacion: new Date() }
]);

print("Roles creados: " + db.roles.countDocuments());
print("Departamentos creados: " + db.departamentos.countDocuments());
