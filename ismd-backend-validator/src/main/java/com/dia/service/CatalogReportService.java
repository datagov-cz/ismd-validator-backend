package com.dia.service;

import com.dia.controller.dto.CatalogRecordDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;

import java.util.Optional;

public interface CatalogReportService {

    Optional<CatalogRecordDto> generateCatalogReport(ConversionResult conversionResult, ValidationResultsDto validationResults);
}
