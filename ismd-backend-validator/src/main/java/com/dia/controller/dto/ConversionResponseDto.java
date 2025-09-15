package com.dia.controller.dto;

import com.dia.validation.data.DetailedValidationReportDto;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResponseDto {
    private String output;
    private String errorMessage;
    private ValidationResultsDto validationResults;
    private DetailedValidationReportDto validationReport;
    private CatalogRecordDto catalogReport;

    public static ConversionResponseDto success(String output, ValidationResultsDto results, DetailedValidationReportDto validationReport, CatalogRecordDto catalogReport) {
        return ConversionResponseDto.builder()
                .output(output)
                .validationResults(results)
                .validationReport(validationReport)
                .catalogReport(catalogReport)
                .build();
    }

    public static ConversionResponseDto error(String errorMessage) {
        return ConversionResponseDto.builder()
                .errorMessage(errorMessage)
                .build();
    }
}
