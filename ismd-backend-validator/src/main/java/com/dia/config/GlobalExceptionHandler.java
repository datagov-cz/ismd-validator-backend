package com.dia.config;

import com.dia.controller.dto.ApiResponseDto;
import com.dia.enums.ErrorCode;
import com.dia.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type: {}", e.getMessage());
        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("File upload size exceeded: {}", e.getMessage());
        return error(ErrorCode.UPLOAD_FILE_TOO_LARGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Neplatná data v požadavku.");
        log.warn("Validation failed: {}", details);
        return error(ErrorCode.REQUEST_VALIDATION_FAILED, "Neplatná data v požadavku: " + details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        log.warn("Method argument validation failed: {}", e.getMessage());
        return error(ErrorCode.REQUEST_VALIDATION_FAILED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Request body unreadable: {}", e.getMessage());
        return error(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Type mismatch for parameter '{}': value='{}'", e.getName(), e.getValue());
        return error(ErrorCode.INVALID_PARAMETER, "Parametr '" + e.getName() + "' má neplatnou hodnotu.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("Missing required parameter '{}'", e.getParameterName());
        return error(ErrorCode.MISSING_PARAMETER, "Chybí povinný parametr '" + e.getParameterName() + "'.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMissingRequestPart(MissingServletRequestPartException e) {
        log.warn("Missing request part '{}'", e.getRequestPartName());
        return error(ErrorCode.UPLOAD_FILE_MISSING, "Chybí požadovaný soubor. Ujistěte se, že jste nahrál soubor.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return error(ErrorCode.INVALID_ARGUMENT, e.getMessage());
    }

    @ExceptionHandler(UploadRequestException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUploadRequest(UploadRequestException e) {
        log.warn("Upload request rejected ({}): {}", e.getErrorCode(), e.getMessage());
        return error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(UrlDownloadException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUrlDownload(UrlDownloadException e) {
        log.warn("File download from URL rejected: {}", e.getMessage());
        return error(ErrorCode.UPLOAD_URL_REJECTED, e.getMessage());
    }

    @ExceptionHandler(UnsupportedFormatException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnsupportedFormat(UnsupportedFormatException e) {
        log.warn("Unsupported format: {}", e.getMessage());
        return error(ErrorCode.UNSUPPORTED_FORMAT, e.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidFile(InvalidFileException e) {
        log.warn("Invalid file: {}", e.getMessage());
        return error(ErrorCode.UPLOAD_FILE_INVALID, e.getMessage());
    }

    @ExceptionHandler(EmptyContentException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleEmptyContent(EmptyContentException e) {
        log.warn("Empty content: {}", e.getMessage());
        return error(ErrorCode.UPLOAD_DATA_EMPTY, e.getMessage());
    }

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidFormat(InvalidFormatException e) {
        log.warn("Invalid RDF format: {}", e.getMessage());
        return error(ErrorCode.RDF_FORMAT_INVALID, e.getMessage());
    }

    @ExceptionHandler(FileParsingException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleFileParsing(FileParsingException e) {
        log.warn("File parsing failed: {}", e.getMessage());
        return error(ErrorCode.FILE_PARSE_FAILED, e.getMessage());
    }

    @ExceptionHandler(ExcelReadingException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleExcelReading(ExcelReadingException e) {
        log.warn("Excel reading failed: {}", e.getMessage());
        return error(ErrorCode.EXCEL_READ_FAILED, e.getMessage());
    }

    @ExceptionHandler(ConversionException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleConversion(ConversionException e) {
        log.error("Conversion failed: {}", e.getMessage(), e);
        return error(ErrorCode.CONVERSION_FAILED, e.getMessage());
    }

    @ExceptionHandler(JsonExportException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleJsonExport(JsonExportException e) {
        log.error("JSON export failed: {}", e.getMessage(), e);
        return error(ErrorCode.EXPORT_FAILED, e.getMessage());
    }

    @ExceptionHandler(TurtleExportException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleTurtleExport(TurtleExportException e) {
        log.error("Turtle export failed: {}", e.getMessage(), e);
        return error(ErrorCode.EXPORT_FAILED, e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleValidation(ValidationException e) {
        log.warn("Validation failed: {}", e.getMessage());
        return error(ErrorCode.VALIDATION_FAILED, e.getMessage());
    }

    /** The output is withheld, but the results travel as data so the user can see what to fix. */
    @ExceptionHandler(ValidationReportHasErrorsException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleValidationReportHasErrors(ValidationReportHasErrorsException e) {
        log.warn("Conversion output withheld, validation report contains errors");
        return error(ErrorCode.VALIDATION_REPORT_HAS_ERRORS, e.getMessage(), e.getResults());
    }

    @ExceptionHandler(ValidationConfigurationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleValidationConfiguration(ValidationConfigurationException e) {
        log.error("Validation configuration error: {}", e.getMessage(), e);
        return error(ErrorCode.VALIDATION_CONFIGURATION_ERROR, e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIOException(IOException e) {
        log.error("IO exception: {}", e.getMessage(), e);
        return error(ErrorCode.IO_ERROR, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return error(ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ApiResponseDto<Void>> error(ErrorCode code) {
        return error(code, null, null);
    }

    private static ResponseEntity<ApiResponseDto<Void>> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    /** The status comes from the code, so the two cannot disagree. */
    private static <T> ResponseEntity<ApiResponseDto<T>> error(ErrorCode code, String message, T data) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponseDto.error(code, message, data));
    }
}