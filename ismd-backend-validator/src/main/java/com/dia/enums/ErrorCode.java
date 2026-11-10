package com.dia.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Every error the API returns: the stable code the FE branches on, the HTTP status it travels with, and
 * the message shown when the failure carries no more specific one. The constant name is the wire value.
 */
@Getter
public enum ErrorCode {

    // Request shape
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Požadavek má neplatný formát."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Chybí povinný parametr."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Parametr má neplatnou hodnotu."),
    REQUEST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Neplatná data v požadavku."),
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "Požadavek obsahuje neplatnou hodnotu."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Nepodporovaný typ požadavku (Content-Type). Požadováno multipart/form-data."),

    // Generic
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Nastala neočekávaná chyba."),
    IO_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Při čtení nebo zápisu dat došlo k chybě."),

    // Upload
    UPLOAD_FILE_MISSING(HttpStatus.BAD_REQUEST, "Nebyl vložen žádný soubor."),
    UPLOAD_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Nahraný soubor překračuje maximální povolenou velikost."),
    UPLOAD_SOURCE_AMBIGUOUS(HttpStatus.BAD_REQUEST, "Můžete zvolit pouze jeden způsob nahrání slovníků."),
    UPLOAD_MULTIPLE_FILES(HttpStatus.BAD_REQUEST, "Můžete nahrát pouze jeden soubor."),
    UPLOAD_FILE_INVALID(HttpStatus.BAD_REQUEST, "Nahraný soubor je neplatný."),
    UPLOAD_DATA_EMPTY(HttpStatus.BAD_REQUEST, "Nahraný soubor neobsahuje žádná data."),
    UPLOAD_URL_REJECTED(HttpStatus.BAD_REQUEST, "Soubor z uvedené URL nelze stáhnout."),
    UNSUPPORTED_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Nepodporovaný formát souboru."),
    RDF_FORMAT_INVALID(HttpStatus.BAD_REQUEST, "Formát RDF dat je neplatný."),

    // Conversion
    FILE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "Soubor se nepodařilo načíst."),
    EXCEL_READ_FAILED(HttpStatus.BAD_REQUEST, "Soubor Excel se nepodařilo načíst."),
    CONVERSION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Převod slovníku selhal."),
    EXPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Export slovníku selhal."),

    // Validation
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validace selhala."),
    VALIDATION_REPORT_HAS_ERRORS(HttpStatus.BAD_REQUEST, "Validační zpráva obsahuje chyby."),
    VALIDATION_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Konfigurace validačních pravidel je neplatná.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}