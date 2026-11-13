package io.accenture.project.controller;

import io.accenture.project.exception.EmptyZipException;
import io.accenture.project.exception.InvalidZipException;
import io.accenture.project.exception.UnsafeZipEntryException;
import io.accenture.project.exception.UnsupportedFileTypeException;
import io.accenture.project.response.BatchProcessingResponse;
import io.accenture.project.response.ProcessingResponse;
import io.accenture.project.service.FileProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileProcessingControllerTest {

    @Test
    void shouldContinueProcessingWhenOneZipFails() throws Exception {

        FileProcessingService fileProcessingService =
                mock(FileProcessingService.class);

        FileProcessingController controller =
                new FileProcessingController(fileProcessingService);

        Path firstZip = Path.of("first.zip");
        Path invalidZip = Path.of("invalid.zip");
        Path thirdZip = Path.of("third.zip");

        when(fileProcessingService.findZipFiles())
                .thenReturn(List.of(firstZip, invalidZip, thirdZip));

        when(fileProcessingService.processZipFile(firstZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "first.zip",
                        "first_processed.zip",
                        "first_processed.zip.txt"
                ));

        when(fileProcessingService.processZipFile(invalidZip))
                .thenThrow(new UnsupportedFileTypeException(
                        List.of("notes.txt")
                ));

        when(fileProcessingService.processZipFile(thirdZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "third.zip",
                        "third_processed.zip",
                        "third_processed.zip.txt"
                ));

        ResponseEntity<List<BatchProcessingResponse>> response =
                controller.findZip();

        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());

        assertEquals("SUCCESS", response.getBody().get(0).status());
        assertEquals("FAILED", response.getBody().get(1).status());
        assertEquals("SUCCESS", response.getBody().get(2).status());

        verify(fileProcessingService).processZipFile(firstZip);
        verify(fileProcessingService).processZipFile(invalidZip);
        verify(fileProcessingService).processZipFile(thirdZip);
    }

    @Test
    void shouldContinueProcessingWhenOneZipIsCorrupt() throws Exception {

        FileProcessingService fileProcessingService =
                mock(FileProcessingService.class);

        FileProcessingController controller =
                new FileProcessingController(fileProcessingService);

        Path firstZip = Path.of("first.zip");
        Path corruptZip = Path.of("corrupt.zip");
        Path thirdZip = Path.of("third.zip");

        when(fileProcessingService.findZipFiles())
                .thenReturn(List.of(firstZip, corruptZip, thirdZip));

        when(fileProcessingService.processZipFile(firstZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "first.zip",
                        "first_processed.zip",
                        "first_processed.zip.txt"
                ));

        when(fileProcessingService.processZipFile(corruptZip))
                .thenThrow(new InvalidZipException("corrupt.zip"));

        when(fileProcessingService.processZipFile(thirdZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "third.zip",
                        "third_processed.zip",
                        "third_processed.zip.txt"
                ));

        ResponseEntity<List<BatchProcessingResponse>> response =
                controller.findZip();

        assertNotNull(response.getBody());

        assertEquals(3, response.getBody().size());

        assertEquals("SUCCESS", response.getBody().get(0).status());
        assertEquals("FAILED", response.getBody().get(1).status());
        assertEquals("SUCCESS", response.getBody().get(2).status());

        verify(fileProcessingService).processZipFile(thirdZip);
    }

    @Test
    void shouldContinueProcessingWhenOneZipIsEmpty() throws Exception {

        FileProcessingService fileProcessingService =
                mock(FileProcessingService.class);

        FileProcessingController controller =
                new FileProcessingController(fileProcessingService);

        Path firstZip = Path.of("first.zip");
        Path emptyZip = Path.of("empty.zip");
        Path thirdZip = Path.of("third.zip");

        when(fileProcessingService.findZipFiles())
                .thenReturn(List.of(firstZip, emptyZip, thirdZip));

        when(fileProcessingService.processZipFile(firstZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "first.zip",
                        "first_processed.zip",
                        "first_processed.zip.txt"
                ));

        when(fileProcessingService.processZipFile(emptyZip))
                .thenThrow(new EmptyZipException("empty.zip"));

        when(fileProcessingService.processZipFile(thirdZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "third.zip",
                        "third_processed.zip",
                        "third_processed.zip.txt"
                ));

        ResponseEntity<List<BatchProcessingResponse>> response =
                controller.findZip();

        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());

        assertEquals("SUCCESS", response.getBody().get(0).status());
        assertEquals("FAILED", response.getBody().get(1).status());
        assertEquals("SUCCESS", response.getBody().get(2).status());

        verify(fileProcessingService).processZipFile(thirdZip);
    }

    @Test
    void shouldContinueProcessingWhenOneZipHasUnsafeEntry() throws Exception {

        FileProcessingService fileProcessingService =
                mock(FileProcessingService.class);

        FileProcessingController controller =
                new FileProcessingController(fileProcessingService);

        Path firstZip = Path.of("first.zip");
        Path unsafeZip = Path.of("unsafe.zip");
        Path thirdZip = Path.of("third.zip");

        when(fileProcessingService.findZipFiles())
                .thenReturn(List.of(firstZip, unsafeZip, thirdZip));

        when(fileProcessingService.processZipFile(firstZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "first.zip",
                        "first_processed.zip",
                        "first_processed.zip.txt"
                ));

        when(fileProcessingService.processZipFile(unsafeZip))
                .thenThrow(new UnsafeZipEntryException("../../outside.pdf"));

        when(fileProcessingService.processZipFile(thirdZip))
                .thenReturn(new ProcessingResponse(
                        "SUCCESS",
                        "third.zip",
                        "third_processed.zip",
                        "third_processed.zip.txt"
                ));

        ResponseEntity<List<BatchProcessingResponse>> response =
                controller.findZip();

        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());

        assertEquals("SUCCESS", response.getBody().get(0).status());
        assertEquals("FAILED", response.getBody().get(1).status());
        assertEquals("SUCCESS", response.getBody().get(2).status());

        verify(fileProcessingService).processZipFile(thirdZip);
    }
}
