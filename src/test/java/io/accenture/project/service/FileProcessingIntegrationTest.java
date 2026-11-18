package io.accenture.project.service;

import io.accenture.project.exception.ZipEntryTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "file-processing.max-entry-size-bytes=1024",
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

        mockMvc.perform(get("/actuator/metrics/zip.processing.duration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("zip.processing.duration"))
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

    @Test
    void shouldRecordFailedZipProcessingMetric() throws Exception {

        Path inputFolder = Path.of("target/integration-test/Input");
        Path zipPath = inputFolder.resolve("invalid-test.zip");

        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(Files.newOutputStream(zipPath))) {

            ZipEntry entry = new ZipEntry("unsupported.txt");
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.write("Unsupported content".getBytes());
            zipOutputStream.closeEntry();
        }

        mockMvc.perform(get("/api/files/find"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/zip.processing.failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("zip.processing.failure"))
                .andExpect(jsonPath("$.measurements[0].statistic").value("COUNT"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));
    }

    @Test
    void shouldUploadZipFile() throws Exception {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(outputStream)) {

            ZipEntry entry = new ZipEntry("document.pdf");
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.write("Test PDF content".getBytes());
            zipOutputStream.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "upload-test.zip",
                "application/zip",
                outputStream.toByteArray()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isOk());

        Path inputFolder =
                Path.of("target/integration-test/Input");

        Path processedFolder =
                Path.of("target/integration-test/Processed");

        Path encodedFolder =
                Path.of("target/integration-test/Encoded");

        assertFalse(
                Files.exists(
                        inputFolder.resolve("upload-test.zip")
                )
        );

        assertTrue(
                Files.exists(
                        processedFolder.resolve("upload-test.zip")
                )
        );

        assertTrue(
                Files.exists(
                        processedFolder.resolve("upload-test_processed.zip")
                )
        );

        assertTrue(
                Files.exists(
                        processedFolder.resolve("upload-test_processed.zip.sha256")
                )
        );

        assertTrue(
                Files.exists(
                        encodedFolder.resolve("upload-test_processed.zip.txt")
                )
        );
    }

    @Test
    void shouldRejectNonZipUpload() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "not-a-zip.txt",
                "text/plain",
                "Some text".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("Only ZIP files can be uploaded: not-a-zip.txt"))
                .andExpect(jsonPath("$.fileName").value("not-a-zip.txt"));

        assertFalse(
                Files.exists(
                        Path.of("target/integration-test/Input/not-a-zip.txt")
                )
        );
    }

    @Test
    void shouldRejectUploadWithUnsafeFileName() throws Exception {

        Path escapedFile = Path.of("target/integration-test/malicious.zip");
        Files.deleteIfExists(escapedFile);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../malicious.zip",
                "application/zip",
                "malicious content".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.fileName").value("../malicious.zip"));

        assertFalse(Files.exists(escapedFile));
    }

    @Test
    void shouldCreateUniqueFileNameWhenUploadedFileAlreadyExists() throws Exception {

        Path inputFolder =
                Path.of("target/integration-test/Input");

        Path processedFolder =
                Path.of("target/integration-test/Processed");

        Path encodedFolder =
                Path.of("target/integration-test/Encoded");

        Path existingFile =
                inputFolder.resolve("duplicate.zip");

        Files.writeString(existingFile, "existing file");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(outputStream)) {

            ZipEntry entry = new ZipEntry("document.pdf");
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.write("Test PDF content".getBytes());
            zipOutputStream.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "duplicate.zip",
                "application/zip",
                outputStream.toByteArray()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isOk());

        // Original existing file must not have been overwritten
        assertTrue(Files.exists(existingFile));

        // Uploaded duplicate should no longer remain in Input
        assertFalse(
                Files.exists(
                        inputFolder.resolve("duplicate_2.zip")
                )
        );

        // Renamed uploaded original should have moved to Processed
        assertTrue(
                Files.exists(
                        processedFolder.resolve("duplicate_2.zip")
                )
        );

        // Processed version should have been created
        assertTrue(
                Files.exists(
                        processedFolder.resolve("duplicate_2_processed.zip")
                )
        );

        // Checksum should have been created
        assertTrue(
                Files.exists(
                        processedFolder.resolve(
                                "duplicate_2_processed.zip.sha256"
                        )
                )
        );

        // Base64 encoded output should have been created
        assertTrue(
                Files.exists(
                        encodedFolder.resolve(
                                "duplicate_2_processed.zip.txt"
                        )
                )
        );
    }

    @Test
    void shouldRejectEmptyUpload() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.zip",
                "application/zip",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.fileName").value("empty.zip"));

        assertFalse(
                Files.exists(
                        Path.of("target/integration-test/Input/empty.zip")
                )
        );
    }

    @Test
    void shouldRejectUploadThatIsNotAValidZip() throws Exception {

        Path inputFile = Path.of("target/integration-test/Input/fake.zip");
        Path errorFile = Path.of("target/integration-test/Error/fake.zip");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.zip",
                "application/zip",
                "This is not actually a ZIP".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.fileName").value("fake.zip"));

        assertFalse(Files.exists(inputFile));

        assertTrue(Files.exists(errorFile));
    }

    @Test
    void shouldReturnBadRequestWhenUploadedZipContainsOversizedEntry() throws Exception {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(outputStream)) {

            ZipEntry entry = new ZipEntry("large-document.pdf");
            zipOutputStream.putNextEntry(entry);

            zipOutputStream.write(new byte[2048]);

            zipOutputStream.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "oversized-entry.zip",
                "application/zip",
                outputStream.toByteArray()
        );

        mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(
                        jsonPath("$.message")
                                .value(containsString(
                                        "maximum permitted size"
                                ))
                );
    }
}
