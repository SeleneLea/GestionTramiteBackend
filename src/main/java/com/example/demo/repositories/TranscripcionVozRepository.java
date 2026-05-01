package com.example.demo.repositories;

import com.example.demo.models.TranscripcionVoz;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranscripcionVozRepository extends MongoRepository<TranscripcionVoz, String> {
    List<TranscripcionVoz> findBySeccionId(String seccionId);
}
