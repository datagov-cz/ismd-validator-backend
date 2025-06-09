package com.dia.service.impl;

import com.dia.engine.ConverterEngine;
import com.dia.exceptions.*;
import com.dia.service.ConverterService;
import lombok.RequiredArgsConstructor;
import org.apache.jena.ontology.ConversionException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConverterServiceImpl implements ConverterService {

    private final ConverterEngine converterEngine;

    @Override
    public void parseArchiFromString(String content) throws FileParsingException {
        converterEngine.parseArchiFromString(content);
    }

    @Override
    public void convertArchi(boolean removeInvalidSources) throws ConversionException {
        converterEngine.convertArchi(removeInvalidSources);
    }

    @Override
    public String exportArchiToJson() throws JsonExportException {
        return converterEngine.exportToJson();
    }

    @Override
    public String exportArchiToTurtle() throws TurtleExportException {
        return converterEngine.exportToTurtle();
    }
}
