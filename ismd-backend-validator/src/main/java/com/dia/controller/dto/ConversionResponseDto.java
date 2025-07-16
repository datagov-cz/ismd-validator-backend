package com.dia.controller.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResponseDto {
    private String output;
    private String errorMessage;
    private ValidationResultsDto validationResults;

    public static ConversionResponseDto success(String output, ValidationResultsDto results) {
        return ConversionResponseDto.builder()
                .output(output)
                .validationResults(results)
                .build();
    }

    public static ConversionResponseDto error(String errorMessage) {
        return ConversionResponseDto.builder()
                .errorMessage(errorMessage)
                .build();
    }
}
