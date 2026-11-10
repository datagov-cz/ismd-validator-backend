package com.dia.exception;

public class JsonExportException extends RuntimeException {
    public JsonExportException(String message) {
        super(message);
    }

    public JsonExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
