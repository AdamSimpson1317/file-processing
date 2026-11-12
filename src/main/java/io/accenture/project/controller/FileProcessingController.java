package io.accenture.project.controller;

import io.accenture.project.exception.UnsupportedFileTypeException;
import io.accenture.project.response.BatchProcessingResponse;
import io.accenture.project.response.ProcessingResponse;
import io.accenture.project.service.FileProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileProcessingController {
    private final FileProcessingService fileProcessingService;

    public FileProcessingController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    @GetMapping("/find")
    public ResponseEntity<List<BatchProcessingResponse>> findZip() throws IOException {

        List<Path> zipFiles = fileProcessingService.findZipFiles();

        List<BatchProcessingResponse> responses = new ArrayList<>();

        for (Path zipFile : zipFiles) {

            try {
                ProcessingResponse response =
                        fileProcessingService.processZipFile(zipFile);

                responses.add(new BatchProcessingResponse(
                        response.status(),
                        response.sourceFile(),
                        response.processedFile(),
                        response.encodedFile(),
                        null
                ));

            } catch (UnsupportedFileTypeException exception) {

                responses.add(new BatchProcessingResponse(
                        "FAILED",
                        zipFile.getFileName().toString(),
                        null,
                        null,
                        "Unsupported file type found: " + exception.getFileNames()
                ));
            }
        }

        return ResponseEntity.ok(responses);
    }
}