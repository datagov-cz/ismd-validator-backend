package com.dia.service;

import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ConverterService {
    void parseArchiFromString(String value) throws FileParsingException;

    void convertArchi(boolean removeInvalidSources) throws ConversionException;

    String exportToJson(FileFormat fileFormat) throws JsonExportException;

    String exportToTurtle(FileFormat fileFormat) throws TurtleExportException;

    void parseExcelFromFile(MultipartFile file) throws FileParsingException, IOException, ExcelReadingException;

    void convertExcel(boolean removeInvalidSources) throws ConversionException;
}
