package com.dia.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ErrorResponseDto {
    private String error;
    private String message;
    private Instant timestamp;
    private String requestId;
}