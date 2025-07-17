package com.dia.service.impl;

import com.dia.controller.dto.SeverityGroupDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.enums.ValidationSeverity;
import com.dia.service.ValidationReportService;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.validation.data.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationReportServiceImpl implements ValidationReportService {

    @Override
    public ValidationResultsDto convertToDto(ISMDValidationReport report) {
        log.debug("Converting validation report to DTO with {} results", report.getTotalResultCount());

        List<SeverityGroupDto> severityGroups = createMessageGroups(report);

        return new ValidationResultsDto(severityGroups);
    }

    private List<SeverityGroupDto> createMessageGroups(ISMDValidationReport report) {
        Map<String, List<ValidationResult>> messageGroups = report.results().stream()
                .collect(Collectors.groupingBy(result ->
                        result.severity() + "|" + result.message()
                ));

        return messageGroups.values().stream()
                .map(results -> {
                    ValidationResult firstResult = results.get(0);

                    String czechSeverity = getCzechSeverityName(firstResult.severity());
                    String ruleMessage = firstResult.message();
                    int count = results.size();

                    return new SeverityGroupDto(czechSeverity, count, ruleMessage);
                })
                .sorted((a, b) -> {
                    int severityOrder1 = getSeverityOrder(a.getSeverity());
                    int severityOrder2 = getSeverityOrder(b.getSeverity());

                    if (severityOrder1 != severityOrder2) {
                        return Integer.compare(severityOrder1, severityOrder2);
                    }

                    return a.getDescription().compareTo(b.getDescription());
                })
                .toList();
    }

    private String getCzechSeverityName(ValidationSeverity severity) {
        return switch (severity) {
            case ERROR -> "Chyba";
            case WARNING -> "Varování";
            case INFO -> "Informace";
        };
    }

    private int getSeverityOrder(String czechSeverity) {
        return switch (czechSeverity) {
            case "Chyba" -> 1;
            case "Varování" -> 2;
            case "Informace" -> 3;
            default -> 4;
        };
    }
}