package io.accenture.project.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "file-processing.input=target/test-folders/Input",
        "file-processing.processed=target/test-folders/Processed",
        "file-processing.encoded=target/test-folders/Encoded",
        "file-processing.error=target/test-folders/Error"
})
class FileProcessingConfigurationTest {

    @Test
    void shouldUseConfiguredFolderPaths() {

        assertTrue(Files.isDirectory(
                Path.of("target/test-folders/Input")
        ));

        assertTrue(Files.isDirectory(
                Path.of("target/test-folders/Processed")
        ));

        assertTrue(Files.isDirectory(
                Path.of("target/test-folders/Encoded")
        ));

        assertTrue(Files.isDirectory(
                Path.of("target/test-folders/Error")
        ));
    }
}