package com.dia.engine;

import com.dia.converter.reader.ea.EnterpriseArchitectReader;
import com.dia.converter.data.OntologyData;
import com.dia.converter.reader.excel.ExcelReader;
import com.dia.converter.transformer.OFNDataTransformer;
import com.dia.converter.transformer.TransformationResult;
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

    private final OFNDataTransformer ofnDataTransformer;
    private final ArchiReader archiReader;
    private final EnterpriseArchitectReader eaReader;
    private final ExcelReader excelReader;

    private OntologyData archiOntologyData;
    private OntologyData eaOntologyData;
    private OntologyData excelOntologyData;

    private TransformationResult archiTransformationResult;
    private TransformationResult eaTransformationResult;
    private TransformationResult excelTransformationResult;

    private final Map<FileFormat, ConverterAdapter> converterRegistry = Map.of(
            FileFormat.ARCHI_XML, new ConverterAdapter() {
                @Override
                public String exportToJson() throws JsonExportException {
                    if (archiTransformationResult == null) {
                        throw new JsonExportException("Archi transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToJson(archiTransformationResult);
                }

                @Override
                public String exportToTurtle() throws TurtleExportException {
                    if (archiTransformationResult == null) {
                        throw new TurtleExportException("Archi transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToTurtle(archiTransformationResult);
                }
            },
            FileFormat.XLSX, new ConverterAdapter() {
                @Override
                public String exportToJson() throws JsonExportException {
                    if (excelTransformationResult == null) {
                        throw new JsonExportException("Excel transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToJson(excelTransformationResult);
                }

                @Override
                public String exportToTurtle() throws TurtleExportException {
                    if (excelTransformationResult == null) {
                        throw new TurtleExportException("Excel transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToTurtle(excelTransformationResult);
                }
            },
            FileFormat.XMI, new ConverterAdapter() {
                @Override
                public String exportToJson() throws JsonExportException {
                    if (eaTransformationResult == null) {
                        throw new JsonExportException("EA transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToJson(eaTransformationResult);
                }

                @Override
                public String exportToTurtle() throws TurtleExportException {
                    if (eaTransformationResult == null) {
                        throw new TurtleExportException("EA transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToTurtle(eaTransformationResult);
                }
            }
    );

    public void parseArchiFromString(String content) throws FileParsingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi file parsing: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            archiOntologyData = archiReader.readArchiFromString(content);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Archi file parsing completed: requestId={}, durationMs={}",
                    requestId, duration);
        } catch (FileParsingException e) {
            log.error("Failed to parse Archi file: requestId={}", requestId, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Archi file: requestId={}", requestId, e);
            throw new FileParsingException("Během čtení souboru došlo k chybě.", e);
        }
    }

    public void convertArchi(Boolean removeInvalidSources) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi model conversion: requestId={}", requestId);
        log.info("Invalid sources removal requested: {}, requestId={}", removeInvalidSources, requestId);
        try {
            long startTime = System.currentTimeMillis();
            ofnDataTransformer.setRemoveELI(removeInvalidSources);
            archiTransformationResult = ofnDataTransformer.transform(archiOntologyData);
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
            ofnDataTransformer.setRemoveELI(removeInvalidSources);
            excelTransformationResult = ofnDataTransformer.transform(excelOntologyData);
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

    public void parseEAFromFile(MultipartFile file) throws FileParsingException, IOException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting EA file parsing: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            eaOntologyData = eaReader.readXmiFromBytes(file.getBytes());
            long duration = System.currentTimeMillis() - startTime;
            log.info("EA file parsing completed: requestId={}, durationMs={}",
                    requestId, duration);
        } catch (IOException e) {
            log.error("Failed to parse EA file: requestId={}", requestId, e);
            throw e;
        } catch (FileParsingException e) {
            log.error("Failed to parse EA file: requestId={}", requestId, e);
            throw new FileParsingException("Během čtení souboru došlo k chybě.", e);
        }
    }

    public void convertEA(Boolean removeInvalidSources) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting EA model conversion: requestId={}", requestId);
        log.info("Invalid sources removal requested: {}, requestId={}", removeInvalidSources, requestId);
        try {
            long startTime = System.currentTimeMillis();
            ofnDataTransformer.setRemoveELI(removeInvalidSources);
            eaTransformationResult = ofnDataTransformer.transform(eaOntologyData);
            long duration = System.currentTimeMillis() - startTime;
            log.info("EA model conversion completed: requestId={}, durationMs={}",
                    requestId, duration);
        } catch (ConversionException e) {
            log.error("Failed to convert EA model: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during EA model conversion: requestId={}",
                    requestId, e);
            throw new ConversionException("Během konverze EnterpriseArchitect souboru došlo k nečekané chybě.");
        }
    }
}
