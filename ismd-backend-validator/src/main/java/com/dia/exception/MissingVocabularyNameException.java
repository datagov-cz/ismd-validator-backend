package com.dia.exception;

/** The input vocabulary has no name, so it cannot be converted: the request is invalid. */
public class MissingVocabularyNameException extends ConversionException {
    public MissingVocabularyNameException(String message) {
        super(message);
    }
}
