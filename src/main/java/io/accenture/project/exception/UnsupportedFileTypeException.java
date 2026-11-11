package io.accenture.project.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class UnsupportedFileTypeException extends RuntimeException {

    private final List<String> fileNames;

    public UnsupportedFileTypeException(List<String> fileNames){
        super("Unsupported file type found");
        this.fileNames = fileNames;
    }

}
