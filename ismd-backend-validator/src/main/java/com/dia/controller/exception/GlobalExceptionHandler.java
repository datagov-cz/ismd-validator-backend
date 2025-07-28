package com.dia.controller.exception;

import com.dia.controller.dto.ConversionResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ConversionResponseDto> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        log.error("Unsupported Media Type: {}", e.getMessage());
        ConversionResponseDto response = new ConversionResponseDto(null, "Nepodporovaný typ požadavku (Content-Type). Požadováno multipart/form-data.", null);
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ConversionResponseDto> handleMaxSizeException(MaxUploadSizeExceededException e) {
        log.error("File size exceeds the limit: {}", e.getMessage());
        ConversionResponseDto response = new ConversionResponseDto(null, "Soubor je příliš velký. Maximální velikost je 5 MB.", null);
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ConversionResponseDto> handleValidationErrors(MethodArgumentNotValidException e) {
        log.error("Validation error: {}", e.getMessage());
        ConversionResponseDto response = new ConversionResponseDto(null, "Nepodporovaný vstupní formát.", null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ConversionResponseDto> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        ConversionResponseDto response = new ConversionResponseDto(null, "Došlo k neočekávané chybě.", null);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}