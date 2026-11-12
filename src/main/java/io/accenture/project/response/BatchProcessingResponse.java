package io.accenture.project.response;

public record BatchProcessingResponse(
        String status,
        String sourceFile,
        String processedFile,
        String encodedFile,
        String message
) {
}