package tech.yildirim.camunda.documentmanager.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Document entity. Used for REST API requests and responses. Excludes
 * sensitive data like file content for performance and security.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDTO {

  /** Unique identifier for the document */
  @JsonProperty("id")
  private Long id;

  /** Insurance claim number this document is associated with */
  @NotBlank(message = "Claim number cannot be blank")
  @Size(max = 100, message = "Claim number cannot exceed 100 characters")
  @JsonProperty("claimNumber")
  private String claimNumber;

  /** Type of document stored */
  @NotNull(message = "Document type cannot be null")
  @JsonProperty("documentType")
  private DocumentType documentType;

  /** Original filename of the uploaded document */
  @NotBlank(message = "File name cannot be blank")
  @Size(max = 255, message = "File name cannot exceed 255 characters")
  @JsonProperty("fileName")
  private String fileName;

  /** MIME type of the file (e.g., application/pdf, image/jpeg) */
  @Size(max = 100, message = "Content type cannot exceed 100 characters")
  @JsonProperty("contentType")
  private String contentType;

  /** Size of the file in bytes */
  @JsonProperty("fileSize")
  private Long fileSize;

  /**
   * File content encoded as Base64 string for REST API file upload.
   * This field is used when creating/uploading documents via REST endpoints.
   * For download operations, this field can be null to avoid large JSON responses.
   */
  @JsonProperty("fileContent")
  private String fileContent;

  /** User who uploaded the document */
  @Size(max = 100, message = "Uploaded by cannot exceed 100 characters")
  @JsonProperty("uploadedBy")
  private String uploadedBy;

  /** Timestamp when the document was created */
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  @JsonProperty("createdAt")
  private LocalDateTime createdAt;

  /** Timestamp when the document was last updated */
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  @JsonProperty("updatedAt")
  private LocalDateTime updatedAt;

  /** Flag indicating if the document is active (not soft-deleted) */
  @Builder.Default
  @JsonProperty("isActive")
  private Boolean isActive = true;

  /** Version number for optimistic locking */
  @Builder.Default
  @JsonProperty("version")
  private Long version = 0L;

  /**
   * Returns a human-readable representation of file size. This is a computed field for UI display
   * purposes.
   */
  @JsonProperty("formattedFileSize")
  public String getFormattedFileSize() {
    if (fileSize == null) return "Unknown";

    if (fileSize < 1024) return fileSize + " B";
    if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
    if (fileSize < 1024 * 1024 * 1024)
      return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
  }

  /** Returns file extension from filename. This is a computed field for UI display purposes. */
  @JsonProperty("fileExtension")
  public String getFileExtension() {
    if (fileName == null) return "";
    int lastDotIndex = fileName.lastIndexOf('.');
    return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
  }

  /** Returns the display name of the document type. This is a computed field for UI display. */
  @JsonProperty("documentTypeDisplayName")
  public String getDocumentTypeDisplayName() {
    return documentType != null ? documentType.getDisplayName() : null;
  }
}
