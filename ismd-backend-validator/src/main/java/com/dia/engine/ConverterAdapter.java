package com.dia.engine;

import com.dia.converter.data.TransformationResult;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;

public interface ConverterAdapter {
    String exportToJson(TransformationResult transformationResult) throws JsonExportException;

    String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException;
}
