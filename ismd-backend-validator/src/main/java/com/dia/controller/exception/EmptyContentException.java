package com.dia.controller.exception;

public class EmptyContentException extends RuntimeException {
    public EmptyContentException(String message) {
        super(message);
    }
}