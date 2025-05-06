package dia.ismd.validator;

import dia.ismd.validator.convertor.ConvertorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/prevodnik")
@RequiredArgsConstructor
public class ConvertorController {

    private final ConvertorService convertorService;

    @PostMapping("/prevod")
    public ResponseEntity<?> convertOntology(
            @RequestParam("file")MultipartFile file,
            @RequestParam(value = "output", defaultValue = "json") String output) {

        try {
            String xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);

            convertorService.parseArchiFromString(xmlContent);
            convertorService.convertArchi();

            return getResponseEntity(output);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> getResponseEntity(
            @RequestParam(value = "output", defaultValue = "json")  String output) throws JSONException {
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
                        .body(ttlOutput);
            }
            default -> ResponseEntity.badRequest().body("unsupported output");
        };
    }
}
