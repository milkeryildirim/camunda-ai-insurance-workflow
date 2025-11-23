package tech.yildirim.camunda.documentmanager.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service class for document management operations. Handles document storage, retrieval, and file
 * operations for insurance claims.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final DocumentMapper documentMapper;

  /**
   * Finds a document by claim number and document type and returns as DTO.
   *
   * @param claimNumber The claim number to search for
   * @param documentType The document type to filter by
   * @return Optional containing the DocumentDTO if found
   */
  @Transactional(readOnly = true)
  public Optional<DocumentDTO> findDocumentByClaimAndType(
      @NotBlank(message = "Claim number cannot be blank")
          @Size(max = 30, message = "Claim number cannot exceed 30 characters")
          String claimNumber,
      @NotNull(message = "Document type cannot be null") DocumentType documentType) {
    log.debug("Finding document for claim: {} and type: {}", claimNumber, documentType);

    Optional<Document> document =
        documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType);

    if (document.isPresent()) {
      log.info(
          "Found document with ID: {} for claim: {} and type: {}",
          document.get().getId(),
          claimNumber,
          documentType);
      return Optional.of(documentMapper.toDTO(document.get()));
    } else {
      log.info("No document found for claim: {} and type: {}", claimNumber, documentType);
      return Optional.empty();
    }
  }

  /**
   * Downloads file content as InputStream by claim number and document type.
   *
   * @param claimNumber The claim number to search for
   * @param documentType The document type to filter by
   * @return Optional containing InputStream of the file content if document exists
   */
  @Transactional(readOnly = true)
  public Optional<FileDownloadResult> downloadFileByClaimAndType(
      @NotBlank(message = "Claim number cannot be blank")
          @Size(max = 30, message = "Claim number cannot exceed 30 characters")
          String claimNumber,
      @NotNull(message = "Document type cannot be null") DocumentType documentType) {
    log.debug("Downloading file for claim: {} and type: {}", claimNumber, documentType);

    // Use internal entity lookup for file operations
    Optional<Document> documentOpt = findDocumentEntityByClaimAndType(claimNumber, documentType);

    if (documentOpt.isEmpty()) {
      log.warn(
          "Cannot download file - document not found for claim: {} and type: {}",
          claimNumber,
          documentType);
      return Optional.empty();
    }

    Document document = documentOpt.get();

    if (document.getFileContent() == null || document.getFileContent().length == 0) {
      log.warn("Cannot download file - no content available for document ID: {}", document.getId());
      return Optional.empty();
    }

    log.info(
        "Downloading file: {} for claim: {} and type: {}, size: {} bytes",
        document.getFileName(),
        claimNumber,
        documentType,
        document.getFileSize());

    InputStream fileStream = new ByteArrayInputStream(document.getFileContent());
    FileDownloadResult result =
        FileDownloadResult.builder()
            .fileName(document.getFileName())
            .contentType(document.getContentType())
            .fileSize(document.getFileSize())
            .fileStream(fileStream)
            .build();

    return Optional.of(result);
  }

  /**
   * Creates a new document from uploaded file for specific claim and document type.
   *
   * @param claimNumber The claim number to associate the document with
   * @param documentType The type of document being uploaded
   * @param file The uploaded file
   * @param uploadedBy The username of the person uploading the document
   * @return The created DocumentDTO
   * @throws DocumentCreationException if document creation fails
   */
  @Transactional
  public DocumentDTO createDocument(
      @NotBlank(message = "Claim number cannot be blank")
          @Size(max = 30, message = "Claim number cannot exceed 30 characters")
          String claimNumber,
      @NotNull(message = "Document type cannot be null") DocumentType documentType,
      @NotNull(message = "File cannot be null") MultipartFile file,
      @NotBlank(message = "Uploaded by cannot be blank")
          @Size(max = 100, message = "Uploaded by cannot exceed 100 characters")
          String uploadedBy) {
    // Validate file content and constraints first
    validateFile(file);

    log.debug(
        "Creating new document for claim: {} and type: {}, file: {}",
        claimNumber,
        documentType,
        file.getOriginalFilename());

    try {
      // Check if document already exists for this claim and type
      if (findDocumentByClaimAndType(claimNumber, documentType).isPresent()) {
        log.warn("Document already exists for claim: {} and type: {}", claimNumber, documentType);
        throw new DocumentCreationException(
            String.format(
                "Document already exists for claim %s and type %s", claimNumber, documentType));
      }

      // Read file content
      byte[] fileContent = file.getBytes();

      // Create new document
      Document document =
          Document.builder()
              .claimNumber(claimNumber)
              .documentType(documentType)
              .fileName(file.getOriginalFilename())
              .fileContent(fileContent)
              .contentType(file.getContentType())
              .fileSize((long) fileContent.length)
              .uploadedBy(uploadedBy)
              .isActive(true)
              .version(0L)
              .build();

      // Save document
      Document savedDocument = documentRepository.save(document);

      log.info(
          "Successfully created document with ID: {} for claim: {} and type: {}, file: {}, size: {} bytes",
          savedDocument.getId(),
          claimNumber,
          documentType,
          savedDocument.getFileName(),
          savedDocument.getFileSize());

      return documentMapper.toDTO(savedDocument);

    } catch (IOException e) {
      log.error(
          "Error reading file content for claim: {} and type: {}", claimNumber, documentType, e);
      throw new DocumentCreationException(
          String.format(
              "Failed to read file content for claim %s and type %s: %s",
              claimNumber, documentType, e.getMessage()),
          e);
    } catch (Exception e) {
      if (e instanceof DocumentCreationException) {
        throw e;
      }
      log.error("Error creating document for claim: {} and type: {}", claimNumber, documentType, e);
      throw new DocumentCreationException(
          String.format(
              "Failed to create document for claim %s and type %s: %s",
              claimNumber, documentType, e.getMessage()),
          e);
    }
  }

  /**
   * Creates a document with simple file parameters (overloaded method for basic file upload).
   *
   * @param claimNumber The claim number to associate the document with
   * @param documentType The type of document being uploaded
   * @param file The uploaded file
   * @return The created DocumentDTO
   */
  @Transactional
  public DocumentDTO createDocument(
      String claimNumber, DocumentType documentType, MultipartFile file) {
    return createDocument(claimNumber, documentType, file, "system");
  }

  /**
   * Finds all documents for a specific claim number.
   *
   * @param claimNumber The claim number to search for
   * @return List of DocumentDTOs for the claim
   */
  @Transactional(readOnly = true)
  public List<DocumentDTO> findDocumentsByClaimNumber(
      @NotBlank(message = "Claim number cannot be blank")
          @Size(max = 30, message = "Claim number cannot exceed 30 characters")
          String claimNumber) {
    log.debug("Finding all documents for claim: {}", claimNumber);

    List<Document> documents = documentRepository.findByClaimNumber(claimNumber);

    log.info("Found {} documents for claim: {}", documents.size(), claimNumber);

    return documentMapper.toDTOList(documents);
  }

  /**
   * Finds all active documents by claim number and document type.
   *
   * @param claimNumber The claim number to search for
   * @param documentType The document type to filter by
   * @return List of DocumentDTOs matching the criteria
   */
  @Transactional(readOnly = true)
  public List<DocumentDTO> findActiveDocumentsByClaimAndType(
      @NotBlank(message = "Claim number cannot be blank")
          @Size(max = 30, message = "Claim number cannot exceed 30 characters")
          String claimNumber,
      @NotNull(message = "Document type cannot be null") DocumentType documentType) {
    log.debug("Finding active documents for claim: {} and type: {}", claimNumber, documentType);

    List<Document> documents =
        documentRepository.findActiveDocumentsByClaimAndType(claimNumber, documentType);

    log.info(
        "Found {} active documents for claim: {} and type: {}",
        documents.size(),
        claimNumber,
        documentType);

    return documentMapper.toDTOList(documents);
  }

  /** Validates uploaded file. */
  private void validateFile(MultipartFile file) {
    if (file == null) {
      throw new IllegalArgumentException("File cannot be null");
    }

    if (file.isEmpty()) {
      throw new IllegalArgumentException("File cannot be empty");
    }

    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || originalFilename.trim().isEmpty()) {
      throw new IllegalArgumentException("File must have a valid filename");
    }

    // Validate file size (e.g., max 10MB)
    long maxFileSize = 10L * 1024 * 1024; // 10MB in bytes
    if (file.getSize() > maxFileSize) {
      throw new IllegalArgumentException(
          String.format(
              "File size (%d bytes) exceeds maximum allowed size (%d bytes)",
              file.getSize(), maxFileSize));
    }

    // Validate content type for supported formats
    String contentType = file.getContentType();
    if (contentType != null && !isSupportedContentType(contentType)) {
      throw new IllegalArgumentException("Unsupported file type: " + contentType);
    }
  }

  /**
   * Checks if the content type is supported. More permissive approach - allows most common file
   * types. Blocks only potentially dangerous executable file types.
   */
  private boolean isSupportedContentType(String contentType) {
    if (contentType == null) return true; // Allow files without explicit content type

    String lowerContentType = contentType.toLowerCase();

    // Block potentially dangerous file types
    String[] blockedTypes = {
      "application/x-executable",
      "application/x-msdownload",
      "application/exe",
      "application/x-exe",
      "application/x-winexe",
      "application/x-bat",
      "application/x-sh",
      "text/x-script"
    };

    for (String blockedType : blockedTypes) {
      if (lowerContentType.contains(blockedType)) {
        return false;
      }
    }

    // Allow all other file types (PDF, images, Word, Excel, text, etc.)
    return true;
  }

  // ========================== PRIVATE HELPER METHODS ==========================

  /**
   * Internal method to find document entity by claim number and document type. Used only for
   * internal operations where entity access is required.
   *
   * @param claimNumber The claim number to search for
   * @param documentType The document type to filter by
   * @return Optional containing the Document entity if found
   */
  private Optional<Document> findDocumentEntityByClaimAndType(
      String claimNumber, DocumentType documentType) {

    return documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType);
  }

  /**
   * Creates a new document from DocumentDTO with Base64 encoded file content. This method is
   * designed for REST API endpoints where file content is sent as Base64.
   *
   * @param documentDTO The DocumentDTO containing all document information including Base64 file
   *     content
   * @return The created DocumentDTO
   */
  @Transactional
  public DocumentDTO createDocumentFromDTO(@Valid DocumentDTO documentDTO) {
    log.info(
        "Creating document from DTO for claim: {} and type: {}",
        documentDTO.getClaimNumber(),
        documentDTO.getDocumentType());

    // Check for duplicate documents
    if (findDocumentByClaimAndType(documentDTO.getClaimNumber(), documentDTO.getDocumentType())
        .isPresent()) {
      throw new DocumentCreationException(
          String.format(
              "Document already exists for claim %s and type %s",
              documentDTO.getClaimNumber(), documentDTO.getDocumentType()));
    }

    try {
      // Convert DTO to Entity (with Base64 -> byte[] conversion)
      Document document = documentMapper.toEntity(documentDTO);

      // Set audit fields
      LocalDateTime now = LocalDateTime.now();
      document.setCreatedAt(now);
      document.setUpdatedAt(now);
      document.setIsActive(true);
      document.setVersion(0L);

      // Set uploadedBy if not provided
      if (document.getUploadedBy() == null || document.getUploadedBy().trim().isEmpty()) {
        document.setUploadedBy("system");
      }

      // Calculate file size from decoded content if not provided
      if (document.getFileSize() == null && document.getFileContent() != null) {
        document.setFileSize((long) document.getFileContent().length);
      }

      // Save and return as DTO
      Document savedDocument = documentRepository.save(document);

      log.info(
          "Successfully created document with ID: {} for claim: {} and type: {}",
          savedDocument.getId(),
          documentDTO.getClaimNumber(),
          documentDTO.getDocumentType());

      return documentMapper.toDTO(savedDocument);

    } catch (Exception e) {
      log.error(
          "Failed to create document from DTO for claim: {} and type: {}: {}",
          documentDTO.getClaimNumber(),
          documentDTO.getDocumentType(),
          e.getMessage(),
          e);
      throw new DocumentCreationException(
          String.format(
              "Failed to create document from DTO for claim %s and type %s: %s",
              documentDTO.getClaimNumber(), documentDTO.getDocumentType(), e.getMessage()),
          e);
    }
  }

  // ========================== VALIDATION METHODS ==========================

  /** Result class for file download operations. */
  @lombok.Data
  @lombok.Builder
  public static class FileDownloadResult {
    private String fileName;
    private String contentType;
    private Long fileSize;
    private InputStream fileStream;
  }

  /** Custom exception for document creation errors. */
  public static class DocumentCreationException extends RuntimeException {
    public DocumentCreationException(String message) {
      super(message);
    }

    public DocumentCreationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
