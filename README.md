# ZIP File Processing Service

A Spring Boot application that reads ZIP files from a configured local input folder, validates their contents, creates a processed ZIP, Base64-encodes the processed output, and handles invalid input through controlled error responses.

## Prerequisites

- JDK 25
- Git
- IntelliJ IDEA or another Java IDE
- Bruno or another HTTP client for API testing

The project includes the Maven Wrapper, so a separate Maven installation is not required.

## Technologies

- Java 25
- Spring Boot
- Maven
- JUnit 5
- Lombok
- Java ZIP APIs
- Java Base64 API
- Bruno

## Supported File Types

ZIP archives support:

- `.pdf`
- `.jpg`
- `.jpeg`

File extensions are handled case-insensitively.

If an unsupported file type is found, the complete ZIP is rejected and moved to the `Error` folder.

## Folder Structure

```text
file-processing/
├── Input/
├── Processed/
├── Encoded/
└── Error/
```

- `Input` contains ZIP files waiting to be processed.
- `Processed` contains successful original ZIPs and newly created processed ZIPs.
- `Encoded` contains Base64-encoded `.txt` output.
- `Error` contains ZIP files that fail validation.

## Configuration

Folder paths are configured in `application.properties`:

```properties
file-processing.input=./file-processing/Input
file-processing.processed=./file-processing/Processed
file-processing.encoded=./file-processing/Encoded
file-processing.error=./file-processing/Error
```

The application automatically creates the required folders at startup if they do not already exist.

## Processing Flow

1. Find a ZIP file in `Input`.
2. Read and validate its entries.
3. Accept PDF, JPG and JPEG files.
4. Ignore directory entries.
5. Reject the complete ZIP if validation fails.
6. Create a new `_processed.zip` containing the validated files.
7. Save the new ZIP in `Processed`.
8. Base64-encode the processed ZIP.
9. Save the encoded output as a `.txt` file in `Encoded`.
10. Move the original successful ZIP to `Processed`.

ZIP files containing unsupported file types or no files are moved to `Error`.

## API

### Process ZIP

```http
GET /api/files/find
```

> **Note:** `GET` is used as defined by the assignment. Calling this endpoint triggers file processing and therefore has side effects. In a production API, an operation that changes state would normally use an appropriate non-GET HTTP method.

### Success

**HTTP 200 OK**

```json
{
  "status": "SUCCESS",
  "sourceFile": "documents.zip",
  "processedFile": "documents_processed.zip",
  "encodedFile": "documents_processed.zip.txt"
}
```

### Unsupported File

**HTTP 400 Bad Request**

```json
{
  "status": "FAILED",
  "message": "Unsupported file type found",
  "fileName": "notes.txt"
}
```

### Empty ZIP

**HTTP 400 Bad Request**

```json
{
  "status": "FAILED",
  "message": "ZIP file is empty",
  "fileName": "empty.zip"
}
```

### Empty Input Folder

**HTTP 404 Not Found**

```json
{
  "status": "FAILED",
  "message": "No ZIP file found in Input folder",
  "fileName": null
}
```

## Testing

The project uses JUnit 5.

`@TempDir` is used so filesystem tests operate on temporary directories rather than the application's real processing folders.

Tests cover:

- Supported and unsupported file validation
- Case-insensitive extensions
- Empty ZIP handling
- Error-folder handling
- Successful ZIP movement
- Processed ZIP creation
- ZIP entry and file-content preservation
- Base64 encoding and decoding
- Directory-entry handling
- Empty `Input` handling

Run all tests with:

```powershell
mvnw.cmd test
```

## Running the Application

Start the application from the project root:

```powershell
mvnw.cmd spring-boot:run
```

Place a ZIP file in:

```text
file-processing/Input/
```

Then call:

```http
GET /api/files/find
```

The endpoint can be tested using Bruno.

## Key Design Decisions

- Folder paths are externalised through `application.properties`.
- Controller and service responsibilities are separated.
- Custom exceptions provide controlled API error responses.
- Try-with-resources ensures ZIP and file resources are closed correctly.
- Original ZIP files are preserved rather than deleted.
- Processed ZIPs use the `_processed.zip` suffix to distinguish them from their source.
- JUnit temporary directories keep automated tests isolated from real processing folders.

## Branches

### `master`

Contains the stable implementation aligned with the core KT requirements.

### `feature/robustness-enhancements`

Contains additional defensive improvements developed separately from the baseline implementation, allowing optional robustness work to be reviewed without changing the core assignment solution.