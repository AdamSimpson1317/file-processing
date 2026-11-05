package io.accenture.project.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final String status;

    private final String message;

    private final String fileName;

    public ErrorResponse(String status, String message, String fileName){
        this.status = status;
        this.message = message;
        this.fileName = fileName;
    }

}
