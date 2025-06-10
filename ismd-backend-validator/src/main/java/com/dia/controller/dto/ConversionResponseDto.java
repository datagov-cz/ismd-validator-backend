package com.dia.controller.dto;

import lombok.*;

@Getter
@Setter
@Data
@AllArgsConstructor
public class ConversionResponseDto {
    private String response;
    private String errorMessage;
}
