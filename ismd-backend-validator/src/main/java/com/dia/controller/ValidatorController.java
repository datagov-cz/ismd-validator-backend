package com.dia.controller;

import com.dia.controller.dto.ValidationRequestDto;
import com.dia.service.ValidationService;
import com.dia.validation.ValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
@Slf4j
public class ValidatorController {

    private final ValidationService validationService;

    @PostMapping("/validate")
    public ResponseEntity<ValidationReportDto> validateOntology(
            @RequestBody ValidationRequestDto request) {

        ISMDValidationReport ismdReport = validationService.validateTtl(
                request.getOntologyContent()
        );

        ValidationReportDto dto = new ValidationReportDto(ismdReport.results(), Instant.now());

        return ResponseEntity.ok(dto);
    }
}
