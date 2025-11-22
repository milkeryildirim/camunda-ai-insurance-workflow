package tech.yildirim.camunda.documentmanager.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test class for DocumentMapper MapStruct implementation.
 * Tests all mapping scenarios including Base64 encoding/decoding.
 */
@SpringBootTest
@DisplayName("DocumentMapper Tests")
class DocumentMapperTest {

    @Autowired
    private DocumentMapper documentMapper;

    // ========================== TEST DATA ==========================

    private static final String TEST_CLAIM_NUMBER = "CLAIM-001";
    private static final DocumentType TEST_DOCUMENT_TYPE = DocumentType.INVOICE;
    private static final String TEST_FILE_NAME = "test-invoice.pdf";
    private static final String TEST_CONTENT_TYPE = "application/pdf";
    private static final Long TEST_FILE_SIZE = 1024L;
    private static final String TEST_UPLOADED_BY = "test-user";
    private static final LocalDateTime TEST_DATE = LocalDateTime.of(2024, 1, 1, 10, 0);
    private static final Boolean TEST_IS_ACTIVE = true;
    private static final Long TEST_VERSION = 1L;

    // Test file content
    private static final byte[] TEST_FILE_CONTENT = "Test PDF Content".getBytes();
    private static final String TEST_BASE64_CONTENT = Base64.getEncoder().encodeToString(TEST_FILE_CONTENT);

    private Document createTestDocument() {
        return Document.builder()
            .id(1L)
            .claimNumber(TEST_CLAIM_NUMBER)
            .documentType(TEST_DOCUMENT_TYPE)
            .fileName(TEST_FILE_NAME)
            .contentType(TEST_CONTENT_TYPE)
            .fileSize(TEST_FILE_SIZE)
            .fileContent(TEST_FILE_CONTENT)
            .uploadedBy(TEST_UPLOADED_BY)
            .createdAt(TEST_DATE)
            .updatedAt(TEST_DATE)
            .isActive(TEST_IS_ACTIVE)
            .version(TEST_VERSION)
            .build();
    }

    private DocumentDTO createTestDocumentDTO() {
        return DocumentDTO.builder()
            .id(1L)
            .claimNumber(TEST_CLAIM_NUMBER)
            .documentType(TEST_DOCUMENT_TYPE)
            .fileName(TEST_FILE_NAME)
            .contentType(TEST_CONTENT_TYPE)
            .fileSize(TEST_FILE_SIZE)
            .fileContent(TEST_BASE64_CONTENT)
            .uploadedBy(TEST_UPLOADED_BY)
            .createdAt(TEST_DATE)
            .updatedAt(TEST_DATE)
            .isActive(TEST_IS_ACTIVE)
            .version(TEST_VERSION)
            .build();
    }

    // ========================== ENTITY TO DTO MAPPING TESTS ==========================

    @Nested
    @DisplayName("Entity to DTO Mapping Tests")
    class EntityToDtoMappingTests {

        @Test
        @DisplayName("Should convert Document entity to DocumentDTO with Base64 encoded file content")
        void shouldConvertEntityToDtoWithContent() {
            // Given
            Document document = createTestDocument();

            // When
            DocumentDTO result = documentMapper.toDTO(document);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(document.getId());
            assertThat(result.getClaimNumber()).isEqualTo(document.getClaimNumber());
            assertThat(result.getDocumentType()).isEqualTo(document.getDocumentType());
            assertThat(result.getFileName()).isEqualTo(document.getFileName());
            assertThat(result.getContentType()).isEqualTo(document.getContentType());
            assertThat(result.getFileSize()).isEqualTo(document.getFileSize());
            assertThat(result.getFileContent()).isEqualTo(TEST_BASE64_CONTENT);
            assertThat(result.getUploadedBy()).isEqualTo(document.getUploadedBy());
            assertThat(result.getCreatedAt()).isEqualTo(document.getCreatedAt());
            assertThat(result.getUpdatedAt()).isEqualTo(document.getUpdatedAt());
            assertThat(result.getIsActive()).isEqualTo(document.getIsActive());
            assertThat(result.getVersion()).isEqualTo(document.getVersion());
        }

