package io.accenture.project.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
