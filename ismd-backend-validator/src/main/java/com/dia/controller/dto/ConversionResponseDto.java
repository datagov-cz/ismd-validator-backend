package com.dia.controller.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResponseDto {
    private String output;
    private String errorMessage;

    public static ConversionResponseDto success(String output) {
        return ConversionResponseDto.builder()
                .output(output)
                .build();
    }

    public static ConversionResponseDto error(String errorMessage) {
        return ConversionResponseDto.builder()
                .errorMessage(errorMessage)
                .build();
    }
}
