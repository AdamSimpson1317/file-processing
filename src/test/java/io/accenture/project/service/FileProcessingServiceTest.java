package io.accenture.project.service;

import io.accenture.project.exception.EmptyZipException;
import io.accenture.project.exception.InvalidZipException;
import io.accenture.project.exception.UnsafeZipEntryException;
import io.accenture.project.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class FileProcessingServiceTest {

    private final FileProcessingService fileProcessingService = new FileProcessingService();

    @TempDir
    Path tempDir;


    private void createTestZip(Path zipPath, String... fileNames) throws IOException{
        try(ZipOutputStream zipOutputStream =
                    new ZipOutputStream(Files.newOutputStream(zipPath))){
            for(String fileName : fileNames){
                ZipEntry entry = new ZipEntry(fileName);
                zipOutputStream.putNextEntry(entry);

                zipOutputStream.write(("Test content for " + fileName).getBytes());

                zipOutputStream.closeEntry();
            }
        }
    }

    @Test
    void shouldAcceptZipContainingSupportedFiles() throws IOException{

        Path zipPath = Files.createTempFile("valid-test", ".zip");
        createTestZip(zipPath,"document.pdf", "picture.jpg", "photo.jpeg");

        assertDoesNotThrow(() -> fileProcessingService.readZipFile(zipPath));
    }

    @Test
    void shouldRejectZipContainingUnsupportedFile() throws IOException{

        Path zipPath = Files.createTempFile("invalid-test", ".zip");
        createTestZip(zipPath, "document.pdf", "notes.txt");

        assertThrows(UnsupportedFileTypeException.class, () -> fileProcessingService.readZipFile(zipPath));
    }

    @Test
    void shouldIncludeUnsupportedFileNameInException() throws IOException {

        Path zipPath = Files.createTempFile("invalid-filename-test", ".zip");
        createTestZip(zipPath, "document.pdf", "notes.txt");

        UnsupportedFileTypeException exception = assertThrows(
                UnsupportedFileTypeException.class,
                () -> fileProcessingService.readZipFile(zipPath)
        );

        assertEquals(List.of("notes.txt"), exception.getFileNames());
    }

    @Test
    void shouldRejectEmptyZip() throws IOException {

        Path zipPath = Files.createTempFile("empty-test", ".zip");
        createTestZip(zipPath);

        assertThrows(EmptyZipException.class,
                () -> fileProcessingService.readZipFile(zipPath));
    }

    @Test
    void shouldAcceptUppercaseSupportedExtensions() throws IOException {

        Path zipPath = Files.createTempFile("uppercase-test", ".zip");

        createTestZip(
                zipPath,
                "DOCUMENT.PDF",
                "PICTURE.JPG",
                "PHOTO.JPEG"
        );

        assertDoesNotThrow(
                () -> fileProcessingService.readZipFile(zipPath)
        );
    }

    @Test
    void shouldMoveInvalidZipToErrorFolder() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path errorFolder = Files.createDirectory(tempDir.resolve("Error"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "errorPath",
                errorFolder.toString()
        );

        Path zipPath = inputFolder.resolve("invalid.zip");
        createTestZip(zipPath, "document.pdf", "notes.txt");

        assertThrows(
                UnsupportedFileTypeException.class,
                () -> fileProcessingService.processZipFile(zipPath)
        );

        assertTrue(Files.exists(errorFolder.resolve("invalid.zip")));
    }

    @Test
    void shouldRemoveInvalidZipFromInputAfterError() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path errorFolder = Files.createDirectory(tempDir.resolve("Error"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "errorPath",
                errorFolder.toString()
        );

        Path zipPath = inputFolder.resolve("invalid.zip");
        createTestZip(zipPath, "document.pdf", "notes.txt");

        assertThrows(
                UnsupportedFileTypeException.class,
                () -> fileProcessingService.processZipFile(zipPath)
        );

        assertFalse(Files.exists(zipPath));
    }

    @Test
    void shouldCreateProcessedZip() throws IOException {

        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = tempDir.resolve("valid.zip");
        createTestZip(zipPath, "document.pdf", "picture.jpg");

        fileProcessingService.processZipFile(zipPath);

        Path processedZip = processedFolder.resolve("valid_processed.zip");

        assertTrue(Files.exists(processedZip));
    }

    @Test
    void shouldContainOriginalFilesInProcessedZip() throws IOException {

        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = tempDir.resolve("valid.zip");
        createTestZip(zipPath, "document.pdf", "picture.jpg");

        fileProcessingService.processZipFile(zipPath);

        Path processedZip = processedFolder.resolve("valid_processed.zip");

        try (ZipInputStream zipInputStream =
                     new ZipInputStream(Files.newInputStream(processedZip))) {

            Set<String> fileNames = new HashSet<>();

            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                fileNames.add(entry.getName());
            }

            assertTrue(fileNames.contains("document.pdf"));
            assertTrue(fileNames.contains("picture.jpg"));
        }
    }

    @Test
    void shouldPreserveFileContentsInProcessedZip() throws IOException {

        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = tempDir.resolve("valid.zip");
        createTestZip(zipPath, "document.pdf");

        fileProcessingService.processZipFile(zipPath);

        Path processedZip = processedFolder.resolve("valid_processed.zip");

        try (ZipInputStream zipInputStream =
                     new ZipInputStream(Files.newInputStream(processedZip))) {

            ZipEntry entry = zipInputStream.getNextEntry();

            assertNotNull(entry);

            String content = new String(zipInputStream.readAllBytes());

            assertEquals("document.pdf", entry.getName());
            assertEquals("Test content for document.pdf", content);
        }
    }

    @Test
    void shouldCreateEncodedFile() throws IOException {

        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = tempDir.resolve("valid.zip");
        createTestZip(zipPath, "document.pdf", "picture.jpg");

        fileProcessingService.processZipFile(zipPath);

        Path encodedFile = encodedFolder.resolve("valid_processed.zip.txt");

        assertTrue(Files.exists(encodedFile));
    }

    @Test
    void shouldProduceValidBase64() throws IOException {

        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = tempDir.resolve("valid.zip");
        createTestZip(zipPath, "document.pdf", "picture.jpg");

        fileProcessingService.processZipFile(zipPath);

        Path encodedFile = encodedFolder.resolve("valid_processed.zip.txt");

        byte[] encodedBytes = Files.readAllBytes(encodedFile);

        assertDoesNotThrow(
                () -> Base64.getDecoder().decode(encodedBytes)
        );
    }

    @Test
    void shouldDecodeToOriginalProcessedZip() throws IOException {

        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = tempDir.resolve("valid.zip");
        createTestZip(zipPath, "document.pdf", "picture.jpg");

        fileProcessingService.processZipFile(zipPath);

        Path processedZip = processedFolder.resolve("valid_processed.zip");
        Path encodedFile = encodedFolder.resolve("valid_processed.zip.txt");

        byte[] processedBytes = Files.readAllBytes(processedZip);
        byte[] encodedBytes = Files.readAllBytes(encodedFile);

        byte[] decodedBytes = Base64.getDecoder().decode(encodedBytes);

        assertArrayEquals(processedBytes, decodedBytes);
    }

    @Test
    void shouldNotProcessOrEncodeInvalidZip() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));
        Path errorFolder = Files.createDirectory(tempDir.resolve("Error"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "errorPath",
                errorFolder.toString()
        );

        Path zipPath = inputFolder.resolve("invalid.zip");
        createTestZip(zipPath, "document.pdf", "notes.txt");

        assertThrows(
                UnsupportedFileTypeException.class,
                () -> fileProcessingService.processZipFile(zipPath)
        );

        assertFalse(Files.exists(processedFolder.resolve("invalid.zip")));

        assertFalse(Files.exists(encodedFolder.resolve("invalid.zip.txt")));
    }

    @Test
    void shouldHandleCorruptZip() throws IOException {

        Path zipPath = tempDir.resolve("corrupt.zip");

        Files.writeString(
                zipPath,
                "This is not actually a ZIP file"
        );

        assertThrows(
                InvalidZipException.class,
                () -> fileProcessingService.readZipFile(zipPath)
        );
    }

    @Test
    void shouldIgnoreDirectoryEntriesInZip() throws IOException{
        Path zipPath = tempDir.resolve("directory-test.zip");

        try(ZipOutputStream zipOutputStream =
                new ZipOutputStream(Files.newOutputStream(zipPath))){

            ZipEntry directory = new ZipEntry("documents/");
            zipOutputStream.putNextEntry(directory);
            zipOutputStream.closeEntry();

            ZipEntry file = new ZipEntry("documents/document.pdf");
            zipOutputStream.putNextEntry(file);
            zipOutputStream.write("Test content".getBytes());
            zipOutputStream.closeEntry();
        }

        assertDoesNotThrow(() -> fileProcessingService.readZipFile(zipPath));
    }

    @Test
    void shouldMoveSuccessfulOriginalZipToProcessed() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = inputFolder.resolve("valid.zip");

        createTestZip(
                zipPath,
                "document.pdf",
                "picture.jpg"
        );

        fileProcessingService.processZipFile(zipPath);

        Path movedOriginalZip = processedFolder.resolve("valid.zip");

        assertTrue(Files.exists(movedOriginalZip));
        assertFalse(Files.exists(zipPath));

        assertDoesNotThrow(() -> Files.delete(movedOriginalZip));

        assertFalse(Files.exists(movedOriginalZip));
    }

    @Test
    void shouldExcludeDirectoryEntriesFromProcessedZip() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        Path zipPath = inputFolder.resolve("valid.zip");

        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(Files.newOutputStream(zipPath))) {

            ZipEntry directory = new ZipEntry("documents/");
            zipOutputStream.putNextEntry(directory);
            zipOutputStream.closeEntry();

            ZipEntry file = new ZipEntry("documents/document.pdf");
            zipOutputStream.putNextEntry(file);
            zipOutputStream.write("Test content".getBytes());
            zipOutputStream.closeEntry();
        }

        fileProcessingService.processZipFile(zipPath);

        Path processedZip =
                processedFolder.resolve("valid_processed.zip");

        try (ZipInputStream zipInputStream =
                     new ZipInputStream(Files.newInputStream(processedZip))) {

            Set<String> entries = new HashSet<>();

            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }

            assertFalse(entries.contains("documents/"));
            assertTrue(entries.contains("documents/document.pdf"));
        }
    }

    @Test
    void shouldThrowFileNotFoundExceptionWhenInputFolderIsEmpty() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "inputPath",
                inputFolder.toString()
        );

        FileNotFoundException exception = assertThrows(
                FileNotFoundException.class,
                fileProcessingService::findZipFiles
        );

        assertEquals(
                "No ZIP file found in Input folder",
                exception.getMessage()
        );
    }
    @Test
    void shouldMoveEmptyZipToErrorFolder() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path errorFolder = Files.createDirectory(tempDir.resolve("Error"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "errorPath",
                errorFolder.toString()
        );

        Path zipPath = inputFolder.resolve("empty.zip");

        createTestZip(zipPath);

        assertThrows(
                EmptyZipException.class,
                () -> fileProcessingService.processZipFile(zipPath)
        );

        assertTrue(
                Files.exists(errorFolder.resolve("empty.zip"))
        );

        assertFalse(
                Files.exists(zipPath)
        );
    }
    @Test
    void shouldRejectUnsafeZipEntryPath() throws IOException{

        Path zipPath = tempDir.resolve("unsafe.zip");

        createTestZip(
                zipPath,
                "../../outside.pdf"
        );

        assertThrows(
                UnsafeZipEntryException.class,
                () -> fileProcessingService.readZipFile(zipPath)
        );
    }

    @Test
    void shouldCreateUniqueProcessedZipWhenOutputAlreadyExists() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        // Existing processed ZIP
        Path existingProcessedZip =
                processedFolder.resolve("valid_processed.zip");

        createTestZip(
                existingProcessedZip,
                "existing.pdf"
        );

        // New input ZIP with the same source filename
        Path zipPath = inputFolder.resolve("valid.zip");

        createTestZip(
                zipPath,
                "document.pdf"
        );

        fileProcessingService.processZipFile(zipPath);

        Path uniqueProcessedZip =
                processedFolder.resolve("valid_processed_2.zip");

        assertTrue(Files.exists(existingProcessedZip));
        assertTrue(Files.exists(uniqueProcessedZip));
    }

    @Test
    void shouldCreateUniqueOriginalZipWhenOriginalAlreadyExists() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        // Existing original ZIP already in Processed
        Path existingOriginalZip =
                processedFolder.resolve("valid.zip");

        createTestZip(
                existingOriginalZip,
                "existing.pdf"
        );

        // New ZIP arriving in Input with the same name
        Path newZip =
                inputFolder.resolve("valid.zip");

        createTestZip(
                newZip,
                "document.pdf"
        );

        fileProcessingService.processZipFile(newZip);

        Path uniqueOriginalZip =
                processedFolder.resolve("valid_2.zip");

        assertTrue(Files.exists(existingOriginalZip));
        assertTrue(Files.exists(uniqueOriginalZip));
    }

    @Test
    void shouldCreateUniqueEncodedFileWhenOutputAlreadyExists() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        // Existing encoded output
        Path existingEncodedFile =
                encodedFolder.resolve("valid_processed.zip.txt");

        Files.writeString(
                existingEncodedFile,
                "Existing encoded content"
        );

        // New input ZIP
        Path zipPath = inputFolder.resolve("valid.zip");

        createTestZip(
                zipPath,
                "document.pdf"
        );

        fileProcessingService.processZipFile(zipPath);

        Path uniqueEncodedFile =
                encodedFolder.resolve("valid_processed.zip_2.txt");

        assertTrue(Files.exists(existingEncodedFile));
        assertTrue(Files.exists(uniqueEncodedFile));

        assertEquals(
                "Existing encoded content",
                Files.readString(existingEncodedFile)
        );
    }

    @Test
    void shouldIncrementProcessedZipNameWhenMultipleOutputsExist() throws IOException{

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path processedFolder = Files.createDirectory(tempDir.resolve("Processed"));
        Path encodedFolder = Files.createDirectory(tempDir.resolve("Encoded"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "processedPath",
                processedFolder.toString()
        );

        ReflectionTestUtils.setField(
                fileProcessingService,
                "encodedPath",
                encodedFolder.toString()
        );

        createTestZip(
                processedFolder.resolve(processedFolder.resolve("valid_processed.zip")),
                "existing.pdf"
        );

        createTestZip(
                processedFolder.resolve(processedFolder.resolve("valid_processed_2.zip")),
                "existing.pdf"
        );

        Path zipPath = inputFolder.resolve("valid.zip");
        createTestZip(zipPath, "document.pdf");

        fileProcessingService.processZipFile(zipPath);

        assertTrue(Files.exists(processedFolder.resolve("valid_processed_3.zip")));
    }

    @Test
    void shouldAcceptSafeNestedZipEntryPath() throws IOException{

        Path zipPath = tempDir.resolve("nested-path.zip");

        createTestZip(
                zipPath,
                "documents/reports/document.pdf"
        );

        assertDoesNotThrow(() -> fileProcessingService.readZipFile(zipPath));
    }

    @Test
    void shouldThrowExceptionForUnsupportedFileTypes() throws IOException {

        Path zipFile = tempDir.resolve("test.zip");

        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(Files.newOutputStream(zipFile))) {

            // Valid PDF
            zipOutputStream.putNextEntry(new ZipEntry("document.pdf"));
            zipOutputStream.write("test".getBytes());
            zipOutputStream.closeEntry();

            // Invalid TXT
            zipOutputStream.putNextEntry(new ZipEntry("notes.txt"));
            zipOutputStream.write("test".getBytes());
            zipOutputStream.closeEntry();

            // Invalid CSV
            zipOutputStream.putNextEntry(new ZipEntry("data.csv"));
            zipOutputStream.write("test".getBytes());
            zipOutputStream.closeEntry();

            // Valid JPG
            zipOutputStream.putNextEntry(new ZipEntry("photo.jpg"));
            zipOutputStream.write("test".getBytes());
            zipOutputStream.closeEntry();

            // Invalid EXE
            zipOutputStream.putNextEntry(new ZipEntry("program.exe"));
            zipOutputStream.write("test".getBytes());
            zipOutputStream.closeEntry();
        }

        UnsupportedFileTypeException exception = assertThrows(
                UnsupportedFileTypeException.class,
                () -> fileProcessingService.readZipFile(zipFile)
        );

        assertEquals(
                List.of("notes.txt", "data.csv", "program.exe"),
                exception.getFileNames()
        );
    }

    @Test
    void shouldCreateRequiredFolders() throws IOException {

        Path input = tempDir.resolve("Input");
        Path processed = tempDir.resolve("Processed");
        Path encoded = tempDir.resolve("Encoded");
        Path error = tempDir.resolve("Error");

        ReflectionTestUtils.setField(fileProcessingService, "inputPath", input.toString());
        ReflectionTestUtils.setField(fileProcessingService, "processedPath", processed.toString());
        ReflectionTestUtils.setField(fileProcessingService, "encodedPath", encoded.toString());
        ReflectionTestUtils.setField(fileProcessingService, "errorPath", error.toString());

        assertFalse(Files.exists(input));
        assertFalse(Files.exists(processed));
        assertFalse(Files.exists(encoded));
        assertFalse(Files.exists(error));

        fileProcessingService.createFolder();

        assertTrue(Files.isDirectory(input));
        assertTrue(Files.isDirectory(processed));
        assertTrue(Files.isDirectory(encoded));
        assertTrue(Files.isDirectory(error));
    }

    @Test
    void shouldFindMultipleZipFiles() throws IOException {

        Path inputFolder =
                Files.createDirectory(tempDir.resolve("Input"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "inputPath",
                inputFolder.toString()
        );

        createTestZip(
                inputFolder.resolve("first.zip"),
                "first.pdf"
        );

        createTestZip(
                inputFolder.resolve("second.zip"),
                "second.pdf"
        );

        createTestZip(
                inputFolder.resolve("third.zip"),
                "third.pdf"
        );

        List<Path> zipFiles = fileProcessingService.findZipFiles();

        assertEquals(3, zipFiles.size());

        assertTrue(zipFiles.contains(inputFolder.resolve("first.zip")));
        assertTrue(zipFiles.contains(inputFolder.resolve("second.zip")));
        assertTrue(zipFiles.contains(inputFolder.resolve("third.zip")));
    }

    @Test
    void shouldMoveCorruptZipToErrorFolder() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path errorFolder = Files.createDirectory(tempDir.resolve("Error"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "errorPath",
                errorFolder.toString()
        );

        Path corruptZip = inputFolder.resolve("corrupt.zip");

        Files.writeString(
                corruptZip,
                "This is not actually a ZIP file"
        );

        assertThrows(
                InvalidZipException.class,
                () -> fileProcessingService.processZipFile(corruptZip)
        );

        assertFalse(Files.exists(corruptZip));

        assertTrue(
                Files.exists(errorFolder.resolve("corrupt.zip"))
        );
    }

    @Test
    void shouldFindZipFileWithUppercaseExtension() throws IOException {

        Path inputFolder =
                Files.createDirectory(tempDir.resolve("Input"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "inputPath",
                inputFolder.toString()
        );

        Path zipPath = inputFolder.resolve("uppercase.ZIP");

        createTestZip(
                zipPath,
                "document.pdf"
        );

        List<Path> zipFiles = fileProcessingService.findZipFiles();

        assertEquals(1, zipFiles.size());
        assertTrue(zipFiles.contains(zipPath));
    }

    @Test
    void shouldMoveUnsafeZipToErrorFolder() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path errorFolder = Files.createDirectory(tempDir.resolve("Error"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "errorPath",
                errorFolder.toString()
        );

        Path zipPath = inputFolder.resolve("unsafe.zip");

        createTestZip(
                zipPath,
                "../../outside.pdf"
        );

        assertThrows(
                UnsafeZipEntryException.class,
                () -> fileProcessingService.processZipFile(zipPath)
        );

        assertFalse(Files.exists(zipPath));

        assertTrue(
                Files.exists(errorFolder.resolve("unsafe.zip"))
        );
    }

    @Test
    void shouldNotOverwriteExistingZipInErrorFolder() throws IOException {

        Path inputFolder = Files.createDirectory(tempDir.resolve("Input"));
        Path errorFolder = Files.createDirectory(tempDir.resolve("Error"));

        ReflectionTestUtils.setField(
                fileProcessingService,
                "errorPath",
                errorFolder.toString()
        );

        // Existing ZIP already in Error
        Path existingZip = errorFolder.resolve("invalid.zip");
        createTestZip(existingZip, "existing.txt");

        // New invalid ZIP with the same name
        Path newZip = inputFolder.resolve("invalid.zip");
        createTestZip(newZip, "new-invalid.txt");

        assertThrows(
                UnsupportedFileTypeException.class,
                () -> fileProcessingService.processZipFile(newZip)
        );

        assertTrue(Files.exists(existingZip));

        assertTrue(
                Files.exists(errorFolder.resolve("invalid_2.zip"))
        );
    }
}
