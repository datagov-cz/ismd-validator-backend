package com.dia.service;

import com.dia.exceptions.ConversionException;
import com.dia.exceptions.FileParsingException;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;

public interface ConverterService {
    void parseArchiFromString(String value) throws FileParsingException;

    void convertArchi(Boolean removeInvalidSources) throws ConversionException;

    String exportArchiToJson() throws JsonExportException;

    String exportArchiToTurtle() throws TurtleExportException;
}
