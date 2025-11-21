package com.dia.controller.exception;

public class ValidationException extends RuntimeException {
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}