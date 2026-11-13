package io.accenture.project.service;

import io.accenture.project.exception.EmptyZipException;
import io.accenture.project.exception.InvalidZipException;
import io.accenture.project.exception.UnsafeZipEntryException;
import io.accenture.project.exception.UnsupportedFileTypeException;
import io.accenture.project.response.ProcessingResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.*;

@Slf4j
@Service
public class FileProcessingService {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".pdf", ".jpg", ".jpeg");

    @Value("${file-processing.input}")
    private String inputPath;

    @Value("${file-processing.processed}")
    private String processedPath;

    @Value("${file-processing.encoded}")
    private String encodedPath;

    @Value("${file-processing.error}")
    private String errorPath;

    @PostConstruct
    public void createFolder() throws IOException {
        Files.createDirectories(Paths.get(inputPath));
        Files.createDirectories(Paths.get(processedPath));
        Files.createDirectories(Paths.get(encodedPath));
        Files.createDirectories(Paths.get(errorPath));
    }

    public List<Path> findZipFiles() throws IOException {
        try (Stream<Path> files = Files.list(Paths.get(inputPath))) {

            List<Path> zipFiles = files
                    .filter(file -> file.toString().toLowerCase().endsWith(".zip"))
                    .toList();

            if (zipFiles.isEmpty()) {
                throw new FileNotFoundException(
                        "No ZIP file found in Input folder"
                );
            }

            return zipFiles;
        }
    }

    public void readZipFile(Path zipFile) throws IOException {

        validateZipFile(zipFile);

        boolean hasFiles = false;
        List<String> unsupportedFiles = new ArrayList<>();

        try(ZipInputStream zipInputStream = new ZipInputStream((new FileInputStream(zipFile.toFile())))){

            ZipEntry entry;

            while((entry= zipInputStream.getNextEntry()) != null){

                if(entry.isDirectory()){
                    log.info("Skipping directory entry: {}", entry.getName());
                    zipInputStream.closeEntry();
                    continue;
                }

                validateZipEntryPath(entry);

                hasFiles = true;

                String fileName = entry.getName().toLowerCase();

                log.info("Checking entry: [{}]", fileName);

                boolean supportedFile = SUPPORTED_EXTENSIONS.stream().anyMatch((fileName::endsWith));

                if(!supportedFile){
                    unsupportedFiles.add(entry.getName());
                } else {
                    log.info("Valid file found: {}", entry.getName());
                }

                zipInputStream.closeEntry();
            }
        }
        if (!hasFiles) {
            throw new EmptyZipException(zipFile.getFileName().toString());
        }
        if(!unsupportedFiles.isEmpty()){
            throw new UnsupportedFileTypeException(unsupportedFiles);
        }
    }

    private void validateZipFile(Path zipFile) throws IOException{
        try(ZipFile zip = new ZipFile(zipFile.toFile())){
            zip.entries();
        }catch(ZipException exception){
            throw new InvalidZipException(zipFile.getFileName().toString());
        }
    }

    private void validateZipEntryPath(ZipEntry entry){
        Path entryPath = Paths.get(entry.getName()).normalize();
        if(entryPath.isAbsolute() || entryPath.startsWith("..")){
            throw new UnsafeZipEntryException(entry.getName());
        }
    }

    public ProcessingResponse processZipFile(Path zipFile) throws IOException {

        log.info("Starting processing for ZIP: {}", zipFile.getFileName());
        try{

            readZipFile(zipFile);
            log.info("ZIP validation completed successfully: {}", zipFile.getFileName());

            Path processedZip = createProcessedZip(zipFile);
            log.info("Processed ZIP created successfully: {}", zipFile.getFileName());

            encodeProcessedZip(processedZip);
            log.info("Processed ZIP encoded successfully: {}", processedZip.getFileName());

            moveZipToProcessed(zipFile);
            log.info("Original ZIP moved to Processed folder {}", zipFile.getFileName());

            return new ProcessingResponse(
                    "SUCCESS",
                    zipFile.getFileName().toString(),
                    processedZip.getFileName().toString(),
                    processedZip.getFileName() + ".txt"
            );

        } catch (UnsupportedFileTypeException exception){

            log.warn("Unsupported files found in ZIP: {}", exception.getFileNames());

            moveToErrorFolder(zipFile);
            log.info("Moved invalid ZIP (due to unsupported files) to error folder: {}", zipFile.getFileName());

            throw exception;
        } catch (EmptyZipException exception) {

            log.warn("Empty ZIP found: {}", zipFile.getFileName());

            moveToErrorFolder(zipFile);
            log.info("Moved empty ZIP to error folder: {}", zipFile.getFileName());
            throw exception;
        } catch (InvalidZipException exception) {

            log.warn("Invalid ZIP found: {}", zipFile.getFileName());

            moveToErrorFolder(zipFile);
            log.info("Moved invalid ZIP to error folder: {}", zipFile.getFileName());

            throw exception;
        } catch (UnsafeZipEntryException exception) {

            log.warn("Unsafe ZIP entry found: {}", exception.getMessage());

            moveToErrorFolder(zipFile);
            log.info("Moved unsafe ZIP to error folder: {}", zipFile.getFileName());

            throw exception;
        }
    }

    private void moveToErrorFolder(Path zipFile) throws IOException{
        Path errorFolderPath = Paths.get(errorPath);
        Path destination = errorFolderPath.resolve(zipFile.getFileName());

        Files.move(zipFile,destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path createProcessedZip(Path zipFile) throws IOException{
        String originalFileName = zipFile.getFileName().toString();

        Path processedFolder = Paths.get(processedPath);

        String processedFileName = originalFileName.replace(
                ".zip",
                "_processed.zip"
        );

        Path outputZip = getUniquePath(
                processedFolder,
                processedFileName
        );

        try(
            ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile));
            ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(outputZip))
        ){
            ZipEntry entry;

            while((entry = zipInputStream.getNextEntry()) != null){

                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                zipOutputStream.putNextEntry(newEntry);

                zipInputStream.transferTo(zipOutputStream);

                zipOutputStream.closeEntry();
                zipInputStream.closeEntry();
            }
        }

        return outputZip;
    }

    private void moveZipToProcessed(Path zipFile) throws IOException{
        Path processedFolder = Paths.get(processedPath);

        Path destination = getUniquePath(
                processedFolder,
                zipFile.getFileName().toString()
        );

        Files.move(zipFile, destination);
    }

    private void encodeProcessedZip(Path processedZip) throws IOException{

        Path encodedFolder = Paths.get(encodedPath);

        byte[] zipBytes = Files.readAllBytes(processedZip);

        byte[] encodedBytes = Base64.getEncoder().encode(zipBytes);

        String encodedFileName = processedZip.getFileName() + ".txt";

        Path outputFile = getUniquePath(encodedFolder, encodedFileName);

        Files.write(outputFile, encodedBytes);
    }

    private Path getUniquePath(Path folder, String fileName){
        Path outputPath = folder.resolve(fileName);

        int counter = 2;

        while(Files.exists(outputPath)){

            int extensionIndex = fileName.lastIndexOf(".");

            String name = fileName.substring(0, extensionIndex);
            String extension = fileName.substring(extensionIndex);

            outputPath = folder.resolve(name + "_" + counter + extension);
            counter++;
        }
        return outputPath;
    }
}
