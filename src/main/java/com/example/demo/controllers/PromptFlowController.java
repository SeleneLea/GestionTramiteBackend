package com.example.demo.controllers;

import com.example.demo.dto.PromptFlujoRequest;
import com.example.demo.dto.PromptFlujoResponse;
import com.example.demo.services.PromptFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow-design")
@Tag(name = "Diseño por IA",
     description = "CU-14: el administrador describe el flujo en texto y la IA genera el diagrama")
public class PromptFlowController {

    @Autowired private PromptFlowService promptFlowService;

    @PostMapping("/from-prompt")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Generar diagrama desde un prompt",
               description = """
                       Recibe una descripción en lenguaje natural del proceso y genera un diagrama
                       con sus nodos y transiciones. El diagrama queda en estado 'borrador' para que
                       el administrador lo revise antes de publicarlo.

                       **Ejemplo de prompt:** "Crea un flujo donde Atención al Cliente recibe la
                       solicitud, luego Área Técnica hace la inspección, después Área Legal aprueba
                       y finalmente Operaciones cierra. Si Legal rechaza, vuelve a Técnica."
                       """)
    public ResponseEntity<PromptFlujoResponse> generar(@Valid @RequestBody PromptFlujoRequest req,
                                                     Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(promptFlowService.generarDesdePrompt(req, auth.getName()));
    }
}
