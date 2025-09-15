package com.dia.service;

import com.dia.controller.dto.ValidationResultsDto;
import com.dia.validation.data.ISMDValidationReport;

public interface ValidationReportService {
    ValidationResultsDto convertToDto(ISMDValidationReport report);

    ValidationResultsDto convertToDto(ISMDValidationReport localReport, ISMDValidationReport globalReport);
}
