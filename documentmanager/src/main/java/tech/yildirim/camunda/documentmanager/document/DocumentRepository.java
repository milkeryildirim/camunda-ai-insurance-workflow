package tech.yildirim.camunda.documentmanager.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Document entity operations.
 * Provides database access methods for document management.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * Finds documents by claim number and document type.
     *
     * @param claimNumber The claim number to search for
     * @param documentType The document type to filter by
     * @return List of documents matching the criteria
     */
    List<Document> findByClaimNumberAndDocumentType(String claimNumber, DocumentType documentType);

    /**
     * Finds a single document by claim number and document type.
     * Returns the first match if multiple documents exist.
     *
     * @param claimNumber The claim number to search for
     * @param documentType The document type to filter by
     * @return Optional containing the document if found
     */
    Optional<Document> getByClaimNumberAndDocumentType(String claimNumber, DocumentType documentType);

    /**
     * Finds all documents for a specific claim number.
     *
     * @param claimNumber The claim number to search for
     * @return List of documents for the claim
     */
    List<Document> findByClaimNumber(String claimNumber);

    /**
     * Finds all documents of a specific type.
     *
     * @param documentType The document type to filter by
     * @return List of documents of the specified type
     */
    List<Document> findByDocumentType(DocumentType documentType);

    /**
     * Finds active documents by claim number and document type.
     *
     * @param claimNumber The claim number to search for
     * @param documentType The document type to filter by
     * @param isActive The active status filter
     * @return List of active documents matching the criteria
     */
    List<Document> findByClaimNumberAndDocumentTypeAndIsActive(
        String claimNumber,
        DocumentType documentType,
        Boolean isActive
    );

    /**
     * Custom query to find documents with specific criteria using JPQL.
     *
     * @param claimNumber The claim number to search for
     * @param documentType The document type to filter by
     * @return List of documents matching the criteria
     */
    @Query("SELECT d FROM Document d WHERE d.claimNumber = :claimNumber AND d.documentType = :documentType AND d.isActive = true")
    List<Document> findActiveDocumentsByClaimAndType(
        @Param("claimNumber") String claimNumber,
        @Param("documentType") DocumentType documentType
    );
}
