package io.accenture.project.service;

import io.accenture.project.exception.ZipEntryTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "file-processing.max-entry-size-bytes=1024",
        "file-processing.input=target/integration-test/Input",
        "file-processing.processed=target/integration-test/Processed",
        "file-processing.encoded=target/integration-test/Encoded",
        "file-processing.error=target/integration-test/Error"
})
@AutoConfigureMockMvc
public class FileProcessingIntegrationTest {

    @Autowired
    private FileProcessingService fileProcessingService;

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    private void clearDirectory(Path directory) throws IOException{

        if(!Files.exists(directory)){
            return;
        }

        try(var files = Files.list(directory)){
            files.forEach(file -> {
                try{
                    Files.deleteIfExists(file);
                } catch (IOException exception){
                    throw new RuntimeException(exception);
                }
            });
        }
    }

    @BeforeEach
    void cleanTestDirectories() throws IOException {

        clearDirectory(Path.of("target/integration-test/Input"));
        clearDirectory(Path.of("target/integration-test/Processed"));
        clearDirectory(Path.of("target/integration-test/Encoded"));
        clearDirectory(Path.of("target/integration-test/Error"));
    }

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

    @Test
    void shouldProcessZipThroughController() throws Exception{

        Path inputFolder = Path.of("target/integration-test/Input");
        Path processedFolder = Path.of("target/integration-test/Processed");
        Path encodedFolder = Path.of("target/integration-test/Encoded");

        Path zipPath = inputFolder.resolve("integration-test.zip");

        try(ZipOutputStream zipOutputStream =
                new ZipOutputStream(Files.newOutputStream(zipPath))){

            ZipEntry entry = new ZipEntry("document.pdf");
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.write("Integration test content".getBytes());
            zipOutputStream.closeEntry();
        }

        mockMvc.perform(get("/api/files/find"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].sourceFile").value("integration-test.zip"));

        assertTrue(
                Files.exists(
                        processedFolder.resolve("integration-test_processed.zip")
                )
        );

        Path processedZip = processedFolder.resolve("integration-test_processed.zip");
        Path checksumFile = processedFolder.resolve("integration-test_processed.zip.sha256");

        assertTrue(Files.exists(checksumFile));

        String expectedChecksum = fileProcessingService.calculateSha256(processedZip);
        String actualChecksum = Files.readString(checksumFile);

        assertEquals(expectedChecksum, actualChecksum);

        assertTrue(
                Files.exists(
                        encodedFolder.resolve("integration-test_processed.zip.txt")
                )
        );
        mockMvc.perform(get("/actuator/metrics/zip.processing.success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("zip.processing.success"))
                .andExpect(jsonPath("$.measurements[0].statistic").value("COUNT"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));
    }

    @Test
    void shouldExposeActuatorHealthEndpoint() throws Exception {

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldExposeLivenessHealthEndpoint() throws Exception {

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldExposeReadinessHealthEndpoint() throws Exception {

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldExposeActuatorMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

}
