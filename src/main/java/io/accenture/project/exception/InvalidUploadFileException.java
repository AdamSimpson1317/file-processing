package io.accenture.project.exception;

import lombok.Getter;

@Getter
public class InvalidUploadFileException extends RuntimeException {

    private final String fileName;

    public InvalidUploadFileException(String fileName) {

        super("Only ZIP files can be uploaded: " + fileName);
        this.fileName = fileName;
    }
}
