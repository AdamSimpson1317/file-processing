package io.accenture.project.controller;

import io.accenture.project.service.FileProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FileProcessingController {
    private final FileProcessingService fileProcessingService;

    public FileProcessingController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    @GetMapping("/find")
    public ResponseEntity<String> findZip() throws IOException{

        Path zipFile = fileProcessingService.findZipFile();

        fileProcessingService.processZipFile(zipFile);

        return ResponseEntity.ok("ZIP file found " + zipFile.getFileName());
    }

}
