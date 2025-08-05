package com.dia.service.impl;

import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.TransformationResult;
import com.dia.conversion.engine.ConverterEngine;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import com.dia.service.ConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ConverterServiceImpl implements ConverterService {

    private final ConverterEngine converterEngine;

    @Override
    public ConversionResult processArchiFile(String content, Boolean removeInvalidSources) throws FileParsingException, ConversionException {
        return converterEngine.processArchiFile(content, removeInvalidSources);
    }

    @Override
    public ConversionResult processExcelFile(MultipartFile file, Boolean removeInvalidSources) throws ExcelReadingException, IOException, ConversionException {
        return converterEngine.processExcelFile(file, removeInvalidSources);
    }

    @Override
    public ConversionResult processEAFile(MultipartFile file, Boolean removeInvalidSources) throws FileParsingException, IOException, ConversionException {
        return converterEngine.processEAFile(file, removeInvalidSources);
    }

    @Override
    public ConversionResult processSSPOntology(String iri, Boolean removeInvalidSources) throws ConversionException {
        return converterEngine.processSSPOntology(iri, removeInvalidSources);
    }

    @Override
    public String exportToJson(FileFormat fileFormat, TransformationResult transformationResult) throws JsonExportException {
        return converterEngine.exportToJson(fileFormat, transformationResult);
    }

    @Override
    public String exportToTurtle(FileFormat fileFormat, TransformationResult transformationResult) throws TurtleExportException {
        return converterEngine.exportToTurtle(fileFormat, transformationResult);
    }
}