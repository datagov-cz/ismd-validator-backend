package com.dia.service;

import com.dia.exceptions.*;

public interface ConverterService {
    void parseArchiFromString(String value) throws FileParsingException;

    void convertArchi(boolean removeInvalidSources) throws ConversionException;

    String exportArchiToJson() throws JsonExportException;

    String exportArchiToTurtle() throws TurtleExportException;
}
