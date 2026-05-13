package com.example.demo.controllers;

import com.example.demo.services.IaProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Transcripción de voz a TEXTO (sin mapear a un formulario).
 *
 * La usa CU-41 "reportes por voz": el admin dicta la consulta, se transcribe y
 * el texto se coloca en el campo de consulta natural. Reutiliza el endpoint
 * {@code /nlp/voz-a-formulario} del microservicio con un schema vacío, del que
 * solo aprovecha {@code texto_transcrito}. Si el microservicio cae, el proxy
 * propaga {@code 503 IA_NO_DISPONIBLE}.
 */
@RestController
@RequestMapping("/api/transcripcion")
@Tag(name = "IA · Transcripción", description = "Voz → texto (p.ej. reportes por voz)")
public class TranscripcionController {

    @Autowired private IaProxyService iaProxy;

    @PostMapping(value = "/voz-a-texto", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMINISTRADOR')")
    @Operation(summary = "Transcribe el audio dictado y devuelve solo el texto")
    public ResponseEntity<Map<String, String>> vozATexto(@RequestParam("audio") MultipartFile audio) {
        // schema vacío → el microservicio devuelve texto_transcrito sin mapear campos
        Map<String, Object> resp = iaProxy.vozAFormulario(audio, "[]");
        Object texto = resp.get("texto_transcrito");
        return ResponseEntity.ok(Map.of("textoTranscrito", texto != null ? texto.toString() : ""));
    }
}
