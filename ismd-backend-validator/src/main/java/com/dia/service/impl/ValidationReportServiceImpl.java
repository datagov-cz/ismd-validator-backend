package com.dia.service.impl;

import com.dia.controller.dto.SeverityGroupDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.enums.ValidationSeverity;
import com.dia.service.ValidationReportService;
import com.dia.validation.data.ISMDValidationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationReportServiceImpl implements ValidationReportService {

    @Override
    public ValidationResultsDto convertToDto(ISMDValidationReport report) {
        log.debug("Converting validation report to DTO with {} results", report.getTotalResultCount());

        List<SeverityGroupDto> severityGroups = createSeverityGroups(report);

        return new ValidationResultsDto(severityGroups);
    }

    private List<SeverityGroupDto> createSeverityGroups(ISMDValidationReport report) {
        return Arrays.stream(ValidationSeverity.values())
                .map(severity -> createSeverityGroup(severity, report))
                .filter(group -> group.getCount() > 0)
                .toList();
    }

    private SeverityGroupDto createSeverityGroup(ValidationSeverity severity, ISMDValidationReport report) {
        int count = report.getResultsBySeverity(severity).size();
        String description = getSeverityDisplayName(severity);

        return new SeverityGroupDto(severity, count, description);
    }

    private String getSeverityDisplayName(ValidationSeverity severity) {
        return switch (severity) {
            case ERROR -> "Chyby";
            case WARNING -> "Varování";
            case INFO -> "Informace";
            default -> "Neznámé";
        };
    }
}
