package com.dia.exceptions;

public class ExcelReadingException extends Exception {
    public ExcelReadingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExcelReadingException(String message) {
        super(message);
    }
}
