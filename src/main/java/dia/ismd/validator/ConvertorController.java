package dia.ismd.validator;

import dia.ismd.common.exceptions.JsonExportException;
import dia.ismd.common.exceptions.UnsupportedFormatException;
import dia.ismd.validator.convertor.ConvertorService;
import dia.ismd.validator.enums.FileFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static dia.ismd.validator.constants.ConvertorControllerConstants.*;

@RestController
@RequestMapping("/api/prevodnik")
@RequiredArgsConstructor
@Slf4j
public class ConvertorController {

    private static final long MAX_FILE_SIZE = 5242880;

    private final ConvertorService convertorService;

    @PostMapping("/prevod")
    public ResponseEntity<String> convertFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "output", defaultValue = "json", required = false) String output,
            @RequestParam(value= "removeInvalidSources", required = false) Boolean removeInvalidSources,
            @RequestHeader(value = "Accept", required = false) String acceptHeader
    ) {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LOG_REQUEST_ID, requestId);

        String outputFormat = determineOutputFormat(output, acceptHeader);

        log.info("File conversion requested: filename={}, size={}, outputFormat={}, remove invalid sources={}",
                file.getOriginalFilename(), file.getSize(), output, removeInvalidSources);

        try {
            if (file.isEmpty()) {
                log.warn("Empty file upload attempt");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Nebyl vložen žádný soubor.");
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                log.warn("File too large: filename={}, size={}, maxAllowedSize={}",
                        file.getOriginalFilename(), file.getSize(), MAX_FILE_SIZE);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body("Soubor je příliš velký. Maximální povolená velikost je 5 MB.");
            }

            FileFormat fileFormat = checkFileFormat(file);
            if (fileFormat == FileFormat.UNSUPPORTED) {
                log.warn("Unsupported file type upload attempt");
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }

            log.info("File format determined: requestId={}, format={}", requestId, fileFormat);

            switch (fileFormat) {
                case TURTLE -> {
                    log.debug("Processing Turtle file: requestId={}", requestId);
                    byte[] fileContent = file.getBytes();
                    log.info("Returning Turtle file without conversion: requestId={}", requestId);
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(new String(fileContent, StandardCharsets.UTF_8));
                }
                case ARCHI_XML -> {
                    log.debug("Processing Archi XML file: requestId={}", requestId);
                    String xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                    convertorService.parseArchiFromString(xmlContent);
                    convertorService.convertArchi(removeInvalidSources != null && removeInvalidSources);
                    log.info("Archi XML file successfully processed: requestId={}", requestId);
                }
                case XMI -> log.debug("Processing XMI file: requestId={}", requestId);
                case XLSX -> log.debug("Processing XLS file: requestId={}", requestId);
                default -> {
                    log.warn("Unhandled file format: requestId={}, format={}", requestId, fileFormat);
                    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                }
            }

            ResponseEntity<String> response = getResponseEntity(outputFormat);
            log.info("File successfully converted: requestId={}, inputFormat={}, outputFormat={}",
                    requestId, fileFormat, output);
            return response;
        } catch (UnsupportedFormatException e) {
            log.error("Unsupported format exception: requestId={}, message={}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("Error processing file conversion: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        } finally {
            MDC.remove(LOG_REQUEST_ID);
        }
    }

    private FileFormat checkFileFormat(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        log.debug("Checking file format: filename={}", filename);

        if (checkForXlsx(file)) {
            log.debug("XLSX format detected: filename={}", filename);
            return FileFormat.XLSX;
        } else if (checkForXmlOrXmi(file) == FileFormat.ARCHI_XML) {
            log.debug("Archi XML format detected: filename={}", filename);
            return FileFormat.ARCHI_XML;
        } else if (checkForXmlOrXmi(file) == FileFormat.XMI) {
            log.debug("XMI format detected: filename={}", filename);
            return FileFormat.XMI;
        } else if (checkForTurtle(file) == FileFormat.TURTLE) {
            log.debug("Turtle format detected: filename={}", filename);
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
                    return FileFormat.ARCHI_XML;
                }

                if (xmlHeader.contains(XMI_HEADER)
                        || xmlHeader.contains("XMI.version=\"2.1\"")) {
                    return FileFormat.XMI;
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

    private ResponseEntity<String> getResponseEntity(
            @RequestParam(value = "output", defaultValue = "json") String output) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.debug("Preparing response entity: requestId={}, outputFormat={}", requestId, output);

        return switch (output.toLowerCase()) {
            case "json" -> {
                log.debug("Exporting to JSON: requestId={}", requestId);
                String jsonOutput = convertorService.exportArchiToJson();
                log.debug("JSON export completed: requestId={}, outputSize={}", requestId, jsonOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonOutput);
            }
            case "ttl" -> {
                log.debug("Exporting to Turtle: requestId={}", requestId);
                String ttlOutput = convertorService.exportArchiToTurtle();
                log.debug("Turtle export completed: requestId={}, outputSize={}", requestId, ttlOutput.length());
                yield ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(ttlOutput);
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
