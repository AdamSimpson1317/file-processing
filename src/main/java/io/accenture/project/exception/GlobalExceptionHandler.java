package io.accenture.project.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.FileNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(
            UnsupportedFileTypeException exception){
        ErrorResponse errorResponse = new ErrorResponse(
                "FAILED",
                exception.getMessage(),
                exception.getFileName()
        );
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(FileNotFoundException exception){
        ErrorResponse errorResponse = new ErrorResponse(
                "FAILED",
                exception.getMessage(),
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    @ExceptionHandler(EmptyZipException.class)
    public ResponseEntity<ErrorResponse> handleEmptyZip(EmptyZipException exception){
        ErrorResponse errorResponse = new ErrorResponse(
                "FAILED",
                exception.getMessage(),
                exception.getFileName()
        );
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(InvalidZipException.class)
    public ResponseEntity<ErrorResponse> handleInvalidZip(
            InvalidZipException exception) {

        ErrorResponse errorResponse = new ErrorResponse(
                "FAILED",
                exception.getMessage(),
                exception.getFileName()
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(UnsafeZipEntryException.class)
    public ResponseEntity<ErrorResponse> handleUnsafeZipEntry(
            UnsafeZipEntryException exception) {

        ErrorResponse errorResponse = new ErrorResponse(
                "FAILED",
                exception.getMessage(),
                exception.getFileName()
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }
}
