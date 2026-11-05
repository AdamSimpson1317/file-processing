package io.accenture.project.service;

import io.accenture.project.exception.UnsupportedFileTypeException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    public Path findZipFile() throws IOException {
        try (Stream<Path> files = Files.list(Paths.get(inputPath))){
            return files
                    .filter(file -> file.toString().endsWith(".zip"))
                    .findFirst()
                    .orElseThrow(() -> new FileNotFoundException("No ZIP file found in Input folder"));
        }
    }

    public void readZipFile(Path zipFile) throws IOException {

        try(ZipInputStream zipInputStream = new ZipInputStream((new FileInputStream(zipFile.toFile())))){

            ZipEntry entry;

            while((entry= zipInputStream.getNextEntry()) != null){

                String fileName = entry.getName().toLowerCase();

                System.out.println("Checking entry: [" + fileName + "]");

                boolean supportedFile = SUPPORTED_EXTENSIONS.stream().anyMatch((fileName::endsWith));

                if(!supportedFile){
                    throw new UnsupportedFileTypeException("Unsupported file type found " + entry.getName());
                }
                System.out.println("Valid file found: " + entry.getName());

                zipInputStream.closeEntry();
            }
        }
    }

    public void processZipFile(Path zipFile) throws IOException {
        try{
            readZipFile(zipFile);
        } catch (UnsupportedFileTypeException exception){
            moveToErrorFolder(zipFile);
            throw exception;
        }
    }

    private void moveToErrorFolder(Path zipFile) throws IOException{
        Path errorFolderPath = Paths.get(errorPath);
        Path destination = errorFolderPath.resolve(zipFile.getFileName());

        Files.move(zipFile,destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
