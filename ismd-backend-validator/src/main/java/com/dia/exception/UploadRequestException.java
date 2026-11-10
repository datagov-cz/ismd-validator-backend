package com.dia.exception;

import com.dia.enums.ErrorCode;
import lombok.Getter;

/** The upload request itself is unusable: no file, more than one file, or both a file and a URL. */
@Getter
public class UploadRequestException extends RuntimeException {

    private final ErrorCode errorCode;

    public UploadRequestException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}