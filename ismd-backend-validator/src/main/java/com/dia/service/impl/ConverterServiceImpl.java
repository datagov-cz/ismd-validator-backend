package com.dia.service.impl;

import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.engine.ConverterEngine;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import com.dia.service.ConverterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConverterServiceImpl implements ConverterService {

    private final ConverterEngine converterEngine;

    @Override
    public ConversionResult processArchiFile(String content) throws FileParsingException, ConversionException {
        log.info("Processing Archi XML file");
        ConversionResult result = converterEngine.processArchiFile(content);
        log.info("Archi XML file processed successfully");
        return result;
    }

    @Override
    public ConversionResult processExcelFile(MultipartFile file) throws ExcelReadingException, IOException, ConversionException {
        log.info("Processing Excel file: filename={}", file.getOriginalFilename());
        ConversionResult result = converterEngine.processExcelFile(file);
        log.info("Excel file processed successfully: filename={}", file.getOriginalFilename());
        return result;
    }

    @Override
    public ConversionResult processEAFile(MultipartFile file) throws FileParsingException, IOException, ConversionException {
        log.info("Processing Enterprise Architect XMI file: filename={}", file.getOriginalFilename());
        ConversionResult result = converterEngine.processEAFile(file);
        log.info("Enterprise Architect XMI file processed successfully: filename={}", file.getOriginalFilename());
        return result;
    }

    @Override
    public ConversionResult processSSPOntology(String iri) throws ConversionException {
        log.info("Processing SSP ontology from IRI: iri={}", iri);
        ConversionResult result = converterEngine.processSSPOntology(iri);
        log.info("SSP ontology processed successfully: iri={}", iri);
        return result;
    }

    @Override
    public String exportToJson(FileFormat fileFormat, TransformationResult transformationResult) throws JsonExportException {
        log.info("Exporting to JSON format: inputFormat={}", fileFormat);
        String result = converterEngine.exportToJson(fileFormat, transformationResult);
        log.debug("JSON export completed: size={} characters", result.length());
        return result;
    }

    @Override
    public String exportToTurtle(FileFormat fileFormat, TransformationResult transformationResult) throws TurtleExportException {
        log.info("Exporting to Turtle format: inputFormat={}", fileFormat);
        String result = converterEngine.exportToTurtle(fileFormat, transformationResult);
        log.debug("Turtle export completed: size={} characters", result.length());
        return result;
    }
}