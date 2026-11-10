package io.accenture.project.exception;

import lombok.Getter;

@Getter
public class InvalidZipException extends RuntimeException {

    private final String fileName;

    public InvalidZipException(String fileName) {
        super("Invalid or corrupt ZIP file");
        this.fileName = fileName;
    }
}