package com.example.demo.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "secuencias")
public class Secuencia {

    @Id
    private String id;

    private long seq;
}
