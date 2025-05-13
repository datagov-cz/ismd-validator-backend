package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.ConversionException;
import dia.ismd.common.exceptions.FileParsingException;
import dia.ismd.common.exceptions.JsonExportException;
import dia.ismd.common.exceptions.TurtleExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import static dia.ismd.validator.constants.ConvertorControllerConstants.*;

@Component
@RequiredArgsConstructor
@Slf4j
class ConvertorEngine {

    private final ArchiConvertor archiConvertor;

    public void parseArchiFromString(String content) throws FileParsingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        int contentLength = content != null ? content.length() : 0;

        log.info("Starting Archi XML parsing: requestId={}, contentLength={}",
                requestId, contentLength);

        try {
            long startTime = System.currentTimeMillis();
            archiConvertor.parseFromString(content);
            long duration = System.currentTimeMillis() - startTime;

            log.info("Archi XML parsing completed: requestId={}, durationMs={}",
                    requestId, duration);
        } catch (FileParsingException e) {
            log.error("Failed to parse Archi XML: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Archi XML parsing: requestId={}",
                    requestId, e);
            throw new FileParsingException("Během čtení souboru došlo k nečekané chybě.", e);
        }
    }

    public void convertArchi() throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi model conversion: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            archiConvertor.convert();
            long duration = System.currentTimeMillis() - startTime;

            log.info("Archi model conversion completed: requestId={}, durationMs={}",
                    requestId, duration);
        } catch (ConversionException e) {
            log.error("Failed to convert Archi model: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Archi model conversion: requestId={}",
                    requestId, e);
            throw new ConversionException("Během konverze Archi souboru došlo k nečekané chybě.");
        }
    }

    public String exportToJson() throws JsonExportException {
        String requestId = MDC.get("requestId");
        log.info("Starting JSON export: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            String result = archiConvertor.exportToJson();
            long duration = System.currentTimeMillis() - startTime;

            log.info("JSON export completed: requestId={}, outputSize={}, durationMs={}",
                    requestId, result.length(), duration);
            return result;
        } catch (JsonExportException e) {
            log.error("Failed to export to JSON: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during JSON export: requestId={}",
                    requestId, e);
            throw new JsonExportException("Během exportu do JSON došlo k nečekané chybě", e);
        }
    }

    public String exportToTurtle() throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            String result = archiConvertor.exportToTurtle();
            long duration = System.currentTimeMillis() - startTime;

            log.info("Turtle export completed: requestId={}, outputSize={}, durationMs={}",
                    requestId, result.length(), duration);
            return result;
        } catch (TurtleExportException e) {
            log.error("Failed to export to Turtle: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Turtle export: requestId={}",
                    requestId, e);
            throw new TurtleExportException("Během exportu do Turtle došlo k nečekané chybě.", e);
        }
    }
}
