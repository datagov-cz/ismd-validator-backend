package com.dia.exceptions;

public class TurtleExportException extends RuntimeException {
    public TurtleExportException(String message) {
        super(message);
    }

    public TurtleExportException(String message, Throwable cause) { super(message, cause); }
}
