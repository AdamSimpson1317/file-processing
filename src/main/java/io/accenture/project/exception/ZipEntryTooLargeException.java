package io.accenture.project.exception;

public class ZipEntryTooLargeException extends RuntimeException {
    public ZipEntryTooLargeException(String entryName, long maximumSize) {
        super(
                "Zip entry exceeds maximum permitted size: "
                    +entryName
                    +" (maximum "
                    +maximumSize
                    +" bytes)"
        );
    }
}
