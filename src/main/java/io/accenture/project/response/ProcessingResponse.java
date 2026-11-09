package io.accenture.project.response;

public record ProcessingResponse(String status,
                                 String sourceFile,
                                 String processedFile,
                                 String encodedFile) {

}
