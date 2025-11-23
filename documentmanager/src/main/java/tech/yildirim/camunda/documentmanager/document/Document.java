package tech.yildirim.camunda.documentmanager.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Document entity for managing insurance claim-related documents. Stores files such as invoices,
 * adjuster reports, and other claim documentation.
 */
@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

  /** Unique identifier for the document */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Insurance claim number this document is associated with */
  @NotBlank(message = "Claim number cannot be blank")
  @Size(max = 30, message = "Claim number cannot exceed 30 characters")
  @Column(name = "claim_number", nullable = false, length = 30)
  private String claimNumber;

  /** Type of document stored */
  @NotNull(message = "Document type cannot be null")
  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false, length = 50)
  private DocumentType documentType;

  /** Binary content of the document file */
  @NotNull(message = "File content cannot be null")
  @Lob
  @Column(name = "file_content", nullable = false)
  private byte[] fileContent;

  /** Original filename of the uploaded document */
  @NotBlank(message = "File name cannot be blank")
  @Size(max = 255, message = "File name cannot exceed 255 characters")
  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  /** MIME type of the file (e.g., application/pdf, image/jpeg) */
  @Size(max = 100, message = "Content type cannot exceed 100 characters")
  @Column(name = "content_type", length = 100)
  private String contentType;

  /** Size of the file in bytes */
  @Column(name = "file_size")
  private Long fileSize;

  /** User who uploaded the document */
  @Size(max = 100, message = "Uploaded by cannot exceed 100 characters")
  @Column(name = "uploaded_by", length = 100)
  private String uploadedBy;

  /** Timestamp when the document was created */
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** Timestamp when the document was last updated */
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** Flag indicating if the document is active (not soft-deleted) */
  @Builder.Default
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /** Version number for optimistic locking */
  @Builder.Default
  @Column(name = "version", nullable = false)
  private Long version = 0L;

  /** Sets the creation and update timestamps before persisting */
  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;

    // Set file size if not already set
    if (this.fileContent != null && this.fileSize == null) {
      this.fileSize = (long) this.fileContent.length;
    }
  }

  /** Updates the modification timestamp before updating */
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();

    // Update file size if content changed
    if (this.fileContent != null) {
      this.fileSize = (long) this.fileContent.length;
    }
  }

  /** Returns a human-readable representation of file size */
  public String getFormattedFileSize() {
    if (fileSize == null) return "Unknown";

    if (fileSize < 1024) return fileSize + " B";
    if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
    if (fileSize < 1024 * 1024 * 1024)
      return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
  }

  /** Returns file extension from filename */
  public String getFileExtension() {
    if (fileName == null) return "";
    int lastDotIndex = fileName.lastIndexOf('.');
    return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
  }

  /** Checks if the document is an image based on content type */
  public boolean isImage() {
    return contentType != null && contentType.startsWith("image/");
  }

  /** Checks if the document is a PDF */
  public boolean isPdf() {
    return "application/pdf".equalsIgnoreCase(contentType);
  }
}
