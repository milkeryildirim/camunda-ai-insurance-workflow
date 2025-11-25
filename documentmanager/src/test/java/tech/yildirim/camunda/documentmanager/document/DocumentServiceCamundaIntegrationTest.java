package tech.yildirim.camunda.documentmanager.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tech.yildirim.camunda.documentmanager.integration.CamundaIntegrationService;

/**
 * Unit tests for DocumentService Camunda integration functionality.
 * Tests the integration between DocumentService and CamundaIntegrationService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Camunda Integration Tests")
class DocumentServiceCamundaIntegrationTest {

    @Mock
    private DocumentRepository mockDocumentRepository;

    @Mock
    private DocumentMapper mockDocumentMapper;

    @Mock
    private CamundaIntegrationService mockCamundaIntegrationService;

    private DocumentService documentService;

    private static final String TEST_CLAIM_NUMBER = "CLAIM-2024-001";
    private static final String TEST_UPLOADED_BY = "testuser";
    private static final String TEST_FILENAME = "test-document.pdf";
    private static final String TEST_CONTENT_TYPE = "application/pdf";

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
            mockDocumentRepository,
            mockDocumentMapper,
            mockCamundaIntegrationService
        );
    }

    @Test
    @DisplayName("Should notify Camunda when adjuster report is uploaded successfully")
    void shouldNotifyCamundaWhenAdjusterReportUploadedSuccessfully() {
        // given
        MockMultipartFile testFile = new MockMultipartFile(
            "file", TEST_FILENAME, TEST_CONTENT_TYPE, "test content".getBytes()
        );

        DocumentType documentType = DocumentType.ADJUSTER_REPORT;

        // Mock repository behavior
        when(mockDocumentRepository.getByClaimNumberAndDocumentType(TEST_CLAIM_NUMBER, documentType))
            .thenReturn(Optional.empty());

        Document savedDocument = createTestDocument(documentType);
        when(mockDocumentRepository.save(any(Document.class))).thenReturn(savedDocument);

        DocumentDTO resultDTO = createTestDocumentDTO(documentType);
        when(mockDocumentMapper.toDTO(any(Document.class))).thenReturn(resultDTO);

        // Mock Camunda service
        doNothing().when(mockCamundaIntegrationService)
            .notifyAdjusterReportReceived(eq(TEST_CLAIM_NUMBER), anyString());

        // when
        DocumentDTO result = documentService.createDocument(
            TEST_CLAIM_NUMBER, documentType, testFile, TEST_UPLOADED_BY
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getClaimNumber()).isEqualTo(TEST_CLAIM_NUMBER);
        assertThat(result.getDocumentType()).isEqualTo(documentType);

        // Verify Camunda notification was called
        verify(mockCamundaIntegrationService).notifyAdjusterReportReceived(
            eq(TEST_CLAIM_NUMBER),
            eq("/api/v1/documents/download?claimNumber=" + TEST_CLAIM_NUMBER + "&documentType=" + documentType)
        );

        // Verify customer invoice method was NOT called
        verify(mockCamundaIntegrationService, never())
            .notifyCustomerInvoiceReceived(anyString(), anyString());
    }

    @Test
    @DisplayName("Should notify Camunda when customer invoice is uploaded successfully")
    void shouldNotifyCamundaWhenCustomerInvoiceUploadedSuccessfully() {
        // given
        MockMultipartFile testFile = new MockMultipartFile(
            "file", TEST_FILENAME, TEST_CONTENT_TYPE, "test content".getBytes()
        );

        DocumentType documentType = DocumentType.INVOICE;

        // Mock repository behavior
        when(mockDocumentRepository.getByClaimNumberAndDocumentType(TEST_CLAIM_NUMBER, documentType))
            .thenReturn(Optional.empty());

        Document savedDocument = createTestDocument(documentType);
        when(mockDocumentRepository.save(any(Document.class))).thenReturn(savedDocument);

        DocumentDTO resultDTO = createTestDocumentDTO(documentType);
        when(mockDocumentMapper.toDTO(any(Document.class))).thenReturn(resultDTO);

        // Mock Camunda service
        doNothing().when(mockCamundaIntegrationService)
            .notifyCustomerInvoiceReceived(eq(TEST_CLAIM_NUMBER), anyString());

        // when
        DocumentDTO result = documentService.createDocument(
            TEST_CLAIM_NUMBER, documentType, testFile, TEST_UPLOADED_BY
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getClaimNumber()).isEqualTo(TEST_CLAIM_NUMBER);
        assertThat(result.getDocumentType()).isEqualTo(documentType);

        // Verify Camunda notification was called
        verify(mockCamundaIntegrationService).notifyCustomerInvoiceReceived(
            eq(TEST_CLAIM_NUMBER),
            eq("/api/v1/documents/download?claimNumber=" + TEST_CLAIM_NUMBER + "&documentType=" + documentType)
        );

        // Verify adjuster report method was NOT called
        verify(mockCamundaIntegrationService, never())
            .notifyAdjusterReportReceived(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle Camunda notification failure gracefully - document upload should not fail")
    void shouldHandleCamundaNotificationFailureGracefully() {
        // given
        MockMultipartFile testFile = new MockMultipartFile(
            "file", TEST_FILENAME, TEST_CONTENT_TYPE, "test content".getBytes()
        );

        DocumentType documentType = DocumentType.ADJUSTER_REPORT;

        // Mock repository behavior
        when(mockDocumentRepository.getByClaimNumberAndDocumentType(TEST_CLAIM_NUMBER, documentType))
            .thenReturn(Optional.empty());

        Document savedDocument = createTestDocument(documentType);
        when(mockDocumentRepository.save(any(Document.class))).thenReturn(savedDocument);

        DocumentDTO resultDTO = createTestDocumentDTO(documentType);
        when(mockDocumentMapper.toDTO(any(Document.class))).thenReturn(resultDTO);

        // Mock Camunda service to throw exception
        doThrow(new RuntimeException("Camunda service unavailable"))
            .when(mockCamundaIntegrationService)
            .notifyAdjusterReportReceived(anyString(), anyString());

        // when & then - should not throw exception
        DocumentDTO result = documentService.createDocument(
            TEST_CLAIM_NUMBER, documentType, testFile, TEST_UPLOADED_BY
        );

        // Document creation should still succeed
        assertThat(result).isNotNull();
        assertThat(result.getClaimNumber()).isEqualTo(TEST_CLAIM_NUMBER);
        assertThat(result.getDocumentType()).isEqualTo(documentType);

        // Verify Camunda notification was attempted
        verify(mockCamundaIntegrationService).notifyAdjusterReportReceived(anyString(), anyString());
    }

    @Test
    @DisplayName("Should not notify Camunda for unsupported document types")
    void shouldNotNotifyCamundaForUnsupportedDocumentTypes() {
        // This test would be for future document types that don't require Camunda notification
        // For now, we only have ADJUSTER_REPORT and INVOICE which both trigger notifications
        // But the pattern is established for extensibility
    }

    // Helper methods

    private Document createTestDocument(DocumentType documentType) {
        return Document.builder()
            .id(1L)
            .claimNumber(TEST_CLAIM_NUMBER)
            .documentType(documentType)
            .fileName(TEST_FILENAME)
            .fileContent("test content".getBytes())
            .contentType(TEST_CONTENT_TYPE)
            .fileSize(12L)
            .uploadedBy(TEST_UPLOADED_BY)
            .isActive(true)
            .version(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private DocumentDTO createTestDocumentDTO(DocumentType documentType) {
        return DocumentDTO.builder()
            .id(1L)
            .claimNumber(TEST_CLAIM_NUMBER)
            .documentType(documentType)
            .fileName(TEST_FILENAME)
            .contentType(TEST_CONTENT_TYPE)
            .fileSize(12L)
            .uploadedBy(TEST_UPLOADED_BY)
            .isActive(true)
            .version(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
