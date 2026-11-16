package io.accenture.project.exception;

public class ZipTooLargeException extends RuntimeException {
    public ZipTooLargeException(long maximumSize) {

        super(
                "ZIP exceeds maximum total uncompressed size of "
                    +maximumSize
                    +" bytes"
        );
    }
}
