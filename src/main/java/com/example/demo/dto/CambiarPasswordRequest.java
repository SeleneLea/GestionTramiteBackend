package com.example.demo.dto;

import lombok.Data;

@Data
public class CambiarPasswordRequest {
    private String actual;
    private String nueva;
}
