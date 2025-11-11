package com.dia.controller;

import com.dia.controller.dto.ValidationRequestDto;
import com.dia.service.ValidationService;
import com.dia.validation.ValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/validate-ttl-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationReportDto> validateTtlFile(
            @RequestParam("file") MultipartFile file) {

        ISMDValidationReport ismdReport = validationService.validateTtlFile(file);

        ValidationReportDto dto = new ValidationReportDto(ismdReport.results(), Instant.now());

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/validate-rdf")
    public ResponseEntity<ValidationReportDto> validateRdf(
            @RequestBody ValidationRequestDto request,
            @RequestParam(defaultValue = "TURTLE") String format) {

        ISMDValidationReport ismdReport = validationService.validateRdf(
                request.getOntologyContent(),
                format
        );

        ValidationReportDto dto = new ValidationReportDto(ismdReport.results(), Instant.now());

        return ResponseEntity.ok(dto);
    }
}
