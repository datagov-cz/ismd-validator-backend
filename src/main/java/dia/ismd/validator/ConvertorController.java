package dia.ismd.validator;

import dia.ismd.common.exceptions.JsonExportException;
import dia.ismd.common.exceptions.UnsupportedFormatException;
import dia.ismd.validator.convertor.ConvertorService;
import dia.ismd.validator.enums.FileFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/prevodnik")
@RequiredArgsConstructor
public class ConvertorController {

    private final ConvertorService convertorService;

    @PostMapping("/prevod")
    public ResponseEntity<?> convertFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "output", defaultValue = "json") String output) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Nebyl vložen žádný soubor.");
            }

            FileFormat fileFormat = checkFileFormat(file);
            if (fileFormat == FileFormat.UNSUPPORTED) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }
            switch (fileFormat) {
                case ARCHI_XML -> {
                    String xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                    convertorService.parseArchiFromString(xmlContent);
                    convertorService.convertArchi();
                }
                case UNSUPPORTED -> throw new UnsupportedFormatException("Nepodporovaný formát souboru.");
                default -> {
                    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                }
            }

            return getResponseEntity(output);
        } catch (UnsupportedFormatException e) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    private FileFormat checkFileFormat(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            if (extension.equals("xlsx")) {
                return FileFormat.XLSX;
            }
        }

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

                if (xmlHeader.contains("http://www.opengroup.org/xsd/archimate/3.0/")
                        || xmlHeader.contains("http://www.opengroup.org/xsd/archimate")) {
                    return FileFormat.ARCHI_XML;
                }

                if (xmlHeader.contains("http://schema.omg.org/spec/XMI/2.1")
                        || xmlHeader.contains("XMI.version=\"2.1\"")) {
                    return FileFormat.XMI;
                }
            }
        }

        return FileFormat.UNSUPPORTED;
    }

    private ResponseEntity<?> getResponseEntity(
            @RequestParam(value = "output", defaultValue = "json") String output) throws JsonExportException {
        return switch (output.toLowerCase()) {
            case "json" -> {
                String jsonOutput = convertorService.exportArchiToJson();
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonOutput);
            }
            case "ttl" -> {
                String ttlOutput = convertorService.exportArchiToTurtle();
                yield ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(ttlOutput);
            }
            default -> throw new UnsupportedFormatException("Nepodporovaný výstupní formát: " + output);
        };
    }
}
