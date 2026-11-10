package io.accenture.project.exception;

import lombok.Getter;

@Getter
public class UnsafeZipEntryException extends RuntimeException {

    private final String fileName;

    public UnsafeZipEntryException(String fileName) {
        super("Unsafe ZIP entry path");
        this.fileName = fileName;
    }
}