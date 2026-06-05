package com.example.demo.config.seeders;

import com.example.demo.models.CampoPlantilla;
import com.example.demo.models.FormularioPlantilla;
import com.example.demo.models.NodoDiagrama;
import com.example.demo.repositories.CampoPlantillaRepository;
import com.example.demo.repositories.FormularioPlantillaRepository;
import com.example.demo.repositories.NodoDiagramaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class FormularioSeeder {

    @Autowired private FormularioPlantillaRepository formularioRepository;
    @Autowired private CampoPlantillaRepository campoRepository;
    @Autowired private NodoDiagramaRepository nodoRepository;

    public void seed() {

        List<NodoDiagrama> actividadNodos = nodoRepository.findAll().stream()
                .filter(n -> "actividad".equals(n.getTipo()))
                .toList();

        int creados = 0;
        for (NodoDiagrama nodo : actividadNodos) {
            if (!formularioRepository.findByNodoId(nodo.getId()).isEmpty()) continue;
            crearFormularioPorNodo(nodo);
            creados++;
        }
        log.info("[Seeder] Formularios OK ({} nuevos, {} actividades totales)",
                creados, actividadNodos.size());
    }

    private void crearFormularioPorNodo(NodoDiagrama nodo) {
        switch (nodo.getNombre()) {
            case "Verificar Documentos" -> crearFormVerificacion(nodo.getId());
            case "Inspeccion en Campo"  -> crearFormInspeccion(nodo.getId());
            case "Elaborar Presupuesto" -> crearFormPresupuesto(nodo.getId());
            case "Revisar Contrato"     -> crearFormContrato(nodo.getId());
            case "Cierre y Conexion"    -> crearFormCierre(nodo.getId());
            default                     -> crearFormGenerico(nodo);
        }
    }

    private void crearFormGenerico(NodoDiagrama nodo) {
        String nombreForm = "Formulario · " + (nodo.getNombre() != null ? nodo.getNombre() : "Actividad");
        FormularioPlantilla form = guardarForm(nodo.getId(), nombreForm, true, true);
        guardarCampo(form.getId(), "fecha_atencion",       "Fecha de atención",              "fecha",    true,  null, null, 1);
        guardarCampo(form.getId(), "responsable",          "Responsable que atiende",        "texto",    true,  null, null, 2);
        guardarCampo(form.getId(), "resumen_actuacion",    "Resumen de la actuación",        "textarea", true,  null, null, 3);
        guardarCampo(form.getId(), "resultado",            "Resultado de la revisión",       "select",   true,
                List.of("Conforme", "Con observaciones", "No conforme"), null, 4);
        guardarCampo(form.getId(), "requiere_seguimiento", "Requiere seguimiento posterior", "checkbox", false, null, null, 5);
        guardarCampo(form.getId(), "observaciones",        "Observaciones adicionales",      "textarea", false, null, null, 6);
    }

    private void crearFormVerificacion(String nodoId) {
        FormularioPlantilla form = guardarForm(nodoId, "Verificacion Documental", true, false);
        guardarCampo(form.getId(), "nombre_solicitante", "Nombre completo del solicitante", "texto", true, null, null, 1);
        guardarCampo(form.getId(), "numero_ci",          "Numero de cedula de identidad",   "texto", true, null, "^[0-9]{6,9}[A-Z]?$", 2);
        guardarCampo(form.getId(), "domicilio",          "Domicilio exacto del inmueble",   "textarea", true, null, null, 3);
        guardarCampo(form.getId(), "telefono",           "Telefono de contacto",            "texto", true, null, "^[67][0-9]{7}$", 4);
        guardarCampo(form.getId(), "tipo_solicitud",     "Tipo de conexion solicitada",     "select", true,
                List.of("Monofasica", "Trifasica", "Industrial"), null, 5);
        guardarCampo(form.getId(), "documentos_completos","Documentos entregados completos", "checkbox", true, null, null, 6);
    }

    private void crearFormInspeccion(String nodoId) {
        FormularioPlantilla form = guardarForm(nodoId, "Inspeccion Tecnica en Campo", false, true);
        guardarCampo(form.getId(), "fecha_inspeccion",       "Fecha de la visita tecnica",          "fecha",    true,  null, null, 1);
        guardarCampo(form.getId(), "descripcion_sitio",      "Descripcion del sitio inspeccionado", "textarea", true,  null, null, 2);
        guardarCampo(form.getId(), "condiciones_electricas", "Condiciones electricas observadas",   "select",   true,
                List.of("Optimas", "Regulares", "Deficientes", "Peligrosas"), null, 3);
        guardarCampo(form.getId(), "requiere_obra_civil",    "Requiere obra civil previa",          "checkbox", false, null, null, 4);
        guardarCampo(form.getId(), "distancia_red_m",        "Distancia a la red en metros",        "numero",   true,  null, null, 5);
        guardarCampo(form.getId(), "observaciones_tecnicas", "Observaciones adicionales",           "textarea", false, null, null, 6);
    }

    private void crearFormPresupuesto(String nodoId) {
        FormularioPlantilla form = guardarForm(nodoId, "Presupuesto Tecnico", true, false);
        guardarCampo(form.getId(), "descripcion_trabajos", "Descripcion detallada de trabajos",  "textarea", true,  null, null, 1);
        guardarCampo(form.getId(), "materiales_bs",        "Costo de materiales (Bs.)",          "numero",   true,  null, null, 2);
        guardarCampo(form.getId(), "mano_obra_bs",         "Costo de mano de obra (Bs.)",        "numero",   true,  null, null, 3);
        guardarCampo(form.getId(), "total_bs",             "Monto total del presupuesto (Bs.)",  "numero",   true,  null, null, 4);
        guardarCampo(form.getId(), "plazo_ejecucion_dias", "Plazo de ejecucion en dias",         "numero",   true,  null, null, 5);
        guardarCampo(form.getId(), "validez_dias",         "Validez del presupuesto (dias)",     "numero",   true,  null, null, 6);
    }

    private void crearFormContrato(String nodoId) {
        FormularioPlantilla form = guardarForm(nodoId, "Revision Contractual", true, false);
        guardarCampo(form.getId(), "numero_contrato",    "Numero de contrato generado",     "texto",    true,  null, null, 1);
        guardarCampo(form.getId(), "fecha_revision",     "Fecha de revision legal",         "fecha",    true,  null, null, 2);
        guardarCampo(form.getId(), "clausulas_ok",       "Clausulas revisadas y conformes", "checkbox", true,  null, null, 3);
        guardarCampo(form.getId(), "resultado_revision", "Resultado de la revision",        "select",   true,
                List.of("Aprobado", "Observado", "Rechazado"), null, 4);
        guardarCampo(form.getId(), "observaciones_legales", "Observaciones del area legal", "textarea", false, null, null, 5);
    }

    private void crearFormCierre(String nodoId) {
        FormularioPlantilla form = guardarForm(nodoId, "Cierre y Conexion Electrica", true, true);
        guardarCampo(form.getId(), "fecha_ejecucion",      "Fecha de ejecucion del trabajo",  "fecha",   true,  null, null, 1);
        guardarCampo(form.getId(), "tecnico_nombre",       "Nombre del tecnico asignado",     "texto",   true,  null, null, 2);
        guardarCampo(form.getId(), "numero_medidor",       "Numero de medidor instalado",     "texto",   true,  null, "^[A-Z0-9]{6,12}$", 3);
        guardarCampo(form.getId(), "potencia_kw",          "Potencia contratada (kW)",        "numero",  true,  null, null, 4);
        guardarCampo(form.getId(), "prueba_funcionamiento","Prueba de funcionamiento OK",     "checkbox", true,  null, null, 5);
        guardarCampo(form.getId(), "observaciones_finales","Observaciones finales del cierre","textarea", false, null, null, 6);
    }

    private FormularioPlantilla guardarForm(String nodoId, String nombre,
                                             boolean permiteAdjuntos, boolean permiteDictadoVoz) {
        FormularioPlantilla f = new FormularioPlantilla();
        f.setNodoId(nodoId);
        f.setNombre(nombre);
        f.setCamposPlantillaIds(new ArrayList<>());
        f.setPermiteAdjuntos(permiteAdjuntos);
        f.setPermiteDictadoVoz(permiteDictadoVoz);
        return formularioRepository.save(f);
    }

    private void guardarCampo(String formularioId, String nombre, String etiqueta,
                               String tipo, boolean obligatorio, List<String> opciones,
                               String regex, int orden) {
        CampoPlantilla c = new CampoPlantilla();
        c.setFormularioPlantillaId(formularioId);
        c.setNombre(nombre);
        c.setEtiqueta(etiqueta);
        c.setTipo(tipo);
        c.setObligatorio(obligatorio);
        c.setOpciones(opciones);
        c.setValidacionRegex(regex);
        c.setOrden(orden);
        CampoPlantilla saved = campoRepository.save(c);

        formularioRepository.findById(formularioId).ifPresent(f -> {
            if (f.getCamposPlantillaIds() == null) f.setCamposPlantillaIds(new ArrayList<>());
            f.getCamposPlantillaIds().add(saved.getId());
            formularioRepository.save(f);
        });
    }
}
