package com.dia.conversion.engine;

import com.dia.conversion.data.TransformationResult;
import com.dia.exception.JsonExportException;
import com.dia.exception.TurtleExportException;

public interface ConverterAdapter {
    String exportToJson(TransformationResult transformationResult) throws JsonExportException;

    String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException;
}
