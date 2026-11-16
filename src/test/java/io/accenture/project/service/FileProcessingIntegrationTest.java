package io.accenture.project.service;

import io.accenture.project.exception.ZipEntryTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "file-processing.max-entry-size-bytes=1024"
})
public class FileProcessingIntegrationTest {

    @Autowired
    private FileProcessingService fileProcessingService;

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadFileProcessingServiceFromSpringContext(){
        assertNotNull(fileProcessingService);
    }

    @Test
    void shouldApplyConfiguredMaximumEntrySize() throws IOException {

        Path zipPath = tempDir.resolve("configured-limit.zip");

        try(ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))){

            ZipEntry entry = new ZipEntry("large-document.pdf");
            zipOutputStream.putNextEntry(entry);

            zipOutputStream.write(new byte[2048]);

            zipOutputStream.closeEntry();
        }

        assertThrows(
                ZipEntryTooLargeException.class, () -> fileProcessingService.readZipFile(zipPath)
        );
    }
}
