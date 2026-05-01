erDiagram
    %% ============================================
    %% SISTEMA DE GESTIÓN DE TRÁMITES - MongoDB
    %% Base de datos con colecciones principales
    %% ============================================
 
    USUARIO ||--o{ TRAMITE : "solicita (cliente)"
    USUARIO ||--o{ TRAMITE : "participa (funcionario)"
    USUARIO ||--o{ POLITICA_NEGOCIO : "crea (admin)"
    USUARIO ||--o{ DIAGRAMA_WORKFLOW : "diseña (admin)"
    USUARIO ||--o{ COLABORACION_DIAGRAMA : "invita"
    USUARIO ||--o{ COLABORACION_DIAGRAMA : "es_invitado"
    USUARIO }o--|| ROL : "tiene"
    USUARIO }o--o{ DEPARTAMENTO : "pertenece_a"
    USUARIO ||--o{ NOTIFICACION : "recibe"
    USUARIO ||--o{ LOG_AGENTE : "consulta"
    USUARIO ||--o{ TRAZABILIDAD : "ejecuta_accion"
 
    ROL ||--o{ PERMISO : "tiene"
 
    DEPARTAMENTO ||--o{ ACTIVIDAD : "contiene"
    DEPARTAMENTO ||--o{ SECCION_EXPEDIENTE : "responsable_de"
 
    ACTIVIDAD }o--o{ NODO_DIAGRAMA : "se_usa_en"
 
    POLITICA_NEGOCIO ||--|| DIAGRAMA_WORKFLOW : "tiene"
    POLITICA_NEGOCIO ||--o{ TRAMITE : "define"
    POLITICA_NEGOCIO ||--o{ VERSION_POLITICA : "versiona"
 
    DIAGRAMA_WORKFLOW ||--o{ NODO_DIAGRAMA : "contiene"
    DIAGRAMA_WORKFLOW ||--o{ FLUJO_TRANSICION : "conecta"
    DIAGRAMA_WORKFLOW ||--o{ COLABORACION_DIAGRAMA : "es_colaborado_en"
    DIAGRAMA_WORKFLOW ||--o{ VERSION_DIAGRAMA : "versiona"
 
    NODO_DIAGRAMA ||--|| FORMULARIO_PLANTILLA : "define"
    NODO_DIAGRAMA ||--o{ FLUJO_TRANSICION : "origen"
    NODO_DIAGRAMA ||--o{ FLUJO_TRANSICION : "destino"
 
    FORMULARIO_PLANTILLA ||--o{ CAMPO_PLANTILLA : "tiene"
 
    TRAMITE ||--|| EXPEDIENTE_DIGITAL : "tiene"
    TRAMITE ||--o{ ESTADO_HISTORICO : "pasa_por"
    TRAMITE ||--o{ TRAZABILIDAD : "registra"
    TRAMITE ||--o{ NOTIFICACION : "genera"
    TRAMITE }o--|| ESTADO_ACTUAL : "en"
 
    EXPEDIENTE_DIGITAL ||--o{ SECCION_EXPEDIENTE : "compuesto_por"
 
    SECCION_EXPEDIENTE ||--o{ CAMPO_SECCION : "contiene"
    SECCION_EXPEDIENTE ||--o{ ADJUNTO : "tiene"
    SECCION_EXPEDIENTE ||--o{ TRANSCRIPCION_VOZ : "puede_tener"
 
    NOTIFICACION }o--|| CANAL_ENVIO : "usa"
 
    METRICA_TIEMPO }o--|| TRAMITE : "mide"
    METRICA_TIEMPO }o--|| ACTIVIDAD : "sobre"
 
    CUELLO_BOTELLA }o--|| ACTIVIDAD : "identifica"
    CUELLO_BOTELLA }o--|| DEPARTAMENTO : "afecta"
 
    REPORTE }o--|| USUARIO : "generado_por"
 
    USUARIO {
        ObjectId _id PK
        string nombre
        string apellido
        string email UK
        string password_hash
        ObjectId rol_id FK
        array departamentos_ids FK
        string tipo "cliente | funcionario | administrador"
        string telefono
        boolean activo
        datetime fecha_registro
        datetime ultimo_acceso
    }
 
    ROL {
        ObjectId _id PK
        string nombre "Cliente | Funcionario | Administrador | SuperUser"
        string descripcion
        array permisos_ids FK
        boolean es_sistema
        datetime fecha_creacion
    }
 
    PERMISO {
        ObjectId _id PK
        string codigo UK "CREAR_FLUJO, APROBAR_TRAMITE, etc."
        string modulo "trámites | flujos | usuarios | reportes"
        string descripcion
    }
 
    DEPARTAMENTO {
        ObjectId _id PK
        string nombre UK
        string codigo UK
        string descripcion
        ObjectId jefe_id FK
        array actividades_ids FK
        boolean activo
        datetime fecha_creacion
    }
 
    ACTIVIDAD {
        ObjectId _id PK
        string nombre
        string descripcion
        ObjectId departamento_id FK
        int sla_horas "Tiempo límite en horas"
        string tipo_salida "aprobar | rechazar | derivar | observar"
        array campos_requeridos
        boolean reutilizable
        datetime fecha_creacion
    }
 
    POLITICA_NEGOCIO {
        ObjectId _id PK
        string nombre UK
        string descripcion
        string categoria
        ObjectId diagrama_id FK
        ObjectId creador_id FK
        int version_actual
        string estado "borrador | activa | archivada"
        object parametros "configuración adicional"
        datetime fecha_creacion
        datetime fecha_activacion
    }
 
    VERSION_POLITICA {
        ObjectId _id PK
        ObjectId politica_id FK
        int numero_version
        ObjectId diagrama_snapshot FK
        ObjectId creador_id FK
        string notas_cambio
        datetime fecha_version
    }
 
    DIAGRAMA_WORKFLOW {
        ObjectId _id PK
        string nombre
        ObjectId politica_id FK
        ObjectId creador_id FK
        array swimlanes "calles del diagrama"
        object canvas_data "posiciones, tamaños"
        int version_actual
        string estado "diseño | publicado"
        boolean generado_por_ia
        string prompt_original "si fue generado por IA"
        datetime fecha_creacion
        datetime ultima_modificacion
    }
 
    VERSION_DIAGRAMA {
        ObjectId _id PK
        ObjectId diagrama_id FK
        int numero_version
        object snapshot "estructura completa"
        ObjectId modificado_por FK
        string descripcion_cambio
        datetime fecha_version
    }
 
    NODO_DIAGRAMA {
        ObjectId _id PK
        ObjectId diagrama_id FK
        string tipo "inicio | actividad | decision | fork | join | fin"
        string nombre
        ObjectId actividad_id FK
        ObjectId departamento_id FK
        string swimlane "calle a la que pertenece"
        object posicion "coordenadas x, y"
        ObjectId formulario_plantilla_id FK
        int orden
    }
 
    FLUJO_TRANSICION {
        ObjectId _id PK
        ObjectId diagrama_id FK
        ObjectId nodo_origen_id FK
        ObjectId nodo_destino_id FK
        string tipo "secuencial | condicional | iterativo | paralelo"
        string condicion "expresión si es condicional"
        string etiqueta
    }
 
    COLABORACION_DIAGRAMA {
        ObjectId _id PK
        ObjectId diagrama_id FK
        ObjectId admin_invitador_id FK
        ObjectId invitado_id FK
        string rol_colaboracion "editor | visualizador"
        string estado "pendiente | aceptada | rechazada"
        datetime fecha_invitacion
        datetime fecha_respuesta
    }
 
    FORMULARIO_PLANTILLA {
        ObjectId _id PK
        ObjectId nodo_id FK
        string nombre
        array campos_plantilla_ids FK
        boolean permite_adjuntos
        boolean permite_dictado_voz
    }
 
    CAMPO_PLANTILLA {
        ObjectId _id PK
        ObjectId formulario_plantilla_id FK
        string nombre
        string etiqueta
        string tipo "texto | numero | fecha | seleccion | booleano | adjunto"
        boolean obligatorio
        array opciones "para campos de selección"
        string validacion_regex
        int orden
    }
 
    TRAMITE {
        ObjectId _id PK
        string codigo UK "TR-2025-00001"
        ObjectId cliente_id FK
        ObjectId politica_id FK
        ObjectId expediente_id FK
        string estado_actual "Nuevo | En proceso | Derivado | Observado | Rechazado | Aprobado"
        ObjectId nodo_actual_id FK
        ObjectId funcionario_actual_id FK
        datetime fecha_inicio
        datetime fecha_estimada_cierre
        datetime fecha_cierre_real
        int prioridad "1-5"
    }
 
    ESTADO_ACTUAL {
        ObjectId _id PK
        ObjectId tramite_id FK
        string estado
        ObjectId nodo_id FK
        datetime desde
    }
 
    ESTADO_HISTORICO {
        ObjectId _id PK
        ObjectId tramite_id FK
        string estado_anterior
        string estado_nuevo
        ObjectId nodo_anterior_id FK
        ObjectId nodo_nuevo_id FK
        ObjectId actor_id FK
        string motivo
        datetime fecha_cambio
    }
 
    EXPEDIENTE_DIGITAL {
        ObjectId _id PK
        ObjectId tramite_id FK
        array secciones_ids FK
        datetime fecha_creacion
        datetime ultima_actualizacion
    }
 
    SECCION_EXPEDIENTE {
        ObjectId _id PK
        ObjectId expediente_id FK
        ObjectId nodo_id FK
        ObjectId departamento_id FK
        int orden_seccion
        string estado "bloqueada | en_curso | completada"
        ObjectId funcionario_id FK
        datetime fecha_asignacion
        datetime fecha_completado
    }
 
    CAMPO_SECCION {
        ObjectId _id PK
        ObjectId seccion_id FK
        ObjectId campo_plantilla_id FK
        string nombre
        string valor
        string tipo
        boolean fue_dictado "true si se rellenó por voz"
        datetime fecha_guardado
    }
 
    ADJUNTO {
        ObjectId _id PK
        ObjectId seccion_id FK
        string nombre_archivo
        string tipo_mime
        string url_almacenamiento
        int tamano_bytes
        ObjectId subido_por FK
        datetime fecha_subida
    }
 
    TRANSCRIPCION_VOZ {
        ObjectId _id PK
        ObjectId seccion_id FK
        ObjectId funcionario_id FK
        string texto_transcrito
        float duracion_segundos
        float confianza_transcripcion
        datetime fecha_transcripcion
    }
 
    NOTIFICACION {
        ObjectId _id PK
        ObjectId destinatario_id FK
        ObjectId tramite_id FK
        string canal "push | web | email"
        string tipo "cambio_estado | asignacion | sla_vencido | observacion"
        string titulo
        string mensaje
        boolean leida
        string estado_envio "pendiente | enviada | fallida"
        int intentos_envio
        datetime fecha_creacion
        datetime fecha_leida
    }
 
    CANAL_ENVIO {
        ObjectId _id PK
        string tipo "push_flutter | web_internal | email"
        object configuracion
        boolean activo
    }
 
    TRAZABILIDAD {
        ObjectId _id PK
        ObjectId tramite_id FK
        ObjectId actor_id FK
        string accion "crear | editar | derivar | aprobar | rechazar | observar"
        ObjectId nodo_id FK
        object datos_antes
        object datos_despues
        string hash_actual "SHA-256 del registro"
        string hash_anterior "hash del registro previo"
        datetime timestamp
    }
 
    METRICA_TIEMPO {
        ObjectId _id PK
        ObjectId tramite_id FK
        ObjectId actividad_id FK
        ObjectId departamento_id FK
        int tiempo_segundos
        boolean supero_sla
        datetime fecha_inicio_actividad
        datetime fecha_fin_actividad
    }
 
    CUELLO_BOTELLA {
        ObjectId _id PK
        ObjectId actividad_id FK
        ObjectId departamento_id FK
        string periodo "dia | semana | mes"
        int tramites_acumulados
        float tiempo_promedio
        float tiempo_esperado
        float desviacion_porcentaje
        string causa_sugerida "IA — análisis automático"
        datetime fecha_deteccion
    }
 
    REPORTE {
        ObjectId _id PK
        ObjectId generado_por_id FK
        string tipo "productividad | cuellos | trazabilidad | politica"
        object filtros "rango fechas, departamento, estado, etc."
        string formato "PDF | EXCEL | CSV"
        string url_archivo
        datetime fecha_generacion
    }
 
    LOG_AGENTE {
        ObjectId _id PK
        ObjectId usuario_id FK
        string contexto_modulo "módulo activo al momento"
        string contexto_rol
        ObjectId contexto_tramite_id FK
        string consulta_usuario
        string respuesta_agente
        float tiempo_respuesta_ms
        boolean fue_util "feedback del usuario"
        datetime timestamp
    }
 