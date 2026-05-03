package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewDocumentoResponse {
    private String urlPreview;
    private String mimeType;
    private Instant expiraEn;
}
