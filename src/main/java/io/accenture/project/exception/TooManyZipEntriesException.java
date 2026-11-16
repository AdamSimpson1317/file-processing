package io.accenture.project.exception;

public class TooManyZipEntriesException extends RuntimeException {
    public TooManyZipEntriesException(int maximumEntries) {

        super(
                "Zip contains more than the maximum permitted entries: "
                    + maximumEntries
        );
    }
}
