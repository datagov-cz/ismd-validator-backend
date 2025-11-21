package com.dia.controller.exception;

import com.dia.controller.dto.ConversionResponseDto;
import com.dia.controller.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ConversionResponseDto> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        log.error("Unsupported Media Type: {}", e.getMessage());
        ConversionResponseDto response = new ConversionResponseDto(null, "Nepodporovaný typ požadavku (Content-Type). Požadováno multipart/form-data.", null, null, null);
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ConversionResponseDto> handleMaxSizeException(MaxUploadSizeExceededException e) {
        log.error("File size exceeds the limit: {}", e.getMessage());
        ConversionResponseDto response = new ConversionResponseDto(null, "Soubor je příliš velký. Maximální velikost je 5 MB.", null, null, null);
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ConversionResponseDto> handleValidationErrors(MethodArgumentNotValidException e) {
        log.error("Validation error: {}", e.getMessage());
        ConversionResponseDto response = new ConversionResponseDto(null, "Nepodporovaný vstupní formát.", null, null, null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(ValidationException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Validation error [requestId={}]: {}", requestId, e.getMessage());
        ErrorResponseDto response = new ErrorResponseDto(
                "Validation Error",
                e.getMessage(),
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidFileException(InvalidFileException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Invalid file error [requestId={}]: {}", requestId, e.getMessage());
        ErrorResponseDto response = new ErrorResponseDto(
                "Invalid File",
                e.getMessage(),
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidFormatException(InvalidFormatException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Invalid format error [requestId={}]: {}", requestId, e.getMessage());
        ErrorResponseDto response = new ErrorResponseDto(
                "Invalid Format",
                e.getMessage(),
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmptyContentException.class)
    public ResponseEntity<ErrorResponseDto> handleEmptyContentException(EmptyContentException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Empty content error [requestId={}]: {}", requestId, e.getMessage());
        ErrorResponseDto response = new ErrorResponseDto(
                "Empty Content",
                e.getMessage(),
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CatalogGenerationException.class)
    public ResponseEntity<ErrorResponseDto> handleCatalogGenerationException(CatalogGenerationException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Catalog generation error [requestId={}]: {}", requestId, e.getMessage(), e);
        ErrorResponseDto response = new ErrorResponseDto(
                "Catalog Generation Error",
                e.getMessage(),
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Missing request part [requestId={}]: {}", requestId, e.getMessage());
        ErrorResponseDto response = new ErrorResponseDto(
                "Missing Request Part",
                "Chybí požadovaný soubor. Ujistěte se, že jste nahrál soubor.",
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Missing request parameter [requestId={}]: {}", requestId, e.getMessage());
        ErrorResponseDto response = new ErrorResponseDto(
                "Missing Parameter",
                "Chybí požadovaný parametr: " + e.getParameterName(),
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException e) {
        String requestId = MDC.get(LOG_REQUEST_ID);
        log.error("Illegal argument [requestId={}]: {}", requestId, e.getMessage());
        ErrorResponseDto response = new ErrorResponseDto(
                "Invalid Argument",
                e.getMessage(),
                Instant.now(),
                requestId
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ConversionResponseDto> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        ConversionResponseDto response = new ConversionResponseDto(null, "Došlo k neočekávané chybě.", null, null, null);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}