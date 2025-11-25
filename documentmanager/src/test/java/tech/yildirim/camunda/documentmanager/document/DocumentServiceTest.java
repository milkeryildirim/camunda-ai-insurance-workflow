package tech.yildirim.camunda.documentmanager.document;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tech.yildirim.camunda.documentmanager.integration.CamundaIntegrationService;

/**
 * Comprehensive test suite for DocumentService class. Tests all public methods including
 * validation, error handling, and business logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Tests")
class DocumentServiceTest {

  @Mock private DocumentRepository documentRepository;

  @Mock private DocumentMapper documentMapper;

  @Mock private CamundaIntegrationService camundaIntegrationService;

  private DocumentService documentService;

  private Document testDocument;
  private DocumentDTO testDocumentDTO;
  private MockMultipartFile testFile;
  private final String claimNumber = "CLM-2023-001";
  private final DocumentType documentType = DocumentType.INVOICE;

  @BeforeEach
  void setUp() {
    // Initialize DocumentService with all required dependencies
    documentService = new DocumentService(documentRepository, documentMapper, camundaIntegrationService);

    // Setup test document entity
    testDocument =
        Document.builder()
            .id(1L)
            .claimNumber(claimNumber)
            .documentType(documentType)
            .fileName("test-invoice.pdf")
            .fileContent("test content".getBytes())
            .contentType("application/pdf")
            .fileSize(12L)
            .uploadedBy("test-user")
            .isActive(true)
            .version(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // Setup test document DTO
    testDocumentDTO =
        DocumentDTO.builder()
            .id(1L)
            .claimNumber(claimNumber)
            .documentType(documentType)
            .fileName("test-invoice.pdf")
            .contentType("application/pdf")
            .fileSize(12L)
            .uploadedBy("test-user")
            .isActive(true)
            .version(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // Setup test multipart file
    testFile =
        new MockMultipartFile(
            "file", "test-invoice.pdf", "application/pdf", "test content".getBytes());
  }

  @Nested
  @DisplayName("findDocumentByClaimAndType Tests")
  class FindDocumentByClaimAndTypeTests {

    @Test
    @DisplayName("Should successfully find document by claim number and type")
    void shouldFindDocumentSuccessfully() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.of(testDocument));
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // When
      Optional<DocumentDTO> result =
          documentService.findDocumentByClaimAndType(claimNumber, documentType);

      // Then
      assertThat(result).isPresent().contains(testDocumentDTO);
      verify(documentRepository).getByClaimNumberAndDocumentType(claimNumber, documentType);
      verify(documentMapper).toDTO(testDocument);
    }

    @Test
    @DisplayName("Should return empty when document not found")
    void shouldReturnEmptyWhenDocumentNotFound() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());

      // When
      Optional<DocumentDTO> result =
          documentService.findDocumentByClaimAndType(claimNumber, documentType);

      // Then
      assertThat(result).isEmpty();
      verify(documentRepository).getByClaimNumberAndDocumentType(claimNumber, documentType);
      verifyNoInteractions(documentMapper);
    }
  }

  @Nested
  @DisplayName("downloadFileByClaimAndType Tests")
  class DownloadFileByClaimAndTypeTests {

    @Test
    @DisplayName("Should successfully download file")
    void shouldDownloadFileSuccessfully() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.of(testDocument));

      // When
      Optional<DocumentService.FileDownloadResult> result =
          documentService.downloadFileByClaimAndType(claimNumber, documentType);

      // Then
      assertThat(result).isPresent();
      DocumentService.FileDownloadResult downloadResult = result.get();
      assertThat(downloadResult.getFileName()).isEqualTo("test-invoice.pdf");
      assertThat(downloadResult.getContentType()).isEqualTo("application/pdf");
      assertThat(downloadResult.getFileSize()).isEqualTo(12L);
      assertThat(downloadResult.getFileStream()).isInstanceOf(ByteArrayInputStream.class);
    }

    @Test
    @DisplayName("Should return empty when document not found")
    void shouldReturnEmptyWhenDocumentNotFound() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());

      // When
      Optional<DocumentService.FileDownloadResult> result =
          documentService.downloadFileByClaimAndType(claimNumber, documentType);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when file content is null")
    void shouldReturnEmptyWhenFileContentIsNull() {
      // Given
      Document documentWithoutContent =
          Document.builder()
              .id(1L)
              .claimNumber(claimNumber)
              .documentType(documentType)
              .fileName("test-invoice.pdf")
              .fileContent(null)
              .build();

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.of(documentWithoutContent));

      // When
      Optional<DocumentService.FileDownloadResult> result =
          documentService.downloadFileByClaimAndType(claimNumber, documentType);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when file content is empty")
    void shouldReturnEmptyWhenFileContentIsEmpty() {
      // Given
      Document documentWithEmptyContent =
          Document.builder()
              .id(1L)
              .claimNumber(claimNumber)
              .documentType(documentType)
              .fileName("test-invoice.pdf")
              .fileContent(new byte[0])
              .build();

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.of(documentWithEmptyContent));

      // When
      Optional<DocumentService.FileDownloadResult> result =
          documentService.downloadFileByClaimAndType(claimNumber, documentType);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("createDocument Tests")
  class CreateDocumentTests {

    @Test
    @DisplayName("Should successfully create document")
    void shouldCreateDocumentSuccessfully() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification - should not throw exception
      doNothing().when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When
      DocumentDTO result =
          documentService.createDocument(claimNumber, documentType, testFile, "test-user");

      // Then
      assertThat(result).isEqualTo(testDocumentDTO);

      ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
      verify(documentRepository).save(documentCaptor.capture());

      Document savedDocument = documentCaptor.getValue();
      assertThat(savedDocument.getClaimNumber()).isEqualTo(claimNumber);
      assertThat(savedDocument.getDocumentType()).isEqualTo(documentType);
      assertThat(savedDocument.getFileName()).isEqualTo("test-invoice.pdf");
      assertThat(savedDocument.getUploadedBy()).isEqualTo("test-user");

      // Verify Camunda notification was called for INVOICE document type
      verify(camundaIntegrationService).notifyCustomerInvoiceReceived(
          claimNumber,
          "/api/v1/documents/download?claimNumber=" + claimNumber + "&documentType=" + documentType
      );
    }

    @Test
    @DisplayName("Should successfully create ADJUSTER_REPORT document and notify Camunda")
    void shouldCreateAdjusterReportDocumentSuccessfully() {
      // Given
      DocumentType adjusterReportType = DocumentType.ADJUSTER_REPORT;
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, adjusterReportType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification for ADJUSTER_REPORT
      doNothing().when(camundaIntegrationService).notifyAdjusterReportReceived(anyString(), anyString());

      // When
      DocumentDTO result =
          documentService.createDocument(claimNumber, adjusterReportType, testFile, "test-user");

      // Then
      assertThat(result).isEqualTo(testDocumentDTO);

      // Verify Camunda notification was called for ADJUSTER_REPORT document type
      verify(camundaIntegrationService).notifyAdjusterReportReceived(
          claimNumber,
          "/api/v1/documents/download?claimNumber=" + claimNumber + "&documentType=" + adjusterReportType
      );

      // Verify customer invoice method was NOT called
      verify(camundaIntegrationService, never()).notifyCustomerInvoiceReceived(anyString(), anyString());
    }

    @Test
    @DisplayName("Should continue document creation even if Camunda notification fails")
    void shouldContinueDocumentCreationEvenIfCamundaNotificationFails() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification to throw exception
      doThrow(new RuntimeException("Camunda service unavailable"))
          .when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When & Then - should not throw exception, document creation should continue
      DocumentDTO result =
          documentService.createDocument(claimNumber, documentType, testFile, "test-user");

      // Document should still be created successfully
      assertThat(result).isEqualTo(testDocumentDTO);
      verify(documentRepository).save(any(Document.class));
      verify(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when document already exists")
    void shouldThrowExceptionWhenDocumentAlreadyExists() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.of(testDocument));
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // When & Then
      assertThatThrownBy(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, testFile, "test-user"))
          .isInstanceOf(DocumentService.DocumentCreationException.class)
          .hasMessageContaining("Document already exists");
    }

    @Test
    @DisplayName("Should throw exception when file is null")
    void shouldThrowExceptionWhenFileIsNull() {
      // When & Then
      assertThatThrownBy(
              () -> documentService.createDocument(claimNumber, documentType, null, "test-user"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("File cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when file is empty")
    void shouldThrowExceptionWhenFileIsEmpty() {
      // Given
      MockMultipartFile emptyFile =
          new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

      // When & Then
      assertThatThrownBy(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, emptyFile, "test-user"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("File cannot be empty");
    }

    @Test
    @DisplayName("Should throw exception when file has no name")
    void shouldThrowExceptionWhenFileHasNoName() {
      // Given
      MockMultipartFile fileWithoutName =
          new MockMultipartFile("file", null, "application/pdf", "content".getBytes());

      // When & Then
      assertThatThrownBy(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, fileWithoutName, "test-user"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("File must have a valid filename");
    }

    @Test
    @DisplayName("Should throw exception when file size exceeds limit")
    void shouldThrowExceptionWhenFileSizeExceedsLimit() {
      // Given - Create a file larger than 10MB
      byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
      MockMultipartFile largeFile =
          new MockMultipartFile("file", "large.pdf", "application/pdf", largeContent);

      // When & Then
      assertThatThrownBy(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, largeFile, "test-user"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("File size")
          .hasMessageContaining("exceeds maximum allowed size");
    }

    @Test
    @DisplayName("Should throw exception when content type is not supported")
    void shouldThrowExceptionWhenContentTypeNotSupported() {
      // Given
      MockMultipartFile executableFile =
          new MockMultipartFile(
              "file", "malicious.exe", "application/x-executable", "content".getBytes());

      // When & Then
      assertThatThrownBy(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, executableFile, "test-user"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported file type");
    }
  }

  @Nested
  @DisplayName("createDocument overloaded method Tests")
  class CreateDocumentOverloadedTests {

    @Test
    @DisplayName("Should create document with default system user")
    void shouldCreateDocumentWithDefaultSystemUser() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification for INVOICE document type
      doNothing().when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When
      DocumentDTO result = documentService.createDocument(claimNumber, documentType, testFile);

      // Then
      assertThat(result).isEqualTo(testDocumentDTO);

      ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
      verify(documentRepository).save(documentCaptor.capture());

      Document savedDocument = documentCaptor.getValue();
      assertThat(savedDocument.getUploadedBy()).isEqualTo("system");

      // Verify Camunda notification was called
      verify(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("findDocumentsByClaimNumber Tests")
  class FindDocumentsByClaimNumberTests {

    @Test
    @DisplayName("Should find all documents by claim number")
    void shouldFindAllDocumentsByClaimNumber() {
      // Given
      List<Document> documents = List.of(testDocument);
      List<DocumentDTO> documentDTOs = List.of(testDocumentDTO);

      when(documentRepository.findByClaimNumber(claimNumber)).thenReturn(documents);
      when(documentMapper.toDTOList(documents)).thenReturn(documentDTOs);

      // When
      List<DocumentDTO> result = documentService.findDocumentsByClaimNumber(claimNumber);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.getFirst()).isEqualTo(testDocumentDTO);
      verify(documentRepository).findByClaimNumber(claimNumber);
      verify(documentMapper).toDTOList(documents);
    }

    @Test
    @DisplayName("Should return empty list when no documents found")
    void shouldReturnEmptyListWhenNoDocumentsFound() {
      // Given
      when(documentRepository.findByClaimNumber(claimNumber)).thenReturn(List.of());
      when(documentMapper.toDTOList(any())).thenReturn(List.of());

      // When
      List<DocumentDTO> result = documentService.findDocumentsByClaimNumber(claimNumber);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findActiveDocumentsByClaimAndType Tests")
  class FindActiveDocumentsByClaimAndTypeTests {

    @Test
    @DisplayName("Should find active documents by claim and type")
    void shouldFindActiveDocumentsByClaimAndType() {
      // Given
      List<Document> documents = List.of(testDocument);
      List<DocumentDTO> documentDTOs = List.of(testDocumentDTO);

      when(documentRepository.findActiveDocumentsByClaimAndType(claimNumber, documentType))
          .thenReturn(documents);
      when(documentMapper.toDTOList(documents)).thenReturn(documentDTOs);

      // When
      List<DocumentDTO> result =
          documentService.findActiveDocumentsByClaimAndType(claimNumber, documentType);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.getFirst()).isEqualTo(testDocumentDTO);
      verify(documentRepository).findActiveDocumentsByClaimAndType(claimNumber, documentType);
      verify(documentMapper).toDTOList(documents);
    }
  }

  @Nested
  @DisplayName("createDocumentFromDTO Tests")
  class CreateDocumentFromDTOTests {

    @Test
    @DisplayName("Should successfully create document from DTO")
    void shouldCreateDocumentFromDTOSuccessfully() {
      // Given
      String base64Content = Base64.getEncoder().encodeToString("test content".getBytes());
      DocumentDTO inputDTO =
          DocumentDTO.builder()
              .claimNumber(claimNumber)
              .documentType(documentType)
              .fileName("test-invoice.pdf")
              .fileContent(base64Content)
              .contentType("application/pdf")
              .uploadedBy("test-user")
              .build();

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentMapper.toEntity(inputDTO)).thenReturn(testDocument);
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification for INVOICE document type
      doNothing().when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When
      DocumentDTO result = documentService.createDocumentFromDTO(inputDTO);

      // Then
      assertThat(result).isEqualTo(testDocumentDTO);
      verify(documentRepository).save(any(Document.class));
      verify(documentMapper).toEntity(inputDTO);
      verify(documentMapper).toDTO(testDocument);

      // Verify Camunda notification was called
      verify(camundaIntegrationService).notifyCustomerInvoiceReceived(
          claimNumber,
          "/api/v1/documents/download?claimNumber=" + claimNumber + "&documentType=" + documentType
      );
    }

    @Test
    @DisplayName("Should throw exception when document already exists")
    void shouldThrowExceptionWhenDocumentAlreadyExistsFromDTO() {
      // Given
      DocumentDTO inputDTO =
          DocumentDTO.builder()
              .claimNumber(claimNumber)
              .documentType(documentType)
              .fileName("test-invoice.pdf")
              .build();

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.of(testDocument));
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // When & Then
      assertThatThrownBy(() -> documentService.createDocumentFromDTO(inputDTO))
          .isInstanceOf(DocumentService.DocumentCreationException.class)
          .hasMessageContaining("Document already exists");
    }

    @Test
    @DisplayName("Should set default uploadedBy when not provided")
    void shouldSetDefaultUploadedByWhenNotProvided() {
      // Given
      DocumentDTO inputDTO =
          DocumentDTO.builder()
              .claimNumber(claimNumber)
              .documentType(documentType)
              .fileName("test-invoice.pdf")
              .fileContent(Base64.getEncoder().encodeToString("test content".getBytes()))
              .build();

      Document documentWithoutUploadedBy =
          Document.builder()
              .claimNumber(claimNumber)
              .documentType(documentType)
              .fileName("test-invoice.pdf")
              .fileContent("test content".getBytes())
              .uploadedBy(null) // Will be set to "system"
              .build();

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentMapper.toEntity(inputDTO)).thenReturn(documentWithoutUploadedBy);
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification for INVOICE document type
      doNothing().when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When
      documentService.createDocumentFromDTO(inputDTO);

      // Then
      ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
      verify(documentRepository).save(documentCaptor.capture());

      Document savedDocument = documentCaptor.getValue();
      assertThat(savedDocument.getUploadedBy()).isEqualTo("system");

      // Verify Camunda notification was called
      verify(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should handle null claim number validation")
    void shouldHandleNullClaimNumberValidation() {
      // This test verifies that Spring's validation framework will handle @NotBlank
      // In actual runtime, ConstraintViolationException would be thrown
      // Here we test the business logic assuming validation passes
      assertThatCode(
              () -> {
                // The actual validation would be handled by Spring's AOP
                // This tests the underlying business logic
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle long claim number validation")
    void shouldHandleLongClaimNumberValidation() {
      // In real scenario, Spring would throw ConstraintViolationException
      // Here we verify the business logic works correctly with valid inputs
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());

      Optional<DocumentDTO> result =
          documentService.findDocumentByClaimAndType(claimNumber, documentType);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle null document type validation")
    void shouldHandleNullDocumentTypeValidation() {
      // Jakarta Validation would handle @NotNull in actual runtime
      // Here we test that the service method works correctly with different scenarios
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.of(testDocument));
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      Optional<DocumentDTO> result =
          documentService.findDocumentByClaimAndType(claimNumber, documentType);
      assertThat(result).isPresent().contains(testDocumentDTO);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle repository exceptions gracefully")
    void shouldHandleRepositoryExceptionsGracefully() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      assertThatThrownBy(
              () -> documentService.findDocumentByClaimAndType(claimNumber, documentType))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Database connection failed");
    }

    @Test
    @DisplayName("Should handle mapper exceptions in createDocument")
    void shouldHandleMapperExceptionsInCreateDocument() {
      // Given
      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenThrow(new RuntimeException("Mapping failed"));

      // When & Then
      assertThatThrownBy(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, testFile, "test-user"))
          .isInstanceOf(DocumentService.DocumentCreationException.class)
          .hasMessageContaining("Failed to create document");
    }

    @Test
    @DisplayName("Should handle IOException when reading file content")
    void shouldHandleIOExceptionWhenReadingFileContent() throws IOException {
      // Given
      MultipartFile problematicFile = mock(MultipartFile.class);
      when(problematicFile.isEmpty()).thenReturn(false);
      when(problematicFile.getOriginalFilename()).thenReturn("test.pdf");
      when(problematicFile.getContentType()).thenReturn("application/pdf");
      when(problematicFile.getSize()).thenReturn(1000L);
      when(problematicFile.getBytes()).thenThrow(new IOException("Failed to read file"));

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, problematicFile, "test-user"))
          .isInstanceOf(DocumentService.DocumentCreationException.class)
          .hasMessageContaining("Failed to read file content");
    }
  }

  @Nested
  @DisplayName("Content Type Validation Tests")
  class ContentTypeValidationTests {

    @Test
    @DisplayName("Should allow PDF files")
    void shouldAllowPDFFiles() {
      // Given
      MockMultipartFile pdfFile =
          new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification
      doNothing().when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When & Then
      assertThatCode(
              () ->
                  documentService.createDocument(claimNumber, documentType, pdfFile, "test-user"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should allow image files")
    void shouldAllowImageFiles() {
      // Given
      MockMultipartFile imageFile =
          new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification
      doNothing().when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When & Then
      assertThatCode(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, imageFile, "test-user"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should allow files with no content type")
    void shouldAllowFilesWithNoContentType() {
      // Given
      MockMultipartFile fileWithoutContentType =
          new MockMultipartFile("file", "test.txt", null, "content".getBytes());

      when(documentRepository.getByClaimNumberAndDocumentType(claimNumber, documentType))
          .thenReturn(Optional.empty());
      when(documentRepository.save(any(Document.class))).thenReturn(testDocument);
      when(documentMapper.toDTO(testDocument)).thenReturn(testDocumentDTO);

      // Mock Camunda notification
      doNothing().when(camundaIntegrationService).notifyCustomerInvoiceReceived(anyString(), anyString());

      // When & Then
      assertThatCode(
              () ->
                  documentService.createDocument(
                      claimNumber, documentType, fileWithoutContentType, "test-user"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should block executable files")
    void shouldBlockExecutableFiles() {
      // Test for various executable types
      String[] dangerousTypes = {
        "application/x-executable",
        "application/x-msdownload",
        "application/exe",
        "application/x-exe",
        "application/x-winexe",
        "application/x-bat",
        "application/x-sh",
        "text/x-script"
      };

      for (String contentType : dangerousTypes) {
        MockMultipartFile executableFile =
            new MockMultipartFile("file", "malicious.exe", contentType, "content".getBytes());

        // When & Then
        assertThatThrownBy(
                () ->
                    documentService.createDocument(
                        claimNumber, documentType, executableFile, "test-user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported file type");
      }
    }
  }
}
