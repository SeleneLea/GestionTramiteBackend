package com.example.demo.services;

import com.example.demo.dto.DocumentoRequest;
import com.example.demo.models.Documento;
import com.example.demo.repositories.DocumentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentoService {

    @Autowired
    private DocumentoRepository documentoRepository;

    public List<Documento> listarTodos() {
        return documentoRepository.findAll();
    }

    public List<Documento> listarActivos() {
        return documentoRepository.findByActivo(true);
    }

    public Optional<Documento> buscarPorId(String id) {
        return documentoRepository.findById(id);
    }

    public Documento crear(DocumentoRequest req) {
        Documento doc = new Documento();
        doc.setNombre(req.getNombre());
        doc.setDescripcion(req.getDescripcion());
        doc.setActivo(req.isActivo());
        doc.setFechaCreacion(LocalDateTime.now());
        return documentoRepository.save(doc);
    }

    public Documento actualizar(String id, DocumentoRequest req) {
        Documento doc = documentoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));
        doc.setNombre(req.getNombre());
        doc.setDescripcion(req.getDescripcion());
        doc.setActivo(req.isActivo());
        return documentoRepository.save(doc);
    }

    public void eliminar(String id) {
        if (!documentoRepository.existsById(id)) {
            throw new IllegalArgumentException("Documento no encontrado");
        }
        documentoRepository.deleteById(id);
    }
}
