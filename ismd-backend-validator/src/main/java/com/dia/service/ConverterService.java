package com.dia.service;

import com.dia.conversion.data.ConversionResult;
import com.dia.conversion.data.TransformationResult;
import com.dia.enums.FileFormat;
import com.dia.exceptions.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ConverterService {

    ConversionResult processArchiFile(String content) throws FileParsingException, ConversionException;

    ConversionResult processEAFile(MultipartFile file) throws FileParsingException, IOException, ConversionException;

    ConversionResult processExcelFile(MultipartFile file) throws ExcelReadingException, IOException, ConversionException;

    ConversionResult processSSPOntology(String iri) throws ConversionException;

    String exportToJson(FileFormat fileFormat, TransformationResult transformationResult) throws JsonExportException;

    String exportToTurtle(FileFormat fileFormat, TransformationResult transformationResult) throws TurtleExportException;
}