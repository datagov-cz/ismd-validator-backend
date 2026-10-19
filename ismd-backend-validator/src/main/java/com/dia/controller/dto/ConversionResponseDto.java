package com.dia.controller.dto;

import com.dia.validation.data.DetailedValidationReportDto;
import lombok.*;

/** Conversion result, returned as the {@code data} of an {@link ApiResponseDto}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResponseDto {
    private String output;
    private ValidationResultsDto validationResults;
    private DetailedValidationReportDto validationReport;
    private String ontologyData;

    public static ConversionResponseDto success(String output, ValidationResultsDto results, DetailedValidationReportDto validationReport) {
        return ConversionResponseDto.builder()
                .output(output)
                .validationResults(results)
                .validationReport(validationReport)
                .build();
    }
}