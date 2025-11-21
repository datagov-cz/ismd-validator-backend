package com.dia.controller.exception;

public class InvalidFormatException extends RuntimeException {
    public InvalidFormatException(String message) {
        super(message);
    }
}