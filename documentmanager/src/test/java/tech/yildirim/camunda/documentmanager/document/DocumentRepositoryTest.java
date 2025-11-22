package tech.yildirim.camunda.documentmanager.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * JPA test for DocumentRepository.
 * Tests all repository methods with in-memory H2 database.
 */
@DataJpaTest
@DisplayName("DocumentRepository JPA Tests")
class DocumentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DocumentRepository documentRepository;

    private Document invoiceDocument;
    private Document adjusterReportDocument;
    private Document inactiveDocument;

    @BeforeEach
    void setUp() {
        // Create test documents
        invoiceDocument = Document.builder()
            .claimNumber("CLAIM-001")
            .documentType(DocumentType.INVOICE)
            .fileName("invoice.pdf")
            .fileContent("Invoice content".getBytes())
            .contentType("application/pdf")
            .uploadedBy("testuser")
            .isActive(true)
            .version(0L)
            .build();

        adjusterReportDocument = Document.builder()
            .claimNumber("CLAIM-001")
            .documentType(DocumentType.ADJUSTER_REPORT)
            .fileName("adjuster-report.pdf")
            .fileContent("Adjuster report content".getBytes())
            .contentType("application/pdf")
            .uploadedBy("adjuster")
            .isActive(true)
            .version(0L)
            .build();

        inactiveDocument = Document.builder()
            .claimNumber("CLAIM-002")
            .documentType(DocumentType.INVOICE)
            .fileName("old-invoice.pdf")
            .fileContent("Old invoice content".getBytes())
            .contentType("application/pdf")
            .uploadedBy("testuser")
            .isActive(false)
            .version(0L)
            .build();

        // Persist test data
        entityManager.persistAndFlush(invoiceDocument);
        entityManager.persistAndFlush(adjusterReportDocument);
        entityManager.persistAndFlush(inactiveDocument);
    }

    @Test
    @DisplayName("Should find documents by claim number and document type")
    void shouldFindDocumentsByClaimNumberAndDocumentType() {
        // When
        List<Document> documents = documentRepository.findByClaimNumberAndDocumentType(
            "CLAIM-001", DocumentType.INVOICE);

        // Then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getClaimNumber()).isEqualTo("CLAIM-001");
        assertThat(documents.get(0).getDocumentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(documents.get(0).getFileName()).isEqualTo("invoice.pdf");
    }

    @Test
    @DisplayName("Should get single document by claim number and document type")
    void shouldGetDocumentByClaimNumberAndDocumentType() {
        // When
        Optional<Document> document = documentRepository.getByClaimNumberAndDocumentType(
            "CLAIM-001", DocumentType.ADJUSTER_REPORT);

        // Then
        assertThat(document).isPresent();
        assertThat(document.get().getClaimNumber()).isEqualTo("CLAIM-001");
        assertThat(document.get().getDocumentType()).isEqualTo(DocumentType.ADJUSTER_REPORT);
        assertThat(document.get().getFileName()).isEqualTo("adjuster-report.pdf");
    }

    @Test
    @DisplayName("Should return empty when no document found")
    void shouldReturnEmptyWhenNoDocumentFound() {
        // When
        Optional<Document> document = documentRepository.getByClaimNumberAndDocumentType(
            "NONEXISTENT", DocumentType.INVOICE);

        // Then
        assertThat(document).isEmpty();
    }

    @Test
    @DisplayName("Should find all documents by claim number")
    void shouldFindAllDocumentsByClaimNumber() {
        // When
        List<Document> documents = documentRepository.findByClaimNumber("CLAIM-001");

        // Then
        assertThat(documents).hasSize(2);
        assertThat(documents).extracting(Document::getClaimNumber)
            .containsOnly("CLAIM-001");
        assertThat(documents).extracting(Document::getDocumentType)
            .containsExactlyInAnyOrder(DocumentType.INVOICE, DocumentType.ADJUSTER_REPORT);
    }

    @Test
    @DisplayName("Should find documents by document type")
    void shouldFindDocumentsByDocumentType() {
        // When
        List<Document> documents = documentRepository.findByDocumentType(DocumentType.INVOICE);

        // Then
        assertThat(documents).hasSize(2); // Both active and inactive invoices
        assertThat(documents).extracting(Document::getDocumentType)
            .containsOnly(DocumentType.INVOICE);
        assertThat(documents).extracting(Document::getClaimNumber)
            .containsExactlyInAnyOrder("CLAIM-001", "CLAIM-002");
    }

    @Test
    @DisplayName("Should find active documents by claim number and document type")
    void shouldFindActiveDocumentsByClaimNumberAndDocumentType() {
        // When
        List<Document> documents = documentRepository.findByClaimNumberAndDocumentTypeAndIsActive(
            "CLAIM-002", DocumentType.INVOICE, true);

        // Then
        assertThat(documents).isEmpty(); // CLAIM-002 invoice is inactive
    }

    @Test
    @DisplayName("Should find inactive documents by claim number and document type")
    void shouldFindInactiveDocumentsByClaimNumberAndDocumentType() {
        // When
        List<Document> documents = documentRepository.findByClaimNumberAndDocumentTypeAndIsActive(
            "CLAIM-002", DocumentType.INVOICE, false);

        // Then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getIsActive()).isFalse();
        assertThat(documents.get(0).getClaimNumber()).isEqualTo("CLAIM-002");
    }

    @Test
    @DisplayName("Should find active documents using custom JPQL query")
    void shouldFindActiveDocumentsUsingCustomQuery() {
        // When
        List<Document> documents = documentRepository.findActiveDocumentsByClaimAndType(
            "CLAIM-001", DocumentType.INVOICE);

        // Then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getIsActive()).isTrue();
        assertThat(documents.get(0).getClaimNumber()).isEqualTo("CLAIM-001");
        assertThat(documents.get(0).getDocumentType()).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    @DisplayName("Should not find inactive documents using custom JPQL query")
    void shouldNotFindInactiveDocumentsUsingCustomQuery() {
        // When
        List<Document> documents = documentRepository.findActiveDocumentsByClaimAndType(
            "CLAIM-002", DocumentType.INVOICE);

        // Then
        assertThat(documents).isEmpty(); // Custom query filters out inactive documents
    }

    @Test
    @DisplayName("Should save and retrieve document correctly")
    void shouldSaveAndRetrieveDocumentCorrectly() {
        // Given
        Document newDocument = Document.builder()
            .claimNumber("CLAIM-003")
            .documentType(DocumentType.ADJUSTER_REPORT)
            .fileName("new-report.pdf")
            .fileContent("New report content".getBytes())
            .contentType("application/pdf")
            .uploadedBy("newadjuster")
            .isActive(true)
            .version(0L)
            .build();

        // When
        Document savedDocument = documentRepository.save(newDocument);

        // Then
        assertThat(savedDocument.getId()).isNotNull();
        assertThat(savedDocument.getCreatedAt()).isNotNull();
        assertThat(savedDocument.getUpdatedAt()).isNotNull();
        assertThat(savedDocument.getFileSize()).isEqualTo(newDocument.getFileContent().length);

        // Verify retrieval
        Optional<Document> retrievedDocument = documentRepository.findById(savedDocument.getId());
        assertThat(retrievedDocument).isPresent();
        assertThat(retrievedDocument.get().getClaimNumber()).isEqualTo("CLAIM-003");
    }

    @Test
    @DisplayName("Should handle multiple documents with same claim but different types")
    void shouldHandleMultipleDocumentsWithSameClaimButDifferentTypes() {
        // Given - we already have CLAIM-001 with both INVOICE and ADJUSTER_REPORT

        // When
        List<Document> invoices = documentRepository.findByClaimNumberAndDocumentType(
            "CLAIM-001", DocumentType.INVOICE);
        List<Document> reports = documentRepository.findByClaimNumberAndDocumentType(
            "CLAIM-001", DocumentType.ADJUSTER_REPORT);

        // Then
        assertThat(invoices).hasSize(1);
        assertThat(reports).hasSize(1);
        assertThat(invoices.get(0).getDocumentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(reports.get(0).getDocumentType()).isEqualTo(DocumentType.ADJUSTER_REPORT);
    }

    @Test
    @DisplayName("Should return empty list when searching for non-existent claim")
    void shouldReturnEmptyListWhenSearchingForNonExistentClaim() {
        // When
        List<Document> documents = documentRepository.findByClaimNumber("NONEXISTENT-CLAIM");

        // Then
        assertThat(documents).isEmpty();
    }

    @Test
    @DisplayName("Should handle case-sensitive claim number searches")
    void shouldHandleCaseSensitiveClaimNumberSearches() {
        // When
        List<Document> documents = documentRepository.findByClaimNumber("claim-001"); // lowercase

        // Then
        assertThat(documents).isEmpty(); // Should not find "CLAIM-001"
    }

    @Test
    @DisplayName("Should verify entity timestamps are set correctly")
    void shouldVerifyEntityTimestampsAreSetCorrectly() {
        // Given
        Document document = Document.builder()
            .claimNumber("CLAIM-TIMESTAMP")
            .documentType(DocumentType.INVOICE)
            .fileName("timestamp-test.pdf")
            .fileContent("Test content".getBytes())
            .contentType("application/pdf")
            .uploadedBy("timestampuser")
            .isActive(true)
            .version(0L)
            .build();

        // When
        Document savedDocument = documentRepository.saveAndFlush(document);

        // Then
        assertThat(savedDocument.getCreatedAt()).isNotNull();
        assertThat(savedDocument.getUpdatedAt()).isNotNull();
        assertThat(savedDocument.getCreatedAt()).isEqualTo(savedDocument.getUpdatedAt());
        assertThat(savedDocument.getFileSize()).isEqualTo("Test content".getBytes().length);
    }
}
