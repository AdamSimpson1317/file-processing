package io.accenture.project.exception;

import lombok.Getter;

@Getter
public class UnsupportedFileTypeException extends RuntimeException {

    private final String fileName;

    public UnsupportedFileTypeException(String fileName){
        super("Unsupported file type found");
        this.fileName = fileName;
    }

}
