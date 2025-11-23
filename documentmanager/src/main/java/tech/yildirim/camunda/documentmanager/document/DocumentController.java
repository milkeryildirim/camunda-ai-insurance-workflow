package tech.yildirim.camunda.documentmanager.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller for document management operations.
 * Handles document upload and download functionality for insurance claims.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Validated
@CrossOrigin(origins = "*")
public class DocumentController {

  private final DocumentService documentService;

  /**
   * Uploads a document for a specific insurance claim.
   *
   * @param claimNumber The insurance claim number (max 30 characters)
   * @param documentType The type of document being uploaded (INVOICE or ADJUSTER_REPORT)
   * @param file The file to upload (PDF, images, etc.)
   * @return DocumentDTO containing the uploaded document information
   */
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentDTO> uploadDocument(
      @RequestParam("claimNumber")
      @NotBlank(message = "Claim number cannot be blank")
      @Size(max = 30, message = "Claim number cannot exceed 30 characters")
      String claimNumber,

      @RequestParam("documentType")
      @NotNull(message = "Document type cannot be null")
      DocumentType documentType,

      @RequestParam("file")
      @NotNull(message = "File cannot be null")
      MultipartFile file) {

    log.info("Uploading document for claim: {} with type: {}, filename: {}",
             claimNumber, documentType, file.getOriginalFilename());

    try {
      DocumentDTO uploadedDocument = documentService.createDocument(claimNumber, documentType, file);

      log.info("Successfully uploaded document with ID: {} for claim: {}",
               uploadedDocument.getId(), claimNumber);

      return ResponseEntity.status(HttpStatus.CREATED).body(uploadedDocument);

    } catch (DocumentService.DocumentCreationException ex) {
      log.warn("Document upload failed - document already exists: {}", ex.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT).build();

    } catch (IllegalArgumentException ex) {
      log.warn("Document upload failed - invalid request: {}", ex.getMessage());
      return ResponseEntity.badRequest().build();

    } catch (Exception ex) {
      log.error("Unexpected error during document upload for claim: {}", claimNumber, ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Downloads a document for a specific insurance claim and document type.
   *
   * @param claimNumber The insurance claim number (max 30 characters)
   * @param documentType The type of document to download (INVOICE or ADJUSTER_REPORT)
   * @return ResponseEntity with the file content as InputStreamResource
   */
  @GetMapping("/download")
  public ResponseEntity<InputStreamResource> downloadDocument(
      @RequestParam("claimNumber")
      @NotBlank(message = "Claim number cannot be blank")
      @Size(max = 30, message = "Claim number cannot exceed 30 characters")
      String claimNumber,

      @RequestParam("documentType")
      @NotNull(message = "Document type cannot be null")
      DocumentType documentType) {

    log.info("Downloading document for claim: {} with type: {}", claimNumber, documentType);

    try {
      Optional<DocumentService.FileDownloadResult> downloadResult =
          documentService.downloadFileByClaimAndType(claimNumber, documentType);

      if (downloadResult.isEmpty()) {
        log.warn("Document not found for claim: {} and type: {}", claimNumber, documentType);
        return ResponseEntity.notFound().build();
      }

      DocumentService.FileDownloadResult result = downloadResult.get();

      // Prepare HTTP headers for file download
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType(
          result.getContentType() != null ? result.getContentType() : "application/octet-stream"));
      headers.setContentDispositionFormData("attachment", result.getFileName());

      if (result.getFileSize() != null) {
        headers.setContentLength(result.getFileSize());
      }

      // Create InputStreamResource from the file stream
      InputStreamResource resource = new InputStreamResource(result.getFileStream()) {
        @Override
        public String getFilename() {
          return result.getFileName();
        }

        @Override
        public long contentLength() {
          return result.getFileSize() != null ? result.getFileSize() : -1;
        }
      };

      log.info("Successfully prepared download for file: {} (size: {} bytes)",
               result.getFileName(), result.getFileSize());

      return ResponseEntity.ok()
          .headers(headers)
          .body(resource);

    } catch (IllegalArgumentException ex) {
      log.warn("Document download failed - invalid request: {}", ex.getMessage());
      return ResponseEntity.badRequest().build();

    } catch (Exception ex) {
      log.error("Unexpected error during document download for claim: {} and type: {}",
                claimNumber, documentType, ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Get document information without downloading the file content.
   *
   * @param claimNumber The insurance claim number (max 30 characters)
   * @param documentType The type of document to get info for (INVOICE or ADJUSTER_REPORT)
   * @return DocumentDTO containing the document information (without file content)
   */
  @GetMapping("/info")
  public ResponseEntity<DocumentDTO> getDocumentInfo(
      @RequestParam("claimNumber")
      @NotBlank(message = "Claim number cannot be blank")
      @Size(max = 30, message = "Claim number cannot exceed 30 characters")
      String claimNumber,

      @RequestParam("documentType")
      @NotNull(message = "Document type cannot be null")
      DocumentType documentType) {

    log.info("Getting document info for claim: {} with type: {}", claimNumber, documentType);

    try {
      Optional<DocumentDTO> document = documentService.findDocumentByClaimAndType(claimNumber, documentType);

      if (document.isEmpty()) {
        log.warn("Document not found for claim: {} and type: {}", claimNumber, documentType);
        return ResponseEntity.notFound().build();
      }

      log.info("Successfully retrieved document info for claim: {} with type: {}", claimNumber, documentType);
      return ResponseEntity.ok(document.get());

    } catch (IllegalArgumentException ex) {
      log.warn("Get document info failed - invalid request: {}", ex.getMessage());
      return ResponseEntity.badRequest().build();

    } catch (Exception ex) {
      log.error("Unexpected error while getting document info for claim: {} and type: {}",
                claimNumber, documentType, ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}
