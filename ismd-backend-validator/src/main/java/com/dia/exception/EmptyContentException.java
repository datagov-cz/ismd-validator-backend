package com.dia.exception;

public class EmptyContentException extends RuntimeException {
    public EmptyContentException(String message) {
        super(message);
    }
}