package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "canales_envio")
public class CanalEnvio {

    @Id
    private String id;

    private String tipo;
    private Map<String, Object> configuracion;
    private boolean activo;
}

