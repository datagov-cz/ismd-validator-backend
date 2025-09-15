package com.dia.controller;

import com.dia.controller.data.UrlMultipartFile;
import com.dia.controller.dto.CatalogRecordDto;
import com.dia.controller.dto.ConversionResponseDto;
import com.dia.controller.dto.ValidationResultsDto;
import com.dia.conversion.data.ConversionResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.SecurityException;
import com.dia.exceptions.UnsupportedFormatException;
import com.dia.service.*;
import com.dia.utility.UtilityMethods;
import com.dia.validation.data.DetailedValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
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
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static com.dia.constants.ConverterControllerConstants.*;
import static com.dia.enums.FileFormat.*;

@RestController
@RequestMapping("/api/converter")
@RequiredArgsConstructor
@Slf4j
public class ConverterController {

    private static final long MAX_FILE_SIZE = 5242880;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("https");
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
            "^(10\\.|172\\.(1[6-9]|2\\d|3[0-1])\\.|192\\.168\\.|127\\.|169\\.254\\.|::1|fc00:|fe80:)"
    );

    private final ConverterService converterService;
    private final ValidationService validationService;
    private final ValidationReportService validationReportService;
    private final DetailedValidationReportService detailedValidationReportService;
    private final CatalogReportService catalogReportService;

    private HttpClient httpClient;

    @Operation(
            summary = "Konverze slovníku ze souboru nebo URL do formátu dle OFN a jeho export dle parametrů,",
            description = "Konverze slovníku ze souboru nebo URL do formátu dle OFN a jeho export dle parametrů. Podporuje vstup v Archi xml, EA xmi, Excel xlsx, a SSP. Podporuje vložení pouze jednoho souboru. Podporuje slovníky v url dle OFN. Formát výstupu konverze podporuje json-ld a ttl. Podporuje validaci dle SHACL. Podporuje generaci výpisu z validace a generaci nekompletního katalogizačního záznamu do NKD."
    )
    @PostMapping("/convert")
    public ResponseEntity<?> convertFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "fileUrl", required = false) String fileUrl,
            @RequestParam(value = "output", required = false) String output,
            @RequestParam(value = "includeDetailedReport", required = false, defaultValue = "true") Boolean includeDetailedReport,
            @RequestParam(value = "includeCatalogRecord", required = false, defaultValue = "true") Boolean includeCatalogRecord,
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            HttpServletRequest request
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        String outputFormat = determineOutputFormat(output, acceptHeader);

        if (file != null) {
            log.info("File conversion requested: filename={}, size={}, outputFormat={}, include detailed report={}",
                    file.getOriginalFilename(), file.getSize(), output, includeDetailedReport);
        }

        if (fileUrl != null) {
            log.info("File conversion requested: fileUrl={}, outputFormat={}, include detailed report={}",
                    fileUrl, output, includeDetailedReport);
        }

        try {
            if (file != null && fileUrl != null) {
                return ResponseEntity.badRequest()
                        .body(ConversionResponseDto.error("Můžete zvolit pouze jeden způsob nahrání slovníků."));
            }

            if (!validateSingleFileUpload(request, requestId)) {
                return ResponseEntity.badRequest()
                        .body(ConversionResponseDto.error("Můžete nahrát pouze jeden soubor."));
            }

            MultipartFile processedFile = file;

            if (fileUrl != null) {
                processedFile = downloadAsMultipartFile(fileUrl);
            }

            if (processedFile == null || processedFile.isEmpty()) {
                log.warn("Empty file upload attempt");
                return ResponseEntity.badRequest()
                        .body(ConversionResponseDto.error("Nebyl vložen žádný soubor."));
            }

            if (processedFile.getSize() > MAX_FILE_SIZE) {
                log.warn("File too large: filename={}, size={}, maxAllowedSize={}",
                        processedFile.getOriginalFilename(), processedFile.getSize(), MAX_FILE_SIZE);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(ConversionResponseDto.error("Soubor je příliš velký. Maximální povolená velikost je 5 MB."));
            }

            FileFormat fileFormat = checkFileFormat(processedFile);
            if (fileFormat == UNSUPPORTED) {
                log.warn("Unsupported file type upload attempt");
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(ConversionResponseDto.error("Nepodporovaný formát souboru."));
            }

            log.info("File format determined: requestId={}, format={}", requestId, fileFormat);

            return switch (fileFormat) {
                case ARCHI_XML -> {
                    log.debug("Processing Archi XML file: requestId={}", requestId);
                    String xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                    ConversionResult conversionResult = converterService.processArchiFile(xmlContent);

                    ISMDValidationReport report = validationService.validate(conversionResult.getTransformationResult());
                    ValidationResultsDto results = convertReportToDto(report);
                    DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                            generateDetailedValidationReport(report, conversionResult.getTransformationResult().getOntModel(), requestId) : null;

                    Optional<CatalogRecordDto> catalogRecord = Boolean.TRUE.equals(includeCatalogRecord) ?
                            catalogReportService.generateCatalogReport(conversionResult, results, requestId) : Optional.empty();

                    ResponseEntity<?> response = getResponseEntity(
                            outputFormat, fileFormat, conversionResult, results, detailedReport, catalogRecord.get()
                    );
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}, validationResults={}, detailedReportIncluded={}",
                            requestId, fileFormat, output, results, includeDetailedReport);
                    yield response;
                }
                case XMI -> {
                    log.debug("Processing XMI file: requestId={}", requestId);
                    ConversionResult conversionResult = converterService.processEAFile(file);

                    ISMDValidationReport report = validationService.validate(conversionResult.getTransformationResult());
                    ValidationResultsDto results = convertReportToDto(report);
                    DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                            generateDetailedValidationReport(report, conversionResult.getTransformationResult().getOntModel(), requestId) : null;

                    Optional<CatalogRecordDto> catalogRecord = Boolean.TRUE.equals(includeCatalogRecord) ?
                            catalogReportService.generateCatalogReport(conversionResult, results, requestId) : Optional.empty();

                    ResponseEntity<?> response = getResponseEntity(
                            outputFormat, fileFormat, conversionResult, results, detailedReport, catalogRecord.get()
                    );
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}, validationResults={}, detailedReportIncluded={}",
                            requestId, fileFormat, output, results, includeDetailedReport);
                    yield response;
                }
                case XLSX -> {
                    log.debug("Processing XLSX file: requestId={}", requestId);
                    ConversionResult conversionResult = converterService.processExcelFile(file);

                    ISMDValidationReport report = validationService.validate(conversionResult.getTransformationResult());
                    ValidationResultsDto results = convertReportToDto(report);
                    DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                            generateDetailedValidationReport(report, conversionResult.getTransformationResult().getOntModel(), requestId) : null;

                    Optional<CatalogRecordDto> catalogRecord = Boolean.TRUE.equals(includeCatalogRecord) ?
                            catalogReportService.generateCatalogReport(conversionResult, results, requestId) : Optional.empty();

                    ResponseEntity<?> response = getResponseEntity(
                            outputFormat, fileFormat, conversionResult, results, detailedReport, catalogRecord.get()
                    );
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}, validationResults={}, detailedReportIncluded={}",
                            requestId, fileFormat, output, results, includeDetailedReport);
                    yield response;
                }
                case TURTLE -> {
                    log.debug("Processing TTL file: requestId={}", requestId);

                    ISMDValidationReport report = validationService.validateTtlFile(processedFile);
                    ValidationResultsDto results = convertReportToDto(report);
                    DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                            generateDetailedValidationReportFromTtl(report, processedFile, requestId) : null;

                    Optional<CatalogRecordDto> catalogRecord = Boolean.TRUE.equals(includeCatalogRecord) ?
                            catalogReportService.generateCatalogReportFromFile(processedFile, results, requestId) : Optional.empty();

                    yield ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(ConversionResponseDto.success(null, results, detailedReport, catalogRecord.get()));
                }
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

    @Operation(
            summary = "Konverze slovníku vyhledaného dle IRI do formátu dle OFN a jeho export dle parametrů.",
            description = "Konverze slovníku vyhledaného přes SPARQL do formátu dle OFN a jeho export dle parametrů. Podporuje vyhedávání dle IRI. Formát výstupu konverze podporuje json-ld a ttl. Podporuje validaci dle SHACL. Podporuje generaci výpisu z validace a generaci nekompletního katalogizačního záznamu do NKD."
    )
    @PostMapping("/ssp/convert")
    public ResponseEntity<?> convertSSPFromIRI(
            @RequestParam(value = "iri") String iri,
            @RequestParam(value = "output", required = false) String output,
            @RequestParam(value = "includeDetailedReport", required = false, defaultValue = "true") Boolean includeDetailedReport,
            @RequestParam(value = "includeCatalogRecord", required = false, defaultValue = "true") Boolean includeCatalogRecord,
            @RequestHeader(value = "Accept", required = false) String acceptHeader
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        String outputFormat = determineOutputFormat(output, acceptHeader);

        try {
            if (iri.isEmpty()) {
                log.warn("Empty IRI conversion attempt");
                return ResponseEntity.badRequest().body(
                        ConversionResponseDto.error("Nebylo vloženo IRI slovníku určeného k převodu.")
                );
            }
            ConversionResult conversionResult = converterService.processSSPOntology(iri);

            ISMDValidationReport report = validationService.validate(conversionResult.getTransformationResult());
            ValidationResultsDto results = convertReportToDto(report);
            DetailedValidationReportDto detailedReport = Boolean.TRUE.equals(includeDetailedReport) ?
                    generateDetailedValidationReport(report, conversionResult.getTransformationResult().getOntModel(), requestId) : null;

            Optional<CatalogRecordDto> catalogRecord = Boolean.TRUE.equals(includeCatalogRecord) ?
                    catalogReportService.generateCatalogReport(conversionResult, results, requestId) : Optional.empty();

            ResponseEntity<?> response = getResponseEntity(outputFormat, SSP, conversionResult, results, detailedReport, catalogRecord.get());
            log.info("SSP ontology successfully converted: requestId={}, inputFormat={}, outputFormat={}",
                    requestId, SSP, output);
            return response;
        } catch (UnsupportedFormatException e) {
            log.error("Unsupported format exception: requestId={}, message={}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(ConversionResponseDto.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing SSP ontology conversion: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ConversionResponseDto.error(e.getMessage()));
        } finally {
            MDC.remove(LOG_REQUEST_ID);
        }
    }

    @Operation(
            summary = "Stažení validační zprávy.",
            description = "Stažení validační zprávy z kontroly slovníku dle SHACL. Soubor je ve formátu csv."
    )
    @PostMapping("/convert/detailed-report/csv")
    public ResponseEntity<byte[]> downloadDetailedValidationReportCSV(
            @RequestPart (value = "detailedReport") DetailedValidationReportDto detailedReport,
            @RequestParam(value = "filename", required = false, defaultValue = "validation-report") String filename
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        log.info("CSV download requested from existing conversion response, filename={}", filename);

        try {
            if (detailedReport == null) {
                log.warn("No detailed validation report found in conversion response: requestId={}", requestId);
                return ResponseEntity.badRequest()
                        .body("No detailed validation report available. Please ensure the conversion was performed with includeDetailedReport=true.".getBytes(StandardCharsets.UTF_8));
            }
            String csvContent = detailedValidationReportService.generateCSV(detailedReport);

            byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String finalFilename = filename + "_" + timestamp + ".csv";

            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", finalFilename);
            headers.setContentLength(csvBytes.length);

            headers.add("Content-Transfer-Encoding", "binary");
            headers.add("Accept-Ranges", "bytes");

            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);

            log.info("CSV report generated successfully: requestId={}, filename={}, size={} bytes",
                    requestId, finalFilename, csvBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvBytes);
        } catch (Exception e) {
            log.error("Error generating CSV from conversion response: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Failed to generate CSV report: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } finally {
            MDC.remove(LOG_REQUEST_ID);
        }
    }

    @Operation(
            summary = "Stažení nekompletního katalogizačního záznamu.",
            description = "Stažení nekompletního katalogizačního záznamu pro nahrání do NKD. Soubor je ve formátu json."
    )
    @PostMapping("/convert/catalog-record/json")
    public ResponseEntity<String> downloadCatalogRecordJSON(
            @RequestPart (value = "catalogRecord") CatalogRecordDto catalogRecord,
            @RequestParam(value = "filename", required = false, defaultValue = "catalog-record") String filename
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        log.info("Catalog record download requested from existing conversion response, filename={}", filename);

        try {
            if (catalogRecord == null) {
                log.warn("No catalog record found in conversion response: requestId={}", requestId);
                return ResponseEntity.badRequest()
                        .body("No catalog record available. Please ensure the conversion was performed with includeCatalogRecord=true and that validation results contain no ERROR severity findings.");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            String jsonContent = objectMapper.writeValueAsString(catalogRecord);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String finalFilename = filename + "_" + timestamp + ".json";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));
            headers.setContentDispositionFormData("attachment", finalFilename);
            headers.add("Content-Length", String.valueOf(jsonContent.getBytes(StandardCharsets.UTF_8).length));

            log.info("Catalog record generated successfully from existing data: requestId={}, filename={}, recordIri={}",
                    requestId, finalFilename, catalogRecord.getIri());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(jsonContent);

        } catch (Exception e) {
            log.error("Error generating catalog record from conversion response: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate catalog record: " + e.getMessage());
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
            return XLSX;
        } else if (checkForXmlOrXmi(file) == ARCHI_XML) {
            log.debug("Archi XML format detected: filename={}", filename);
            return ARCHI_XML;
        } else if (checkForXmlOrXmi(file) == XMI) {
            log.debug("XMI format detected: filename={}", filename);
            return XMI;
        } else if (checkForTurtle(file) == TURTLE) {
            log.debug("Turtle format detected: filename={}", filename);
            return TURTLE;
        }

        log.debug("Unsupported format detected: filename={}, contentType={}",
                filename, file.getContentType());
        return UNSUPPORTED;
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
        return UNSUPPORTED;
    }

    private FileFormat checkForTurtle(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.equals("text/turtle")) {
            return TURTLE;
        }
        String fileName = file.getOriginalFilename();

        if (Objects.requireNonNull(fileName).endsWith(".ttl")) {
            return TURTLE;
        }

        return UNSUPPORTED;
    }

    private ResponseEntity<?> getResponseEntity(
            String outputFormat, FileFormat fileFormat, ConversionResult conversionResult,
            ValidationResultsDto results, DetailedValidationReportDto detailedReport,
            CatalogRecordDto catalogRecord) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.debug("Preparing response entity: requestId={}, outputFormat={}", requestId, outputFormat);

        return switch (outputFormat.toLowerCase()) {
            case "json" -> {
                log.debug("Exporting to JSON: requestId={}", requestId);
                String jsonOutput = converterService.exportToJson(fileFormat, conversionResult.getTransformationResult());
                log.debug("JSON export completed: requestId={}, outputSize={}", requestId, jsonOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ConversionResponseDto.success(jsonOutput, results, detailedReport, catalogRecord).getOutput());
            }
            case "ttl" -> {
                log.debug("Exporting to Turtle: requestId={}", requestId);
                String ttlOutput = converterService.exportToTurtle(fileFormat, conversionResult.getTransformationResult());
                log.debug("Turtle export completed: requestId={}, outputSize={}", requestId, ttlOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ConversionResponseDto.success(ttlOutput, results, detailedReport, catalogRecord).getOutput());
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

    private ValidationResultsDto convertReportToDto(ISMDValidationReport report) {
        try {
            return validationReportService.convertToDto(report);
        } catch (Exception e) {
            return null;
        }
    }

    private DetailedValidationReportDto generateDetailedValidationReport(ISMDValidationReport report, OntModel model, String requestId) {
        try {
            log.debug("Generating detailed validation report: requestId={}", requestId);

            DetailedValidationReportDto detailedReport = detailedValidationReportService.generateDetailedReport(
                    report,
                    model
            );

            log.debug("Detailed validation report generated successfully: requestId={}, concepts={}",
                    requestId, detailedReport.validation().size());

            return detailedReport;

        } catch (Exception e) {
            log.error("Failed to generate detailed validation report: requestId={}", requestId, e);
            return null;
        }
    }

    private DetailedValidationReportDto generateDetailedValidationReportFromTtl(ISMDValidationReport report, MultipartFile file, String requestId) {
        try {
            log.debug("Generating detailed validation report: requestId={}", requestId);

            DetailedValidationReportDto detailedReport = detailedValidationReportService.generateDetailedReportFromTtlFile(
                    report,
                    file
            );

            log.debug("Detailed validation report generated successfully: requestId={}, concepts={}",
                    requestId, detailedReport.validation().size());

            return detailedReport;
        } catch (Exception e) {
            log.error("Failed to generate detailed validation report: requestId={}", requestId, e);
            return null;
        }
    }

    private MultipartFile downloadAsMultipartFile(String fileUrl) throws Exception {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(DOWNLOAD_TIMEOUT)
                    .build();
        }

        URL url = UtilityMethods.validateUrl(fileUrl, ALLOWED_PROTOCOLS);

        performSecurityChecks(url);

        log.info("Downloading file from URL: {}", fileUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url.toURI())
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new SecurityException("Chyba při stahování souboru z URL. HTTP " + response.statusCode());
        }

        byte[] fileContent = response.body();

        if (fileContent.length > MAX_FILE_SIZE) {
            throw new SecurityException("Stažený soubor je příliš velký. Velikost: " + fileContent.length +
                    " bytů, maximální povolená velikost: " + MAX_FILE_SIZE + " bytů.");
        }

        if (fileContent.length == 0) {
            throw new SecurityException("Stažený soubor je prázdný.");
        }

        String filename = UtilityMethods.extractFilenameFromUrl(url);

        log.info("Soubor úspěšně stažen: název={}, velikost={}", filename, fileContent.length);

        return new UrlMultipartFile(fileContent, filename, UtilityMethods.extractContentType(response, filename));
    }

    private void performSecurityChecks(URL url) throws SecurityException, UnknownHostException {
        InetAddress address = InetAddress.getByName(url.getHost());
        String hostAddress = address.getHostAddress();

        if (PRIVATE_IP_PATTERN.matcher(hostAddress).find()) {
            throw new SecurityException("Přístup k privátním IP adresám není povolen.");
        }

        if ("localhost".equalsIgnoreCase(url.getHost()) ||
                "127.0.0.1".equals(url.getHost()) ||
                "0.0.0.0".equals(url.getHost())) {
            throw new SecurityException("Přístup k localhost není povolen.");
        }

        int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
        if (port != 80 && port != 443) {
            log.warn("Nestandardní port detekován: {}", port);
        }
    }
}