package com.dia.service.impl;

import com.dia.engine.ConverterEngine;
import com.dia.enums.FileFormat;
import com.dia.exceptions.ExcelReadingException;
import com.dia.exceptions.FileParsingException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;
import com.dia.service.ConverterService;
import lombok.RequiredArgsConstructor;
import org.apache.jena.ontology.ConversionException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ConverterServiceImpl implements ConverterService {

    private final ConverterEngine converterEngine;

    @Override
    public void parseArchiFromString(String content) throws FileParsingException {
        converterEngine.parseArchiFromString(content);
    }

    public void convertArchi(Boolean removeInvalidSources) throws ConversionException {
        converterEngine.convertArchi(removeInvalidSources);
    }

    @Override
    public String exportToJson(FileFormat fileFormat) throws JsonExportException {
        return converterEngine.exportToJson(fileFormat);
    }

    @Override
    public String exportToTurtle(FileFormat fileFormat) throws TurtleExportException {
        return converterEngine.exportToTurtle(fileFormat);
    }

    @Override
    public void parseExcelFromFile(MultipartFile file) throws ExcelReadingException, IOException {
        converterEngine.parseExcelFromFile(file);
    }

    @Override
    public void convertExcel(Boolean removeInvalidSources) throws com.dia.exceptions.ConversionException {
        converterEngine.convertExcel(removeInvalidSources);
    }
}
