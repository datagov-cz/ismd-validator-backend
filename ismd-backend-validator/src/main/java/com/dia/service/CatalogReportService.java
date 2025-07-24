package com.dia.service;

import com.dia.controller.dto.CatalogReportDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;

import java.util.Optional;

public interface CatalogReportService {

    Optional<CatalogReportDto> generateCatalogReport(ConversionResult conversionResult, ValidationResultsDto validationResults);
}
