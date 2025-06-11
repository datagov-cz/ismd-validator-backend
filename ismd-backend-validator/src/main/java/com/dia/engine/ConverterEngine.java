package com.dia.engine;

import com.dia.converter.archi.ArchiConverter;
import com.dia.converter.excel.data.OntologyData;
import com.dia.converter.excel.reader.ExcelReader;
import com.dia.converter.excel.transformer.ExcelDataTransformer;
import com.dia.converter.excel.transformer.TransformationResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static com.dia.constants.ConvertorControllerConstants.LOG_REQUEST_ID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConverterEngine {

    private final ArchiConverter archiConverter;
    private final ExcelReader excelReader;
    private final ExcelDataTransformer excelDataTransformer;

    private TransformationResult excelTransformationResult;
    private OntologyData  excelOntologyData;

    private final Map<FileFormat, ConverterAdapter> converterRegistry = Map.of(
            FileFormat.ARCHI_XML, new ConverterAdapter() {
                @Override
                public String exportToJson() throws JsonExportException {
                    return archiConverter.exportToJson();
                }

                @Override
                public String exportToTurtle() throws TurtleExportException {
                    return archiConverter.exportToTurtle();
                }
            },
            FileFormat.XLSX, new ConverterAdapter() {
                @Override
                public String exportToJson() throws JsonExportException {
                    if (excelTransformationResult == null) {
                        throw new JsonExportException("Excel transformation result is not available.");
                    }
                    return excelDataTransformer.exportToJson(excelTransformationResult);
                }

                @Override
                public String exportToTurtle() throws TurtleExportException {
                    if (excelTransformationResult == null) {
                        throw new TurtleExportException("Excel transformation result is not available.");
                    }
                    return excelDataTransformer.exportToTurtle(excelTransformationResult);
                }
            }
    );

    public void parseArchiFromString(String content) throws FileParsingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        int contentLength = content != null ? content.length() : 0;

        log.info("Starting Archi XML parsing: requestId={}, contentLength={}",
                requestId, contentLength);

        try {
            long startTime = System.currentTimeMillis();
            archiConverter.parseFromString(content);
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

    public void convertArchi(Boolean removeInvalidSources) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi model conversion: requestId={}", requestId);
        log.info("Invalid sources removal requested: {}, requestId={}", removeInvalidSources, requestId);

        try {
            long startTime = System.currentTimeMillis();
            archiConverter.setRemoveELI(removeInvalidSources);
            archiConverter.convert();
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

    public String exportToJson(FileFormat fileFormat) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting JSON export using registry: requestId={}, fileFormat={}", requestId, fileFormat);

        try {
            ConverterAdapter adapter = converterRegistry.get(fileFormat);
            if (adapter == null) {
                throw new JsonExportException("Unsupported file format for JSON export: " + fileFormat);
            }

            long startTime = System.currentTimeMillis();
            String result = adapter.exportToJson();
            long duration = System.currentTimeMillis() - startTime;

            log.info("JSON export completed using registry: requestId={}, fileFormat={}, outputSize={}, durationMs={}",
                    requestId, fileFormat, result.length(), duration);
            return result;

        } catch (JsonExportException e) {
            log.error("Failed to export to JSON using registry: requestId={}, fileFormat={}, error={}",
                    requestId, fileFormat, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during JSON export using registry: requestId={}, fileFormat={}",
                    requestId, fileFormat, e);
            throw new JsonExportException("Během exportu do JSON došlo k nečekané chybě.", e);
        }
    }

    public String exportToTurtle(FileFormat fileFormat) throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export: requestId={}", requestId);

        try {
            ConverterAdapter adapter = converterRegistry.get(fileFormat);
            if (adapter == null) {
                throw new TurtleExportException("Unsupported file format for Turtle export: " + fileFormat);
            }

            long startTime = System.currentTimeMillis();
            String result = adapter.exportToTurtle();
            long duration = System.currentTimeMillis() - startTime;

            log.info("Turtle export completed using registry: requestId={}, fileFormat={}, outputSize={}, durationMs={}",
                    requestId, fileFormat, result.length(), duration);
            return result;

        } catch (TurtleExportException e) {
            log.error("Failed to export to Turtle using registry: requestId={}, fileFormat={}, error={}",
                    requestId, fileFormat, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Turtle export using registry: requestId={}, fileFormat={}",
                    requestId, fileFormat, e);
            throw new TurtleExportException("Během exportu do Turtle došlo k nečekané chybě.", e);
        }
    }

    public void parseExcelFromFile(MultipartFile file) throws IOException, ExcelReadingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Excel file parsing: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            excelOntologyData = excelReader.readOntologyFromExcel(file.getInputStream());
            long duration = System.currentTimeMillis() - startTime;
            log.info("Excel file parsing completed: requestId={}, durationMs={}",
                    requestId, duration);
        } catch (IOException e) {
            log.error("Failed to parse Excel file: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            throw e;
        } catch (ExcelReadingException e) {
            log.error("Unexpected error during Excel file parsing: requestId={}",
                    requestId, e);
            throw new ExcelReadingException("Během čtení souboru došlo k nečekané chybě.", e);
        }
    }

    public void convertExcel(Boolean removeInvalidSources) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Excel model conversion: requestId={}", requestId);
        log.info("Invalid sources removal requested: {}, requestId={}", removeInvalidSources, requestId);
        try {
            long startTime = System.currentTimeMillis();
            excelDataTransformer.setRemoveELI(removeInvalidSources);
            excelTransformationResult = excelDataTransformer.transform(excelOntologyData);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Excel model conversion completed: requestId={}, durationMs={}",
                    requestId, duration);
        } catch (ConversionException e) {
            log.error("Failed to convert Excel model: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Excel model conversion: requestId={}",
                    requestId, e);
            throw new ConversionException("Během konverze Excel souboru došlo k nečekané chybě.");
        }
    }
}
