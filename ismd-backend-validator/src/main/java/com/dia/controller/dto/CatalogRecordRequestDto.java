package com.dia.controller.dto;

import com.dia.validation.ValidationReportDto;
import lombok.Getter;

@Getter
public class CatalogRecordRequestDto {
    private ValidationReportDto validationReport;
    private String ttlContent;
}
