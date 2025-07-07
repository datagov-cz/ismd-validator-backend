package com.dia.service;

import com.dia.converter.data.ConversionResult;
import com.dia.converter.data.TransformationResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ConverterService {

    ConversionResult processArchiFile(String content, Boolean removeInvalidSources) throws FileParsingException, ConversionException;

    ConversionResult processEAFile(MultipartFile file, Boolean removeInvalidSources) throws FileParsingException, IOException, ConversionException;

    ConversionResult processExcelFile(MultipartFile file, Boolean removeInvalidSources) throws ExcelReadingException, IOException, ConversionException;

    String exportToJson(FileFormat fileFormat, TransformationResult transformationResult) throws JsonExportException;

    String exportToTurtle(FileFormat fileFormat, TransformationResult transformationResult) throws TurtleExportException;
}
