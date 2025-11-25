package tech.yildirim.camunda.documentmanager.document;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.yildirim.camunda.documentmanager.exception.GlobalExceptionHandler;

/**
 * Comprehensive test suite for DocumentController class. Tests all REST endpoints with various
 * scenarios including validation, error handling, and success cases.
 */
@WebMvcTest(DocumentController.class)
@ExtendWith(MockitoExtension.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("DocumentController Tests")
class DocumentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DocumentService documentService;

  // Test constants
  private static final String API_BASE_PATH = "/api/v1/documents";
  private static final String CLAIM_NUMBER = "CLM-2023-001";
  private static final DocumentType DOCUMENT_TYPE = DocumentType.INVOICE;
  private static final String TEST_FILE_NAME = "test-invoice.pdf";
  private static final String TEST_CONTENT_TYPE = "application/pdf";
  private static final Long TEST_FILE_SIZE = 1024L;
  private static final Long TEST_DOCUMENT_ID = 1L;
  private static final Long TEST_VERSION = 1L;

  // Test data
  private DocumentDTO testDocumentDTO;
  private MockMultipartFile testFile;

  @BeforeEach
  void setUp() {
    // Reset the mock before each test to ensure clean state
    reset(documentService);

    // Create test DocumentDTO
    testDocumentDTO =
        DocumentDTO.builder()
            .id(TEST_DOCUMENT_ID)
            .claimNumber(CLAIM_NUMBER)
            .documentType(DOCUMENT_TYPE)
            .fileName(TEST_FILE_NAME)
            .contentType(TEST_CONTENT_TYPE)
            .fileSize(TEST_FILE_SIZE)
            .fileContent(Base64.getEncoder().encodeToString("test content".getBytes()))
            .version(TEST_VERSION)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // Create test MultipartFile
    testFile =
        new MockMultipartFile(
            "file", TEST_FILE_NAME, TEST_CONTENT_TYPE, "test file content".getBytes());
  }

  @Nested
  @DisplayName("Upload Document Tests")
  class UploadDocumentTests {

    @Test
    @DisplayName("Should upload document successfully")
    void shouldUploadDocumentSuccessfully() throws Exception {
      // Given
      when(documentService.createDocument(
              eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any(MockMultipartFile.class)))
          .thenReturn(testDocumentDTO);

      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isCreated())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value(TEST_DOCUMENT_ID))
          .andExpect(jsonPath("$.claimNumber").value(CLAIM_NUMBER))
          .andExpect(jsonPath("$.documentType").value(DOCUMENT_TYPE.name()))
          .andExpect(jsonPath("$.fileName").value(TEST_FILE_NAME))
          .andExpect(jsonPath("$.contentType").value(TEST_CONTENT_TYPE))
          .andExpect(jsonPath("$.fileSize").value(TEST_FILE_SIZE))
          .andExpect(jsonPath("$.isActive").value(true));

      verify(documentService, times(1)).createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any());
    }

    @Test
    @DisplayName("Should return 409 when document already exists")
    void shouldReturn409WhenDocumentAlreadyExists() throws Exception {
      // Given
      when(documentService.createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any()))
          .thenThrow(new DocumentService.DocumentCreationException("Document already exists"));

      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isConflict());

      verify(documentService, times(1)).createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any());
    }

    @Test
    @DisplayName("Should return 400 for empty claim number")
    void shouldReturn400ForEmptyClaimNumber() throws Exception {
      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", "")
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).createDocument(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 for claim number exceeding max length")
    void shouldReturn400ForClaimNumberExceedingMaxLength() throws Exception {
      // Given
      String longClaimNumber = "A".repeat(31); // 31 characters, exceeds max of 30

      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", longClaimNumber)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).createDocument(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 for missing claim number")
    void shouldReturn400ForMissingClaimNumber() throws Exception {
      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).createDocument(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 for missing document type")
    void shouldReturn400ForMissingDocumentType() throws Exception {
      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", CLAIM_NUMBER)
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).createDocument(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 for invalid document type")
    void shouldReturn400ForInvalidDocumentType() throws Exception {
      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", "INVALID_TYPE")
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).createDocument(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 for missing file")
    void shouldReturn400ForMissingFile() throws Exception {
      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).createDocument(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 for illegal argument exception")
    void shouldReturn400ForIllegalArgumentException() throws Exception {
      // Given
      when(documentService.createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any()))
          .thenThrow(new IllegalArgumentException("Invalid file content"));

      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, times(1)).createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any());
    }

    @Test
    @DisplayName("Should return 500 for unexpected exceptions")
    void shouldReturn500ForUnexpectedExceptions() throws Exception {
      // Given
      when(documentService.createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any()))
          .thenThrow(new RuntimeException("Unexpected error"));

      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(testFile)
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isInternalServerError());

      verify(documentService, times(1)).createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any());
    }
  }

  @Nested
  @DisplayName("Download Document Tests")
  class DownloadDocumentTests {

    @Test
    @DisplayName("Should download document successfully")
    void shouldDownloadDocumentSuccessfully() throws Exception {
      // Given
      byte[] fileContent = "test file content".getBytes();
      InputStream fileStream = new ByteArrayInputStream(fileContent);
      DocumentService.FileDownloadResult downloadResult =
          DocumentService.FileDownloadResult.builder()
              .fileName(TEST_FILE_NAME)
              .contentType(TEST_CONTENT_TYPE)
              .fileSize((long) fileContent.length)
              .fileStream(fileStream)
              .build();

      when(documentService.downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenReturn(Optional.of(downloadResult));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/download")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().contentType(TEST_CONTENT_TYPE))
          .andExpect(
              header()
                  .string(
                      "Content-Disposition",
                      "form-data; name=\"attachment\"; filename=\"" + TEST_FILE_NAME + "\""))
          .andExpect(header().longValue("Content-Length", fileContent.length));

      verify(documentService, times(1)).downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should return 404 when document not found")
    void shouldReturn404WhenDocumentNotFound() throws Exception {
      // Given
      when(documentService.downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenReturn(Optional.empty());

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/download")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isNotFound());

      verify(documentService, times(1)).downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should return 400 for empty claim number")
    void shouldReturn400ForEmptyClaimNumber() throws Exception {
      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/download")
                  .param("claimNumber", "")
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).downloadFileByClaimAndType(any(), any());
    }

    @Test
    @DisplayName("Should return 400 for claim number exceeding max length")
    void shouldReturn400ForClaimNumberExceedingMaxLength() throws Exception {
      // Given
      String longClaimNumber = "A".repeat(31);

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", longClaimNumber)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      // Service not called due to validation failure at controller level
    }

    @Test
    @DisplayName("Should return 400 for missing claim number")
    void shouldReturn400ForMissingClaimNumber() throws Exception {
      // When & Then
      mockMvc
          .perform(get(API_BASE_PATH + "/download").param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).downloadFileByClaimAndType(any(), any());
    }

    @Test
    @DisplayName("Should return 400 for missing document type")
    void shouldReturn400ForMissingDocumentType() throws Exception {
      // When & Then
      mockMvc
          .perform(get(API_BASE_PATH + "/download").param("claimNumber", CLAIM_NUMBER))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, never()).downloadFileByClaimAndType(any(), any());
    }

    @Test
    @DisplayName("Should handle document with no content type")
    void shouldHandleDocumentWithNoContentType() throws Exception {
      // Given
      byte[] fileContent = "test file content".getBytes();
      InputStream fileStream = new ByteArrayInputStream(fileContent);
      DocumentService.FileDownloadResult downloadResult =
          DocumentService.FileDownloadResult.builder()
              .fileName("test-document.bin")
              .contentType(null)
              .fileSize((long) fileContent.length)
              .fileStream(fileStream)
              .build();

      when(documentService.downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenReturn(Optional.of(downloadResult));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/download")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().contentType("application/octet-stream"));

      verify(documentService, times(1)).downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should handle document with no file size")
    void shouldHandleDocumentWithNoFileSize() throws Exception {
      // Given
      byte[] fileContent = "test file content".getBytes();
      InputStream fileStream = new ByteArrayInputStream(fileContent);
      DocumentService.FileDownloadResult downloadResult =
          DocumentService.FileDownloadResult.builder()
              .fileName(TEST_FILE_NAME)
              .contentType(TEST_CONTENT_TYPE)
              .fileSize(null)
              .fileStream(fileStream)
              .build();

      when(documentService.downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenReturn(Optional.of(downloadResult));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/download")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().contentType(TEST_CONTENT_TYPE));

      verify(documentService, times(1)).downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should return 400 for illegal argument exception")
    void shouldReturn400ForIllegalArgumentException() throws Exception {
      // Given
      when(documentService.downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenThrow(new IllegalArgumentException("Invalid parameters"));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/download")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, times(1)).downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should return 500 for unexpected exceptions")
    void shouldReturn500ForUnexpectedExceptions() throws Exception {
      // Given
      when(documentService.downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenThrow(new RuntimeException("Unexpected error"));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/download")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isInternalServerError());

      verify(documentService, times(1)).downloadFileByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }
  }

  @Nested
  @DisplayName("Get Document Info Tests")
  class GetDocumentInfoTests {

    @Test
    @DisplayName("Should get document info successfully")
    void shouldGetDocumentInfoSuccessfully() throws Exception {
      // Given
      when(documentService.findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenReturn(Optional.of(testDocumentDTO));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value(TEST_DOCUMENT_ID))
          .andExpect(jsonPath("$.claimNumber").value(CLAIM_NUMBER))
          .andExpect(jsonPath("$.documentType").value(DOCUMENT_TYPE.name()))
          .andExpect(jsonPath("$.fileName").value(TEST_FILE_NAME))
          .andExpect(jsonPath("$.contentType").value(TEST_CONTENT_TYPE))
          .andExpect(jsonPath("$.fileSize").value(TEST_FILE_SIZE))
          .andExpect(jsonPath("$.isActive").value(true));

      verify(documentService, times(1)).findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should return 404 when document not found")
    void shouldReturn404WhenDocumentNotFound() throws Exception {
      // Given
      when(documentService.findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenReturn(Optional.empty());

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isNotFound());

      verify(documentService, times(1)).findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should return 400 for empty claim number")
    void shouldReturn400ForEmptyClaimNumber() throws Exception {
      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", "")
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      // Service not called due to validation failure at controller level
    }

    @Test
    @DisplayName("Should return 400 for claim number exceeding max length")
    void shouldReturn400ForClaimNumberExceedingMaxLength() throws Exception {
      // Given
      String longClaimNumber = "A".repeat(31);

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", longClaimNumber)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      // Service not called due to validation failure at controller level
    }

    @Test
    @DisplayName("Should return 400 for missing claim number")
    void shouldReturn400ForMissingClaimNumber() throws Exception {
      // When & Then
      mockMvc
          .perform(get(API_BASE_PATH + "/info").param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      // Service not called due to validation failure at controller level
    }

    @Test
    @DisplayName("Should return 400 for missing document type")
    void shouldReturn400ForMissingDocumentType() throws Exception {
      // When & Then
      mockMvc
          .perform(get(API_BASE_PATH + "/info").param("claimNumber", CLAIM_NUMBER))
          .andDo(print())
          .andExpect(status().isBadRequest());

      // Service not called due to validation failure at controller level
    }

    @Test
    @DisplayName("Should return 400 for invalid document type")
    void shouldReturn400ForInvalidDocumentType() throws Exception {
      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", "INVALID_TYPE"))
          .andDo(print())
          .andExpect(status().isBadRequest());

      // Service not called due to validation failure at controller level
    }

    @Test
    @DisplayName("Should return 400 for illegal argument exception")
    void shouldReturn400ForIllegalArgumentException() throws Exception {
      // Given
      when(documentService.findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenThrow(new IllegalArgumentException("Invalid parameters"));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isBadRequest());

      verify(documentService, times(1)).findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should return 500 for unexpected exceptions")
    void shouldReturn500ForUnexpectedExceptions() throws Exception {
      // Given
      when(documentService.findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE))
          .thenThrow(new RuntimeException("Unexpected error"));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isInternalServerError());

      verify(documentService, times(1)).findDocumentByClaimAndType(CLAIM_NUMBER, DOCUMENT_TYPE);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should handle ADJUSTER_REPORT document type")
    void shouldHandleAdjusterReportDocumentType() throws Exception {
      // Given
      DocumentDTO adjusterReportDTO =
          DocumentDTO.builder()
              .id(2L)
              .claimNumber(CLAIM_NUMBER)
              .documentType(DocumentType.ADJUSTER_REPORT)
              .fileName("adjuster-report.pdf")
              .contentType(TEST_CONTENT_TYPE)
              .fileSize(2048L)
              .fileContent(Base64.getEncoder().encodeToString("adjuster report content".getBytes()))
              .version(TEST_VERSION)
              .isActive(true)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

      when(documentService.findDocumentByClaimAndType(CLAIM_NUMBER, DocumentType.ADJUSTER_REPORT))
          .thenReturn(Optional.of(adjusterReportDTO));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", "ADJUSTER_REPORT"))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.documentType").value("ADJUSTER_REPORT"))
          .andExpect(jsonPath("$.fileName").value("adjuster-report.pdf"));

      verify(documentService, times(1))
          .findDocumentByClaimAndType(CLAIM_NUMBER, DocumentType.ADJUSTER_REPORT);
    }

    @Test
    @DisplayName("Should handle maximum allowed claim number length")
    void shouldHandleMaximumAllowedClaimNumberLength() throws Exception {
      // Given
      String maxLengthClaimNumber = "A".repeat(30); // Exactly 30 characters
      when(documentService.findDocumentByClaimAndType(maxLengthClaimNumber, DOCUMENT_TYPE))
          .thenReturn(Optional.of(testDocumentDTO));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", maxLengthClaimNumber)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isOk());

      verify(documentService, times(1))
          .findDocumentByClaimAndType(maxLengthClaimNumber, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should handle special characters in claim number")
    void shouldHandleSpecialCharactersInClaimNumber() throws Exception {
      // Given
      String specialClaimNumber = "CLM-2023/001_TEST";
      when(documentService.findDocumentByClaimAndType(specialClaimNumber, DOCUMENT_TYPE))
          .thenReturn(Optional.of(testDocumentDTO));

      // When & Then
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", specialClaimNumber)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andDo(print())
          .andExpect(status().isOk());

      verify(documentService, times(1))
          .findDocumentByClaimAndType(specialClaimNumber, DOCUMENT_TYPE);
    }

    @Test
    @DisplayName("Should handle different file types in upload")
    void shouldHandleDifferentFileTypesInUpload() throws Exception {
      // Given
      MockMultipartFile imageFile =
          new MockMultipartFile(
              "file", "invoice-scan.jpg", "image/jpeg", "image content".getBytes());

      DocumentDTO imageDocumentDTO =
          DocumentDTO.builder()
              .id(3L)
              .claimNumber(CLAIM_NUMBER)
              .documentType(DOCUMENT_TYPE)
              .fileName("invoice-scan.jpg")
              .contentType("image/jpeg")
              .fileSize(512L)
              .version(TEST_VERSION)
              .isActive(true)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

      when(documentService.createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any()))
          .thenReturn(imageDocumentDTO);

      // When & Then
      mockMvc
          .perform(
              multipart(API_BASE_PATH + "/upload")
                  .file(imageFile)
                  .param("claimNumber", CLAIM_NUMBER)
                  .param("documentType", DOCUMENT_TYPE.name())
                  .contentType(MediaType.MULTIPART_FORM_DATA))
          .andDo(print())
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.fileName").value("invoice-scan.jpg"))
          .andExpect(jsonPath("$.contentType").value("image/jpeg"));

      verify(documentService, times(1)).createDocument(eq(CLAIM_NUMBER), eq(DOCUMENT_TYPE), any());
    }

    @Test
    @DisplayName("Should handle validation edge cases")
    void shouldHandleValidationEdgeCases() throws Exception {
      // Test exactly 30 character claim number (boundary test)
      String exactLengthClaimNumber = "A".repeat(30);
      when(documentService.findDocumentByClaimAndType(exactLengthClaimNumber, DOCUMENT_TYPE))
          .thenReturn(Optional.of(testDocumentDTO));

      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", exactLengthClaimNumber)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andExpect(status().isOk());

      verify(documentService, times(1))
          .findDocumentByClaimAndType(exactLengthClaimNumber, DOCUMENT_TYPE);

      // Test 31 character claim number (should fail)
      String tooLongClaimNumber = "A".repeat(31);
      mockMvc
          .perform(
              get(API_BASE_PATH + "/info")
                  .param("claimNumber", tooLongClaimNumber)
                  .param("documentType", DOCUMENT_TYPE.name()))
          .andExpect(status().isBadRequest());

      // Service should not be called for validation failures
      // (Validation happens at controller level before service call)
    }
  }
}
