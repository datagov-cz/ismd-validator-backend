package com.dia.conversion.engine;

import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.reader.ssp.SSPReader;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.transformer.OFNDataTransformerV2;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static com.dia.constants.ConverterControllerConstants.LOG_REQUEST_ID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConverterEngine {

    private final OFNDataTransformerV2 ofnDataTransformer;
    private final ArchiReader archiReader;
    private final EnterpriseArchitectReader eaReader;
    private final ExcelReader excelReader;
    private final SSPReader sspReader;

    private final Map<FileFormat, ConverterAdapter> converterRegistry = Map.of(
            FileFormat.ARCHI_XML, new ConverterAdapter() {
                @Override
                public String exportToJson(TransformationResult transformationResult) throws JsonExportException {
                    if (transformationResult == null) {
                        throw new JsonExportException("Archi transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToJson(transformationResult);
                }

                @Override
                public String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException {
                    if (transformationResult == null) {
                        throw new TurtleExportException("Archi transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToTurtle(transformationResult);
                }
            },
            FileFormat.XLSX, new ConverterAdapter() {
                @Override
                public String exportToJson(TransformationResult transformationResult) throws JsonExportException {
                    if (transformationResult == null) {
                        throw new JsonExportException("Excel transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToJson(transformationResult);
                }

                @Override
                public String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException {
                    if (transformationResult == null) {
                        throw new TurtleExportException("Excel transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToTurtle(transformationResult);
                }
            },
            FileFormat.XMI, new ConverterAdapter() {
                @Override
                public String exportToJson(TransformationResult transformationResult) throws JsonExportException {
                    if (transformationResult == null) {
                        throw new JsonExportException("EA transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToJson(transformationResult);
                }

                @Override
                public String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException {
                    if (transformationResult == null) {
                        throw new TurtleExportException("EA transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToTurtle(transformationResult);
                }
            },
            FileFormat.SSP, new ConverterAdapter() {
                @Override
                public String exportToJson(TransformationResult transformationResult) throws JsonExportException {
                    if (transformationResult == null) {
                        throw new JsonExportException("SSP transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToJson(transformationResult);
                }

                @Override
                public String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException {
                    if (transformationResult == null) {
                        throw new TurtleExportException("SSP transformation result is not available.");
                    }
                    return ofnDataTransformer.exportToTurtle(transformationResult);
                }
            }
    );

    public ConversionResult processArchiFile(String content) throws FileParsingException, ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi file processing: requestId={}", requestId);

        OntologyData ontologyData = parseArchiFromString(content);
        TransformationResult transformationResult = convertArchi(ontologyData);

        return new ConversionResult(ontologyData, transformationResult);
    }

    private OntologyData parseArchiFromString(String content) throws FileParsingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi file parsing: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            OntologyData ontologyData = archiReader.readArchiFromString(content);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Archi file parsing completed: requestId={}, durationMs={}", requestId, duration);
            return ontologyData;
        } catch (Exception e) {
            log.error("Failed to parse Archi file: requestId={}", requestId, e);
            throw new FileParsingException("Během čtení souboru došlo k chybě.", e);
        }
    }

    private TransformationResult convertArchi(OntologyData ontologyData) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Archi model conversion: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            TransformationResult result = ofnDataTransformer.transform(ontologyData);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Archi model conversion completed: requestId={}, durationMs={}", requestId, duration);
            return result;
        } catch (Exception e) {
            log.error("Unexpected error during Archi model conversion: requestId={}", requestId, e);
            throw new ConversionException("Během konverze Archi souboru došlo k nečekané chybě.");
        }
    }

    public ConversionResult processExcelFile(MultipartFile file) throws ExcelReadingException, IOException, ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Excel file processing: requestId={}", requestId);

        OntologyData ontologyData = parseExcelFromFile(file);
        TransformationResult transformationResult = convertExcel(ontologyData);

        return new ConversionResult(ontologyData, transformationResult);
    }

    private OntologyData parseExcelFromFile(MultipartFile file) throws IOException, ExcelReadingException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Excel file parsing: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            OntologyData ontologyData = excelReader.readOntologyFromExcel(file.getInputStream());
            long duration = System.currentTimeMillis() - startTime;
            log.info("Excel file parsing completed: requestId={}, durationMs={}", requestId, duration);
            return ontologyData;
        } catch (IOException e) {
            log.error("Failed to parse Excel file: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Excel file parsing: requestId={}", requestId, e);
            throw new ExcelReadingException("Během čtení souboru došlo k nečekané chybě.", e);
        }
    }

    private TransformationResult convertExcel(OntologyData ontologyData) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Excel model conversion: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            TransformationResult result = ofnDataTransformer.transform(ontologyData);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Excel model conversion completed: requestId={}, durationMs={}", requestId, duration);
            return result;
        } catch (ConversionException e) {
            log.error("Failed to convert Excel model: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Excel model conversion: requestId={}", requestId, e);
            throw new ConversionException("Během konverze Excel souboru došlo k nečekané chybě.");
        }
    }

    public ConversionResult processEAFile(MultipartFile file) throws FileParsingException, IOException, ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting EA file processing: requestId={}", requestId);

        OntologyData ontologyData = parseEAFromFile(file);
        TransformationResult transformationResult = convertEA(ontologyData);

        return new ConversionResult(ontologyData, transformationResult);
    }

    private OntologyData parseEAFromFile(MultipartFile file) throws FileParsingException, IOException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting EA file parsing: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            OntologyData ontologyData = eaReader.readXmiFromBytes(file.getBytes());
            long duration = System.currentTimeMillis() - startTime;
            log.info("EA file parsing completed: requestId={}, durationMs={}", requestId, duration);
            return ontologyData;
        } catch (IOException e) {
            log.error("Failed to parse EA file: requestId={}", requestId, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse EA file: requestId={}", requestId, e);
            throw new FileParsingException("Během čtení souboru došlo k chybě.", e);
        }
    }

    private TransformationResult convertEA(OntologyData ontologyData) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting EA model conversion: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            TransformationResult result = ofnDataTransformer.transform(ontologyData);
            long duration = System.currentTimeMillis() - startTime;
            log.info("EA model conversion completed: requestId={}, durationMs={}", requestId, duration);
            return result;
        } catch (ConversionException e) {
            log.error("Failed to convert EA model: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during EA model conversion: requestId={}", requestId, e);
            throw new ConversionException("Během konverze EnterpriseArchitect souboru došlo k nečekané chybě.");
        }
    }

    public ConversionResult processSSPOntology(String iri) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting SSP ontology processing: requestId={}", requestId);

        OntologyData ontologyData = parseSSPOntology(iri);
        TransformationResult transformationResult = convertSSP(ontologyData);

        return new ConversionResult(ontologyData, transformationResult);
    }

    private OntologyData parseSSPOntology(String iri) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting SSP ontology parsing: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            OntologyData ontologyData = sspReader.readOntology(iri);
            long duration = System.currentTimeMillis() - startTime;
            log.info("SSP ontology parsing completed: requestId={}, durationMs={}", requestId, duration);
            return ontologyData;
        } catch (Exception e) {
            log.error("Failed to parse SSP ontology: requestId={}", requestId, e);
            throw new ConversionException("Během čtení slovníku došlo k chybě.", e);
        }
    }

    private TransformationResult convertSSP(OntologyData ontologyData) throws ConversionException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting SSP model conversion: requestId={}", requestId);

        try {
            long startTime = System.currentTimeMillis();
            TransformationResult result = ofnDataTransformer.transform(ontologyData);
            long duration = System.currentTimeMillis() - startTime;
            log.info("SSP model conversion completed: requestId={}, durationMs={}", requestId, duration);
            return result;
        } catch (ConversionException e) {
            log.error("Failed to convert SSP model: requestId={}, error={}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during SSP model conversion: requestId={}", requestId, e);
            throw new ConversionException("Během konverze SSP slovníku došlo k nečekané chybě.");
        }
    }

    public String exportToJson(FileFormat fileFormat, TransformationResult transformationResult) throws JsonExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting JSON export using registry: requestId={}, fileFormat={}", requestId, fileFormat);

        try {
            ConverterAdapter adapter = converterRegistry.get(fileFormat);
            if (adapter == null) {
                throw new JsonExportException("Unsupported file format for JSON export: " + fileFormat);
            }

            long startTime = System.currentTimeMillis();
            String result = adapter.exportToJson(transformationResult);
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

    public String exportToTurtle(FileFormat fileFormat, TransformationResult transformationResult) throws TurtleExportException {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.info("Starting Turtle export: requestId={}", requestId);

        try {
            ConverterAdapter adapter = converterRegistry.get(fileFormat);
            if (adapter == null) {
                throw new TurtleExportException("Unsupported file format for Turtle export: " + fileFormat);
            }

            long startTime = System.currentTimeMillis();
            String result = adapter.exportToTurtle(transformationResult);
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

    interface ConverterAdapter {
        String exportToJson(TransformationResult transformationResult) throws JsonExportException;
        String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException;
    }
}