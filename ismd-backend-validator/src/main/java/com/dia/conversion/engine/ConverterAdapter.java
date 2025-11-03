package com.dia.conversion.engine;

import com.dia.conversion.data.TransformationResult;
import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;

public interface ConverterAdapter {
    String exportToJson(TransformationResult transformationResult) throws JsonExportException;

    String exportToTurtle(TransformationResult transformationResult) throws TurtleExportException;
}
