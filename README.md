# ZIP File Processing Service

A Spring Boot application for validating and processing ZIP files.

ZIP files can be processed from a configured input folder or uploaded through a REST endpoint. Valid archives are validated, copied into a processed ZIP, SHA-256 checksummed, Base64-encoded, and archived. Invalid ZIPs are moved to an error folder.

## Technologies

- Java 25
- Spring Boot
- Spring MVC
- Maven
- JUnit 5
- Mockito
- Lombok
- Spring AOP
- Spring Boot Actuator
- Micrometer

## Supported File Types

ZIP archives support:

- `.pdf`
- `.jpg`
- `.jpeg`

Supported extensions are configurable and are handled case-insensitively.

```properties
file-processing.supported-extensions=.pdf,.jpg,.jpeg
```

## Processing Folders

The development profile uses:

```text
file-processing/
├── Input/
├── Processed/
├── Encoded/
└── Error/
```

- `Input` - ZIP files waiting to be processed
- `Processed` - successful originals, processed ZIPs, and SHA-256 checksums
- `Encoded` - Base64-encoded processed ZIPs
- `Error` - ZIP files that fail validation

The application creates the required folders automatically.

Existing files are not overwritten. If a filename already exists, an incremented name such as `_2` or `_3` is generated.

## Processing Flow

For each ZIP, the application:

1. Validates that the archive is a valid ZIP.
2. Validates ZIP entry paths and supported file types.
3. Ignores directory entries.
4. Enforces configurable ZIP resource limits.
5. Creates a `_processed.zip`.
6. Generates a SHA-256 checksum.
7. Base64-encodes the processed ZIP.
8. Moves the original successful ZIP to `Processed`.

ZIPs that fail validation are moved to `Error`.

Batch processing allows one ZIP to fail without preventing the remaining ZIPs from being processed.

## Configuration

Shared configuration is stored in `application.properties`.

Default ZIP resource limits are:

```properties
file-processing.max-entry-size-bytes=10485760
file-processing.max-entries=1000
file-processing.max-total-uncompressed-size-bytes=104857600
```

These correspond to:

- 10 MB maximum individual ZIP entry size
- 1,000 maximum ZIP entries
- 100 MB maximum total uncompressed size

### Spring Profiles

The application separates environment-specific configuration using Spring profiles.

#### Development

`application-dev.properties`:

```properties
file-processing.input=./file-processing/Input
file-processing.processed=./file-processing/Processed
file-processing.encoded=./file-processing/Encoded
file-processing.error=./file-processing/Error
```

#### Test

`application-test.properties` uses isolated folders under:

```text
target/integration-test/
```

#### Production

`application-prod.properties` reads environment-specific values from:

```text
FILE_PROCESSING_INPUT
FILE_PROCESSING_PROCESSED
FILE_PROCESSING_ENCODED
FILE_PROCESSING_ERROR
FILE_PROCESSING_MAX_ENTRY_SIZE
FILE_PROCESSING_MAX_ENTRIES
FILE_PROCESSING_MAX_TOTAL_SIZE
```

This keeps production-specific paths and limits outside the source code.

## API

### Process Input Folder

```http
GET /api/files/find
```

Processes the ZIP files in the configured `Input` folder.

Each ZIP receives an independent result, allowing batch processing to continue if one archive fails.

> `GET` is used as required by the assignment. Because processing changes application state, a production API would normally use an appropriate non-GET HTTP method.

### Upload ZIP

```http
POST /api/files/upload
```

Accepts a ZIP using the multipart field:

```text
file
```

The uploaded ZIP is processed through the same validation and processing workflow.

## Validation and Error Handling

Validation covers:

- Unsupported file types
- Empty ZIP files
- Invalid or corrupt ZIP files
- Unsafe ZIP entry paths
- Invalid or unsafe uploads
- Oversized ZIP entries
- Excessive ZIP entry counts
- Excessive total uncompressed size

Custom exceptions and `@RestControllerAdvice` provide controlled HTTP error responses.

Invalid ZIPs are preserved in the `Error` folder rather than silently deleted.

## SHA-256 and Base64 Output

Successful processing produces:

```text
Processed/
├── documents.zip
├── documents_processed.zip
└── documents_processed.zip.sha256

Encoded/
└── documents_processed.zip.txt
```

The `.sha256` file contains the SHA-256 checksum of the processed ZIP.

The `.txt` file contains the Base64 representation of the processed ZIP.

## Logging

The application uses file-only logging.

Logs are written to:

```text
logs/file-processing.log
```

Spring AOP is used to log controller and service method execution and duration without duplicating logging logic across those classes.

## Actuator and Metrics

Spring Boot Actuator exposes:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/metrics
```

Micrometer records custom processing metrics:

```text
zip.processing.success
zip.processing.failure
zip.processing.duration
```

## Testing

The project contains **65 tests** covering unit and integration behaviour, including:

- ZIP validation
- Error handling
- Batch processing
- Duplicate filenames
- Resource limits
- SHA-256 checksums
- Base64 encoding
- Multipart uploads
- Spring configuration
- AOP
- Actuator
- Micrometer metrics

Filesystem unit tests use `@TempDir`, while integration tests use isolated paths under `target`.

Run the complete test suite:

```powershell
.\mvnw.cmd test
```

Run from a clean build:

```powershell
.\mvnw.cmd clean test
```

Expected result:

```text
65 tests passing
```

## Running the Application

Run with the development profile:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Place ZIP files in:

```text
file-processing/Input/
```

and call:

```http
GET /api/files/find
```

Alternatively, upload a ZIP using:

```http
POST /api/files/upload
```

## Key Design Decisions

- Controller and service responsibilities are separated.
- Configuration is externalised and separated using Spring profiles.
- Production configuration uses environment variables.
- ZIP resource limits protect against excessive archive contents.
- Original ZIP files are preserved for traceability.
- Existing files are never silently overwritten.
- Invalid ZIPs are retained in `Error`.
- SHA-256 provides an integrity value for processed archives.
- `@RestControllerAdvice` centralises API error handling.
- Spring AOP centralises execution logging.
- Actuator and Micrometer provide health and processing observability.
- Unit and integration tests use isolated filesystem locations.