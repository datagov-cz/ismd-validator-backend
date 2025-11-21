package com.dia.service;

import com.dia.controller.dto.CatalogRecordDto;
import com.dia.controller.dto.CatalogRecordRequestDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;
import com.dia.validation.ValidationReportDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface CatalogReportService {

    Optional<CatalogRecordDto> generateCatalogReport(ConversionResult conversionResult, ValidationResultsDto validationResults, String requestId);

    Optional<CatalogRecordDto> generateCatalogReportFromFile(MultipartFile file, ValidationResultsDto validationResults, String requestId);

    Optional<CatalogRecordDto> generateCatalogReportFromTool(CatalogRecordRequestDto request, String requestId);
}
