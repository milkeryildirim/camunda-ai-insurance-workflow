package tech.yildirim.camunda.documentmanager.document;

import java.util.Base64;
import java.util.List;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper for Document entity and DocumentDTO conversions.
 * Handles Base64 encoding/decoding for file content transfer via REST API.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DocumentMapper {

    DocumentMapper INSTANCE = Mappers.getMapper(DocumentMapper.class);

    /**
     * Converts Document entity to DocumentDTO.
     * Converts byte[] fileContent to Base64 string for REST API.
     *
     * @param document The Document entity to convert
     * @return DocumentDTO with Base64 encoded file content
     */
    @Named("entityToDtoWithContent")
    @Mapping(source = "fileContent", target = "fileContent", qualifiedByName = "bytesToBase64")
    DocumentDTO toDTO(Document document);

    /**
     * Converts DocumentDTO to Document entity.
     * Converts Base64 string fileContent to byte[] for storage.
     *
     * @param documentDTO The DocumentDTO to convert
     * @return Document entity with decoded file content
     */
    @Mapping(source = "fileContent", target = "fileContent", qualifiedByName = "base64ToBytes")
    Document toEntity(DocumentDTO documentDTO);

    /**
     * Converts Document entity to DocumentDTO without file content.
     * Used for listing operations where file content is not needed.
     *
     * @param document The Document entity to convert
     * @return DocumentDTO without file content
     */
    @Named("entityToDtoWithoutContent")
    @Mapping(target = "fileContent", ignore = true)
    DocumentDTO toDTOWithoutContent(Document document);

    /**
     * Converts a list of Document entities to a list of DocumentDTOs.
     * Uses toDTOWithoutContent for performance (excludes file content).
     *
     * @param documents List of Document entities
     * @return List of DocumentDTOs without file content
     */
    @IterableMapping(qualifiedByName = "entityToDtoWithoutContent")
    List<DocumentDTO> toDTOList(List<Document> documents);

    /**
     * Converts a list of DocumentDTOs to a list of Document entities.
     *
     * @param documentDTOs List of DocumentDTOs
     * @return List of Document entities without file content
     */
    List<Document> toEntityList(List<DocumentDTO> documentDTOs);

    /**
     * Updates existing Document entity with data from DocumentDTO.
     * Useful for partial updates while preserving fileContent and other fields.
     *
     * @param documentDTO Source DTO with updated data
     * @param document Target entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "fileContent", target = "fileContent", qualifiedByName = "base64ToBytes")
    void updateEntityFromDTO(DocumentDTO documentDTO, @MappingTarget Document document);

    // ========================== BASE64 CONVERSION METHODS ==========================

    /**
     * Converts byte array to Base64 string for REST API responses.
     *
     * @param fileContent byte array content
     * @return Base64 encoded string or null if input is null
     */
    @Named("bytesToBase64")
    default String bytesToBase64(byte[] fileContent) {
        if (fileContent == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(fileContent);
    }

    /**
     * Converts Base64 string to byte array for entity storage.
     *
     * @param base64Content Base64 encoded string
     * @return byte array content or null if input is null/empty
     */
    @Named("base64ToBytes")
    default byte[] base64ToBytes(String base64Content) {
        if (base64Content == null || base64Content.trim().isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 content: " + e.getMessage(), e);
        }
    }
}
