package com.dia.controller;

import com.dia.controller.dto.ValidationRequestDto;
import com.dia.service.ValidationService;
import com.dia.validation.ValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;

@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
@Slf4j
public class ValidatorController {

    private final ValidationService validationService;

    @PostMapping("/validate")
    public ResponseEntity<ValidationReportDto> validateOntology(
            @RequestBody ValidationRequestDto request) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        try {
            log.info("Received ontology validation request [requestId={}]", requestId);

            ISMDValidationReport ismdReport = validationService.validateTtl(
                    request.getOntologyContent()
            );

            ValidationReportDto dto = new ValidationReportDto(ismdReport.results(), request.getIri(), Instant.now());

            log.info("Ontology validation completed successfully [requestId={}]", requestId);
            return ResponseEntity.ok(dto);
        } finally {
            MDC.remove(LOG_REQUEST_ID);
        }
    }

    @PostMapping(value = "/validate-rdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationReportDto> validateRdf(
            @RequestPart("file") MultipartFile file
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        try {
            log.info("Received RDF validation request [requestId={}]", requestId);

            ISMDValidationReport ismdReport = validationService.validateTtlFile(file);

            ValidationReportDto dto = new ValidationReportDto(ismdReport.results(), Instant.now());

            log.info("RDF validation completed successfully [requestId={}]", requestId);
            return ResponseEntity.ok(dto);
        } finally {
            MDC.remove(LOG_REQUEST_ID);
        }
    }
}
