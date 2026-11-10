package io.accenture.project.exception;

import lombok.Getter;

@Getter
public class EmptyZipException extends RuntimeException {
    private final String fileName;

    public EmptyZipException(String fileName) {
        super("ZIP file is empty");
        this.fileName = fileName;
    }
}