        @Test
        @DisplayName("Should convert Document entity to DocumentDTO without file content")
        void shouldConvertEntityToDtoWithoutContent() {
            // Given
            Document document = createTestDocument();

            // When
            DocumentDTO result = documentMapper.toDTOWithoutContent(document);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(document.getId());
            assertThat(result.getClaimNumber()).isEqualTo(document.getClaimNumber());
            assertThat(result.getDocumentType()).isEqualTo(document.getDocumentType());
            assertThat(result.getFileName()).isEqualTo(document.getFileName());
            assertThat(result.getContentType()).isEqualTo(document.getContentType());
            assertThat(result.getFileSize()).isEqualTo(document.getFileSize());
            assertThat(result.getFileContent()).isNull(); // Should be excluded
            assertThat(result.getUploadedBy()).isEqualTo(document.getUploadedBy());
            assertThat(result.getCreatedAt()).isEqualTo(document.getCreatedAt());
            assertThat(result.getUpdatedAt()).isEqualTo(document.getUpdatedAt());
            assertThat(result.getIsActive()).isEqualTo(document.getIsActive());
            assertThat(result.getVersion()).isEqualTo(document.getVersion());
        }

        @Test
        @DisplayName("Should handle null Document entity gracefully")
        void shouldHandleNullEntityGracefully() {
            // When
            DocumentDTO result = documentMapper.toDTO(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle Document entity with null file content")
        void shouldHandleEntityWithNullFileContent() {
            // Given
            Document document = createTestDocument();
            document.setFileContent(null);

            // When
            DocumentDTO result = documentMapper.toDTO(document);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFileContent()).isNull();
            assertThat(result.getClaimNumber()).isEqualTo(document.getClaimNumber());
        }
    }

    // ========================== DTO TO ENTITY MAPPING TESTS ==========================

    @Nested
    @DisplayName("DTO to Entity Mapping Tests")
    class DtoToEntityMappingTests {

