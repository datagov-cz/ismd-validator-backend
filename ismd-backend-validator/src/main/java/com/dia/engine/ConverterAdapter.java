package com.dia.engine;

import com.dia.exceptions.JsonExportException;
import com.dia.exceptions.TurtleExportException;

public interface ConverterAdapter {
    String exportToJson() throws JsonExportException;

    String exportToTurtle() throws TurtleExportException;
}
