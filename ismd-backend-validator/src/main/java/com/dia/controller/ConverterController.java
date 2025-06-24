package com.dia.controller;

import com.dia.controller.dto.ConversionResponseDto;
import com.dia.enums.FileFormat;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.UnsupportedFormatException;
import com.dia.service.ConverterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dia.constants.ConverterControllerConstants.*;
import static com.dia.enums.FileFormat.*;

@RestController
@RequestMapping("/api/convertor")
@RequiredArgsConstructor
@Slf4j
public class ConverterController {

    private static final long MAX_FILE_SIZE = 5242880;

    private final ConverterService converterService;

    @PostMapping("/convert")
    public ResponseEntity<?> convertFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "output", required = false) String output,
            @RequestParam(value = "removeInvalidSources", required = false) Boolean removeInvalidSources,
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            HttpServletRequest request
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        String outputFormat = determineOutputFormat(output, acceptHeader);

        log.info("File conversion requested: filename={}, size={}, outputFormat={}, remove invalid sources={}",
                file.getOriginalFilename(), file.getSize(), output, removeInvalidSources);

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
                    converterService.parseArchiFromString(xmlContent);
                    converterService.convertArchi(removeInvalidSources != null && removeInvalidSources);
                    ResponseEntity<?> response = getResponseEntity(outputFormat, ARCHI_XML);
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}",
                            requestId, fileFormat, output);
                    yield response;
                }
                case XMI -> {
                    log.debug("Processing XMI file: requestId={}", requestId);
                    converterService.parseEAFromFile(file);
                    converterService.convertEA(removeInvalidSources != null && removeInvalidSources);
                    ResponseEntity<?> response = getResponseEntity(outputFormat, XMI);
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}",
                            requestId, fileFormat, output);
                    yield response;
                }
                case XLSX -> {
                    log.debug("Processing XLSX file: requestId={}", requestId);
                    converterService.parseExcelFromFile(file);
                    converterService.convertExcel(removeInvalidSources != null && removeInvalidSources);
                    ResponseEntity<?> response = getResponseEntity(outputFormat, XLSX);
                    log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}",
                            requestId, fileFormat, output);
                    yield  response;
                }
                case TURTLE -> ResponseEntity.ok(ConversionResponseDto.success("File processed successfully"));
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

    private ResponseEntity<?> getResponseEntity(
            @RequestParam(value = "output", defaultValue = "json") String output, FileFormat fileFormat) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.debug("Preparing response entity: requestId={}, outputFormat={}", requestId, output);

        return switch (output.toLowerCase()) {
            case "json" -> {
                log.debug("Exporting to JSON: requestId={}", requestId);
                String jsonOutput = converterService.exportToJson(fileFormat);
                log.debug("JSON export completed: requestId={}, outputSize={}", requestId, jsonOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ConversionResponseDto.success(jsonOutput).getOutput());
            }
            case "ttl" -> {
                log.debug("Exporting to Turtle: requestId={}", requestId);
                String ttlOutput = converterService.exportToTurtle(fileFormat);
                log.debug("Turtle export completed: requestId={}, outputSize={}", requestId, ttlOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ConversionResponseDto.success(ttlOutput).getOutput());
            }
            default -> {
                log.warn("Unsupported output format requested: requestId={}, format={}", requestId, output);
                throw new UnsupportedFormatException("Nepodporovaný výstupní formát: " + output);
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
}