package com.dia.controller.dto;

import com.dia.validation.ValidationReport;
import lombok.Getter;

@Getter
public class CatalogRecordRequestDto {
    private ValidationReport validationReport;
    private String ttlContent;
}
