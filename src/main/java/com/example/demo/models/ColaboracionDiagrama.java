package com.example.demo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "colaboraciones_diagrama")
public class ColaboracionDiagrama {

    @Id
    private String id;

    private String diagramaId;
    private String adminInvitadorId;
    private String invitadoId;
    private String rolColaboracion;
    private String estado;

    private LocalDateTime fechaInvitacion;
    private LocalDateTime fechaRespuesta;
}

