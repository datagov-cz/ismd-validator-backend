package com.dia.controller;

import com.dia.controller.dto.CatalogReportDto;
import com.dia.controller.dto.ConversionResponseDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.UnsupportedFormatException;
import com.dia.service.*;
import com.dia.validation.data.DetailedValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.dia.constants.ConverterControllerConstants.*;
import static com.dia.enums.FileFormat.ARCHI_XML;
import static com.dia.enums.FileFormat.XMI;

@RestController
@RequestMapping("/api/converter")
@RequiredArgsConstructor
@Slf4j
public class ConverterController {

    private static final long MAX_FILE_SIZE = 5242880;

    private final ConverterService converterService;
    private final ValidationService validationService;
    private final ValidationReportService validationReportService;
    private final DetailedValidationReportService detailedValidationReportService;
    private final CatalogReportService catalogReportService;

    @PostMapping("/convert")
    public ResponseEntity<ConversionResponseDto> convertFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "output", required = false) String output,
            @RequestParam(value = "removeInvalidSources", required = false) Boolean removeInvalidSources,
            @RequestParam(value = "includeDetailedReport", required = false, defaultValue = "true") Boolean includeDetailedReport,
            @RequestParam(value = "includeCatalogRecord", required = false, defaultValue = "true") Boolean includeCatalogRecord,
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            HttpServletRequest request
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        String outputFormat = determineOutputFormat(output, acceptHeader);

        log.info("File conversion requested: filename={}, size={}, outputFormat={}, remove invalid sources={}, include detailed report={}",
                file.getOriginalFilename(), file.getSize(), output, removeInvalidSources, includeDetailedReport);

        try {
            if (!validateSingleFileUpload(request, requestId)) {
                return ResponseEntity.badRequest()
                        .body(ConversionResponseDto.error("Můžete nahrát pouze jeden soubor."));
            }

            if (file.isEmpty()) {
                log.warn("Empty file upload attempt");
                return ResponseEntity.badRequest()
                        .body(ConversionResponseDto.error("Nebyl vložen žádný soubor."));
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                log.warn("File too large: filename={}, size={}, maxAllowedSize={}",
                        file.getOriginalFilename(), file.getSize(), MAX_FILE_SIZE);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(ConversionResponseDto.error("Soubor je příliš velký. Maximální povolená velikost je 5 MB."));
            }

            FileFormat fileFormat = checkFileFormat(file);
            if (fileFormat == FileFormat.UNSUPPORTED) {
                log.warn("Unsupported file type upload attempt");
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(ConversionResponseDto.error("Nepodporovaný formát souboru."));
            }

            log.info("File format determined: requestId={}, format={}", requestId, fileFormat);

            return switch (fileFormat) {
                case ARCHI_XML -> {
                    log.debug("Processing Archi XML file: requestId={}", requestId);
                    String xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                    ConversionResult conversionResult = converterService.processArchiFile(xmlContent, removeInvalidSources);

                    ValidationResultsDto results = performValidation(conversionResult, requestId);
                    DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                            generateDetailedValidationReport(conversionResult, requestId) : null;

                    CatalogReportDto catalogReport = Boolean.TRUE.equals(includeCatalogRecord) ?
                            generateCatalogReport(conversionResult, results, requestId) : null;

                    ResponseEntity<ConversionResponseDto> response = getResponseEntity(
                            outputFormat, fileFormat, conversionResult, results, detailedReport, catalogReport
                    );
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}, validationResults={}, detailedReportIncluded={}",
                            requestId, fileFormat, output, results, includeDetailedReport);
                    yield response;
                }
                case XMI -> {
                    log.debug("Processing XMI file: requestId={}", requestId);
                    ConversionResult conversionResult = converterService.processEAFile(file, removeInvalidSources);

                    ValidationResultsDto results = performValidation(conversionResult, requestId);
                    DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                            generateDetailedValidationReport(conversionResult, requestId) : null;

                    CatalogReportDto catalogReport = Boolean.TRUE.equals(includeCatalogRecord) ?
                            generateCatalogReport(conversionResult, results, requestId) : null;

                    ResponseEntity<ConversionResponseDto> response = getResponseEntity(
                            outputFormat, fileFormat, conversionResult, results, detailedReport, catalogReport
                    );
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}, validationResults={}, detailedReportIncluded={}",
                            requestId, fileFormat, output, results, includeDetailedReport);
                    yield response;
                }
                case XLSX -> {
                    log.debug("Processing XLSX file: requestId={}", requestId);
                    ConversionResult conversionResult = converterService.processExcelFile(file, removeInvalidSources);

                    ValidationResultsDto results = performValidation(conversionResult, requestId);
                    DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                            generateDetailedValidationReport(conversionResult, requestId) : null;

                    CatalogReportDto catalogReport = Boolean.TRUE.equals(includeCatalogRecord) ?
                            generateCatalogReport(conversionResult, results, requestId) : null;

                    ResponseEntity<ConversionResponseDto> response = getResponseEntity(
                            outputFormat, fileFormat, conversionResult, results, detailedReport, catalogReport
                    );
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}, validationResults={}, detailedReportIncluded={}",
                            requestId, fileFormat, output, results, includeDetailedReport);
                    yield response;
                }
                case TURTLE -> ResponseEntity.ok(ConversionResponseDto.success("File processed successfully", null, null, null));
                default -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ConversionResponseDto.error("Nepodporovaný formát souboru."));
            };
        } catch (UnsupportedFormatException e) {
            log.error("Unsupported format exception: requestId={}, message={}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(ConversionResponseDto.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing file conversion: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ConversionResponseDto.error(e.getMessage()));
        } finally {
            MDC.remove(LOG_REQUEST_ID);
        }
    }

    @PostMapping("/convert/detailed-report/csv")
    public ResponseEntity<String> downloadDetailedValidationReportCSV(
            @RequestBody ConversionResponseDto conversionResponse,
            @RequestParam(value = "filename", required = false, defaultValue = "validation-report") String filename
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        log.info("CSV download requested from existing conversion response, filename={}", filename);

        try {
            DetailedValidationReportDto detailedReport = conversionResponse.getValidationReport();

            if (detailedReport == null) {
                log.warn("No detailed validation report found in conversion response: requestId={}", requestId);
                return ResponseEntity.badRequest()
                        .body("No detailed validation report available. Please ensure the conversion was performed with includeDetailedReport=true.");
            }

            String csvContent = detailedValidationReportService.generateCSV(detailedReport);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String finalFilename = filename + "_" + timestamp + ".csv";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
            headers.setContentDispositionFormData("attachment", finalFilename);
            headers.add("Content-Length", String.valueOf(csvContent.getBytes(StandardCharsets.UTF_8).length));

            log.info("CSV report generated successfully from existing data: requestId={}, filename={}, concepts={}",
                    requestId, finalFilename, detailedReport.validation().size());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvContent);

        } catch (Exception e) {
            log.error("Error generating CSV from conversion response: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate CSV report: " + e.getMessage());
        } finally {
            MDC.remove(LOG_REQUEST_ID);
        }
    }

    private boolean validateSingleFileUpload(HttpServletRequest request, String requestId) {
        if (request instanceof MultipartHttpServletRequest multipartRequest) {

            int totalFiles = 0;
            Map<String, List<MultipartFile>> fileMap = multipartRequest.getMultiFileMap();

            for (Map.Entry<String, List<MultipartFile>> entry : fileMap.entrySet()) {
                totalFiles += entry.getValue().size();
            }

            if (totalFiles > 1) {
                log.warn("Multiple files upload rejected: requestId={}, fileCount={}", requestId, totalFiles);
                return false;
            }

            log.debug("Single file validation passed: requestId={}", requestId);
        }
        return true;
    }

    private FileFormat checkFileFormat(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        log.debug("Checking file format: filename={}", filename);

        if (checkForXlsx(file)) {
            log.debug("XLSX format detected: filename={}", filename);
            return FileFormat.XLSX;
        } else if (checkForXmlOrXmi(file) == ARCHI_XML) {
            log.debug("Archi XML format detected: filename={}", filename);
            return ARCHI_XML;
        } else if (checkForXmlOrXmi(file) == XMI) {
            log.debug("XMI format detected: filename={}", filename);
            return XMI;
        } else if (checkForTurtle(file) == FileFormat.TURTLE) {
            log.debug("Turtle format detected: filename={}", filename);
            return FileFormat.TURTLE;
        }

        log.debug("Unsupported format detected: filename={}, contentType={}",
                filename, file.getContentType());
        return FileFormat.UNSUPPORTED;
    }

    private boolean checkForXlsx(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            return extension.equals("xlsx");
        }
        return false;
    }

    private FileFormat checkForXmlOrXmi(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType != null && (contentType.equals("application/xml")
                || contentType.equals("text/xml")
                || contentType.contains("xml"))) {

            byte[] bytes = new byte[4096];
            int bytesRead;
            try (InputStream stream = file.getInputStream()) {
                bytesRead = stream.read(bytes);
            }
            if (bytesRead > 0) {
                String xmlHeader = new String(bytes, StandardCharsets.UTF_8);

                if (xmlHeader.contains(ARCHI_3_HEADER)
                        || xmlHeader.contains(ARCHIMATE_HEADER)) {
                    return ARCHI_XML;
                }

                if (xmlHeader.contains(XMI_HEADER)
                        || xmlHeader.contains("xmi:version=\"2.1\"")
                        || xmlHeader.contains("xmi:version=\"2.0\"")
                        || xmlHeader.contains("xmi:XMI")
                        || xmlHeader.contains("<xmi:XMI")) {
                    return XMI;
                }
            }
        }
        return FileFormat.UNSUPPORTED;
    }

    private FileFormat checkForTurtle(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.equals("text/turtle")) {
            return FileFormat.TURTLE;
        }

        return FileFormat.UNSUPPORTED;
    }

    private ResponseEntity<ConversionResponseDto> getResponseEntity(
            String outputFormat, FileFormat fileFormat, ConversionResult conversionResult,
            ValidationResultsDto results, DetailedValidationReportDto detailedReport,
            CatalogReportDto catalogReport) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.debug("Preparing response entity: requestId={}, outputFormat={}", requestId, outputFormat);

        return switch (outputFormat.toLowerCase()) {
            case "json" -> {
                log.debug("Exporting to JSON: requestId={}", requestId);
                String jsonOutput = converterService.exportToJson(fileFormat, conversionResult.getTransformationResult());
                log.debug("JSON export completed: requestId={}, outputSize={}", requestId, jsonOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ConversionResponseDto.success(jsonOutput, results, detailedReport, catalogReport));
            }
            case "ttl" -> {
                log.debug("Exporting to Turtle: requestId={}", requestId);
                String ttlOutput = converterService.exportToTurtle(fileFormat, conversionResult.getTransformationResult());
                log.debug("Turtle export completed: requestId={}, outputSize={}", requestId, ttlOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ConversionResponseDto.success(ttlOutput, results, detailedReport, catalogReport));
            }
            default -> {
                log.warn("Unsupported output format requested: requestId={}, format={}", requestId, outputFormat);
                throw new UnsupportedFormatException("Nepodporovaný výstupní formát: " + outputFormat);
            }
        };
    }

    private String determineOutputFormat(String output, String acceptHeader) {
        if (output != null && !output.isEmpty()) {
            return output.toLowerCase();
        }

        if (acceptHeader != null && !acceptHeader.isEmpty()) {
            if (acceptHeader.contains("application/json")) {
                return "json";
            } else if (acceptHeader.contains("text/turtle") || acceptHeader.contains("application/x-turtle")) {
                return "ttl";
            }
        }

        return "json";
    }

    private ValidationResultsDto performValidation(ConversionResult conversionResult, String requestId) {
        try {
            ISMDValidationReport combinedReport = validationService.validate(conversionResult.getTransformationResult());

            log.debug("Combined validation completed: requestId={}, results={}", requestId, combinedReport.getSummary());
            return validationReportService.convertToDto(combinedReport);

        } catch (Exception e) {
            log.error("Validation failed but continuing with conversion: requestId={}", requestId, e);

            try {
                ISMDValidationReport localReport = validationService.validate(conversionResult.getTransformationResult());
                log.warn("Fallback to local validation successful: requestId={}", requestId);
                return validationReportService.convertToDto(localReport);
            } catch (Exception fallbackE) {
                log.error("Even local validation failed: requestId={}", requestId, fallbackE);
                return null;
            }
        }
    }

    private DetailedValidationReportDto generateDetailedValidationReport(ConversionResult conversionResult, String requestId) {
        try {
            log.debug("Generating detailed validation report: requestId={}", requestId);

            ISMDValidationReport combinedReport = validationService.validate(conversionResult.getTransformationResult());

            DetailedValidationReportDto detailedReport = detailedValidationReportService.generateDetailedReport(
                    combinedReport,
                    conversionResult.getTransformationResult().getOntModel()
            );

            log.debug("Detailed validation report generated successfully: requestId={}, concepts={}",
                    requestId, detailedReport.validation().size());

            return detailedReport;

        } catch (Exception e) {
            log.error("Failed to generate detailed validation report: requestId={}", requestId, e);

            try {
                ISMDValidationReport localReport = validationService.validate(conversionResult.getTransformationResult());
                DetailedValidationReportDto detailedReport = detailedValidationReportService.generateDetailedReport(
                        localReport,
                        conversionResult.getTransformationResult().getOntModel()
                );

                log.warn("Fallback detailed report generation successful: requestId={}", requestId);
                return detailedReport;

            } catch (Exception fallbackE) {
                log.error("Even fallback detailed report generation failed: requestId={}", requestId, fallbackE);
                return null;
            }
        }
    }

    private CatalogReportDto generateCatalogReport(ConversionResult conversionResult, ValidationResultsDto validationResults, String requestId) {
        try {
            log.debug("Attempting to generate catalog report: requestId={}", requestId);

            Optional<CatalogReportDto> catalogReport = catalogReportService.generateCatalogReport(conversionResult, validationResults);

            if (catalogReport.isPresent()) {
                log.info("Catalog report generated successfully: requestId={}", requestId);
                return catalogReport.get();
            } else {
                log.info("Catalog report not generated due to validation errors or processing issues: requestId={}", requestId);
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to generate catalog report: requestId={}", requestId, e);
            return null;
        }
    }
}