        @Test
        @DisplayName("Should convert DocumentDTO to Document entity with decoded file content")
        void shouldConvertDtoToEntity() {
            // Given
            DocumentDTO documentDTO = createTestDocumentDTO();

            // When
            Document result = documentMapper.toEntity(documentDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(documentDTO.getId());
            assertThat(result.getClaimNumber()).isEqualTo(documentDTO.getClaimNumber());
            assertThat(result.getDocumentType()).isEqualTo(documentDTO.getDocumentType());
            assertThat(result.getFileName()).isEqualTo(documentDTO.getFileName());
            assertThat(result.getContentType()).isEqualTo(documentDTO.getContentType());
            assertThat(result.getFileSize()).isEqualTo(documentDTO.getFileSize());
            assertThat(result.getFileContent()).isEqualTo(TEST_FILE_CONTENT);
            assertThat(result.getUploadedBy()).isEqualTo(documentDTO.getUploadedBy());
            assertThat(result.getCreatedAt()).isEqualTo(documentDTO.getCreatedAt());
            assertThat(result.getUpdatedAt()).isEqualTo(documentDTO.getUpdatedAt());
            assertThat(result.getIsActive()).isEqualTo(documentDTO.getIsActive());
            assertThat(result.getVersion()).isEqualTo(documentDTO.getVersion());
        }

        @Test
        @DisplayName("Should handle null DocumentDTO gracefully")
        void shouldHandleNullDtoGracefully() {
            // When
            Document result = documentMapper.toEntity(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle DocumentDTO with null file content")
        void shouldHandleDtoWithNullFileContent() {
            // Given
            DocumentDTO documentDTO = createTestDocumentDTO();
            documentDTO.setFileContent(null);

            // When
            Document result = documentMapper.toEntity(documentDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFileContent()).isNull();
            assertThat(result.getClaimNumber()).isEqualTo(documentDTO.getClaimNumber());
        }

        @Test
        @DisplayName("Should handle DocumentDTO with empty file content")
        void shouldHandleDtoWithEmptyFileContent() {
            // Given
            DocumentDTO documentDTO = createTestDocumentDTO();
            documentDTO.setFileContent("");

            // When
            Document result = documentMapper.toEntity(documentDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFileContent()).isNull();
        }

        @Test
        @DisplayName("Should throw exception for invalid Base64 content")
        void shouldThrowExceptionForInvalidBase64() {
            // Given
            DocumentDTO documentDTO = createTestDocumentDTO();
            documentDTO.setFileContent("invalid-base64-content!");

            // When & Then
            assertThatThrownBy(() -> documentMapper.toEntity(documentDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Base64 content");
        }
    }

    // ========================== LIST MAPPING TESTS ==========================

    @Nested
    @DisplayName("List Mapping Tests")
    class ListMappingTests {

        @Test
        @DisplayName("Should convert list of Document entities to list of DocumentDTOs without file content")
        void shouldConvertEntityListToDtoList() {
            // Given
            Document document1 = createTestDocument();
            Document document2 = createTestDocument();
            document2.setId(2L);
            document2.setClaimNumber("CLAIM-002");

            List<Document> documents = Arrays.asList(document1, document2);

            // When
            List<DocumentDTO> result = documentMapper.toDTOList(documents);

            // Then
            assertThat(result)
                .isNotNull()
                .hasSize(2);

            DocumentDTO dto1 = result.getFirst();
            assertThat(dto1.getId()).isEqualTo(document1.getId());
            assertThat(dto1.getClaimNumber()).isEqualTo(document1.getClaimNumber());
            assertThat(dto1.getFileContent()).isNull(); // Should be excluded for performance

            DocumentDTO dto2 = result.get(1);
            assertThat(dto2.getId()).isEqualTo(document2.getId());
            assertThat(dto2.getClaimNumber()).isEqualTo(document2.getClaimNumber());
            assertThat(dto2.getFileContent()).isNull(); // Should be excluded for performance
        }

        @Test
        @DisplayName("Should handle empty list gracefully")
        void shouldHandleEmptyListGracefully() {
            // Given
            List<Document> emptyList = List.of();

            // When
            List<DocumentDTO> result = documentMapper.toDTOList(emptyList);

            // Then
            assertThat(result)
                .isNotNull()
                .isEmpty();
        }

        @Test
        @DisplayName("Should handle null list gracefully")
        void shouldHandleNullListGracefully() {
            // When
            List<DocumentDTO> result = documentMapper.toDTOList(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should convert list of DocumentDTOs to list of Document entities")
        void shouldConvertDtoListToEntityList() {
            // Given
            DocumentDTO dto1 = createTestDocumentDTO();
            DocumentDTO dto2 = createTestDocumentDTO();
            dto2.setId(2L);
            dto2.setClaimNumber("CLAIM-002");

            List<DocumentDTO> dtos = Arrays.asList(dto1, dto2);

            // When
            List<Document> result = documentMapper.toEntityList(dtos);

            // Then
            assertThat(result)
                .isNotNull()
                .hasSize(2);

            Document entity1 = result.getFirst();
            assertThat(entity1.getId()).isEqualTo(dto1.getId());
            assertThat(entity1.getClaimNumber()).isEqualTo(dto1.getClaimNumber());
            assertThat(entity1.getFileContent()).isEqualTo(TEST_FILE_CONTENT);

            Document entity2 = result.get(1);
            assertThat(entity2.getId()).isEqualTo(dto2.getId());
            assertThat(entity2.getClaimNumber()).isEqualTo(dto2.getClaimNumber());
            assertThat(entity2.getFileContent()).isEqualTo(TEST_FILE_CONTENT);
        }
    }

    // ========================== UPDATE ENTITY MAPPING TESTS ==========================

    @Nested
    @DisplayName("Update Entity Mapping Tests")
    class UpdateEntityMappingTests {

        @Test
        @DisplayName("Should update existing entity from DTO preserving ID and createdAt")
        void shouldUpdateEntityFromDto() {
            // Given
            Document existingDocument = createTestDocument();
            existingDocument.setId(100L);
            existingDocument.setCreatedAt(LocalDateTime.of(2023, 1, 1, 9, 0));

            DocumentDTO updateDTO = createTestDocumentDTO();
            updateDTO.setId(999L); // Should be ignored
            updateDTO.setClaimNumber("UPDATED-CLAIM");
            updateDTO.setFileName("updated-file.pdf");
            updateDTO.setCreatedAt(LocalDateTime.of(2024, 12, 1, 15, 0)); // Should be ignored

            // When
            documentMapper.updateEntityFromDTO(updateDTO, existingDocument);

            // Then
            assertThat(existingDocument.getId()).isEqualTo(100L); // Preserved
            assertThat(existingDocument.getCreatedAt()).isEqualTo(LocalDateTime.of(2023, 1, 1, 9, 0)); // Preserved
            assertThat(existingDocument.getClaimNumber()).isEqualTo("UPDATED-CLAIM"); // Updated
            assertThat(existingDocument.getFileName()).isEqualTo("updated-file.pdf"); // Updated
            assertThat(existingDocument.getFileContent()).isEqualTo(TEST_FILE_CONTENT); // Updated from Base64
            assertThat(existingDocument.getUpdatedAt()).isEqualTo(updateDTO.getUpdatedAt()); // Updated
        }

        @Test
        @DisplayName("Should handle null DTO in update gracefully")
        void shouldHandleNullDtoInUpdate() {
            // Given
            Document existingDocument = createTestDocument();
            String originalClaimNumber = existingDocument.getClaimNumber();

            // When & Then - Should not throw exception
            documentMapper.updateEntityFromDTO(null, existingDocument);

            // Entity should remain unchanged
            assertThat(existingDocument.getClaimNumber()).isEqualTo(originalClaimNumber);
        }

        @Test
        @DisplayName("Should throw exception when target entity is null in update")
        void shouldThrowExceptionWhenTargetEntityIsNull() {
            // Given
            DocumentDTO updateDTO = createTestDocumentDTO();

            // When & Then - Should throw NullPointerException since MapStruct cannot update null target
            assertThatThrownBy(() -> documentMapper.updateEntityFromDTO(updateDTO, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ========================== BASE64 CONVERSION TESTS ==========================

    @Nested
    @DisplayName("Base64 Conversion Tests")
    class Base64ConversionTests {

        @Test
        @DisplayName("Should convert byte array to Base64 string")
        void shouldConvertBytesToBase64() {
            // Given
            byte[] testBytes = "Test content".getBytes();
            String expectedBase64 = Base64.getEncoder().encodeToString(testBytes);

            // When
            String result = documentMapper.bytesToBase64(testBytes);

            // Then
            assertThat(result).isEqualTo(expectedBase64);
        }

        @Test
        @DisplayName("Should convert Base64 string to byte array")
        void shouldConvertBase64ToBytes() {
            // Given
            byte[] originalBytes = "Test content".getBytes();
            String base64String = Base64.getEncoder().encodeToString(originalBytes);

            // When
            byte[] result = documentMapper.base64ToBytes(base64String);

            // Then
            assertThat(result).isEqualTo(originalBytes);
        }

        @Test
        @DisplayName("Should handle null byte array gracefully")
        void shouldHandleNullBytesGracefully() {
            // When
            String result = documentMapper.bytesToBase64(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle null Base64 string gracefully")
        void shouldHandleNullBase64Gracefully() {
            // When
            byte[] result = documentMapper.base64ToBytes(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle empty Base64 string gracefully")
        void shouldHandleEmptyBase64Gracefully() {
            // When
            byte[] result = documentMapper.base64ToBytes("");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle whitespace-only Base64 string gracefully")
        void shouldHandleWhitespaceBase64Gracefully() {
            // When
            byte[] result = documentMapper.base64ToBytes("   ");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should throw exception for invalid Base64 format")
        void shouldThrowExceptionForInvalidBase64Format() {
            // Given
            String invalidBase64 = "invalid-base64!@#$%";

            // When & Then
            assertThatThrownBy(() -> documentMapper.base64ToBytes(invalidBase64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Base64 content");
        }

        @Test
        @DisplayName("Should handle round-trip conversion correctly")
        void shouldHandleRoundTripConversionCorrectly() {
            // Given
            byte[] originalBytes = "Complex test content with special chars: äöü@#$%".getBytes();

            // When
            String base64 = documentMapper.bytesToBase64(originalBytes);
            byte[] roundTripBytes = documentMapper.base64ToBytes(base64);

            // Then
            assertThat(roundTripBytes).isEqualTo(originalBytes);
        }
    }

    // ========================== INTEGRATION TESTS ==========================

    @Nested
    @DisplayName("Integration Mapping Tests")
    class IntegrationMappingTests {

        @Test
        @DisplayName("Should perform complete round-trip conversion correctly")
        void shouldPerformCompleteRoundTripConversion() {
            // Given
            Document originalDocument = createTestDocument();

            // When - Entity -> DTO -> Entity
            DocumentDTO dto = documentMapper.toDTO(originalDocument);
            Document roundTripDocument = documentMapper.toEntity(dto);

            // Then
            assertThat(roundTripDocument).isNotNull();
            assertThat(roundTripDocument.getId()).isEqualTo(originalDocument.getId());
            assertThat(roundTripDocument.getClaimNumber()).isEqualTo(originalDocument.getClaimNumber());
            assertThat(roundTripDocument.getDocumentType()).isEqualTo(originalDocument.getDocumentType());
            assertThat(roundTripDocument.getFileName()).isEqualTo(originalDocument.getFileName());
            assertThat(roundTripDocument.getContentType()).isEqualTo(originalDocument.getContentType());
            assertThat(roundTripDocument.getFileSize()).isEqualTo(originalDocument.getFileSize());
            assertThat(roundTripDocument.getFileContent()).isEqualTo(originalDocument.getFileContent());
            assertThat(roundTripDocument.getUploadedBy()).isEqualTo(originalDocument.getUploadedBy());
            assertThat(roundTripDocument.getCreatedAt()).isEqualTo(originalDocument.getCreatedAt());
            assertThat(roundTripDocument.getUpdatedAt()).isEqualTo(originalDocument.getUpdatedAt());
            assertThat(roundTripDocument.getIsActive()).isEqualTo(originalDocument.getIsActive());
            assertThat(roundTripDocument.getVersion()).isEqualTo(originalDocument.getVersion());
        }

        @Test
        @DisplayName("Should handle large file content correctly")
        void shouldHandleLargeFileContentCorrectly() {
            // Given
            byte[] largeContent = new byte[1024 * 1024]; // 1MB
            for (int i = 0; i < largeContent.length; i++) {
                largeContent[i] = (byte) (i % 256);
            }

            Document document = createTestDocument();
            document.setFileContent(largeContent);

            // When
            DocumentDTO dto = documentMapper.toDTO(document);
            Document roundTripDocument = documentMapper.toEntity(dto);

            // Then
            assertThat(roundTripDocument.getFileContent()).isEqualTo(largeContent);
            assertThat(dto.getFileContent()).isNotNull();
            assertThat(dto.getFileContent().length()).isGreaterThan(1000000); // Base64 encoded should be larger
        }
    }
}
