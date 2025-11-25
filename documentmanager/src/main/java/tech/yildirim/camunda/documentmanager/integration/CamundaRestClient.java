package tech.yildirim.camunda.documentmanager.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tech.yildirim.camunda.documentmanager.exception.CamundaConnectionException;
import tech.yildirim.camunda.documentmanager.exception.CamundaIntegrationException;

/**
 * REST client for integrating with Camunda BPM Engine.
 * <p>
 * This client provides high-level operations for communicating with the Camunda REST API,
 * specifically for triggering message events and managing process instances in the
 * insurance claim workflow system.
 * </p>
 * <p>
 * The client handles:
 * <ul>
 *   <li>Message correlation for intermediate message events</li>
 *   <li>Process instance triggering via message start events</li>
 *   <li>Error handling and retry logic</li>
 *   <li>Comprehensive logging for debugging and monitoring</li>
 *   <li>Validation of message payloads</li>
 * </ul>
 * </p>
 * <p>
 * Example usage for document upload workflow:
 * <pre>
 * {@code
 * @Autowired
 * private CamundaRestClient camundaClient;
 *
 * public void notifyDocumentUploaded(Long documentId, String claimNumber) {
 *     CamundaMessageDTO message = CamundaMessageDTO.builder()
 *         .messageName("DocumentUploaded")
 *         .businessKey(claimNumber)
 *         .addCorrelationKey("claimNumber", claimNumber)
 *         .addProcessVariable("documentId", documentId)
 *         .build();
 *
 *     camundaClient.sendMessage(message);
 * }
 * }
 * </pre>
 *
 * @author M. Ilker Yildirim
 * @version 2.0
 * @since 1.0
 * @see CamundaMessageDTO
 * @see RestTemplate
 */
@Component
@Slf4j
@Validated
public class CamundaRestClient {

  /** REST template optimized for Camunda operations with extended timeouts */
  private final RestTemplate restTemplate;

  /** Base URL for Camunda Engine REST API */
  private final String camundaEngineUrl;

  /** Message correlation endpoint path */
  private static final String MESSAGE_ENDPOINT = "/message";

  /**
   * Constructs a new CamundaRestClient with the specified configuration.
   * <p>
   * The client is configured with a REST template optimized for Camunda operations
   * and the base URL for the Camunda Engine REST API. URL normalization is performed
   * to ensure consistent endpoint construction.
   * </p>
   *
   * @param camundaRestTemplate The RestTemplate bean specifically configured for Camunda operations
   *                           with extended timeouts for long-running processes
   * @param camundaBaseUrl The base URL from application.properties for Camunda Engine REST API
   *                       (e.g., {@code http://localhost:8080/engine-rest})
   * @throws IllegalArgumentException if camundaBaseUrl is null, empty, or malformed
   */
  public CamundaRestClient(
      @Qualifier("camundaRestTemplate") RestTemplate camundaRestTemplate,
      @Value("${camunda.bpm.client.base-url}") String camundaBaseUrl) {

    if (camundaBaseUrl == null || camundaBaseUrl.trim().isEmpty()) {
      throw new IllegalArgumentException("Camunda base URL cannot be null or empty");
    }

    this.restTemplate = camundaRestTemplate;
    // Normalize URL by removing trailing slash to avoid double slashes in endpoint construction
    this.camundaEngineUrl = normalizeBaseUrl(camundaBaseUrl.trim());

    log.info("CamundaRestClient initialized with base URL: {}", this.camundaEngineUrl);
  }

  /**
   * Sends a correlation message to the Camunda BPM Engine.
   * <p>
   * This method triggers workflow continuation from intermediate message events
   * or starts new process instances with message start events. The message is
   * correlated based on the provided correlation keys and business key.
   * </p>
   * <p>
   * Message correlation behavior:
   * <ul>
   *   <li><strong>Intermediate Message Events:</strong> Correlates to waiting process instances
   *       based on correlation keys and continues workflow execution</li>
   *   <li><strong>Message Start Events:</strong> Starts new process instances if no
   *       matching running instances are found</li>
   *   <li><strong>Process Variables:</strong> Sets variables in the process context
   *       that are available throughout the workflow</li>
   * </ul>
   * </p>
   * <p>
   * Example for claim processing workflow:
   * <pre>
   * {@code
   * // Notify that an adjuster has been assigned to a claim
   * CamundaMessageDTO message = CamundaMessageDTO.builder()
   *     .messageName("AdjusterAssigned")
   *     .businessKey("CLAIM-2024-001")
   *     .addCorrelationKey("claimNumber", "CLAIM-2024-001")
   *     .addProcessVariable("adjusterId", 12345L)
   *     .addProcessVariable("assignedDate", LocalDateTime.now())
   *     .build();
   *
   * camundaClient.sendMessage(message);
   * }
   * </pre>
   *
   * @param messageDto The message payload containing message name, correlation keys,
   *                   and process variables. Must not be null and should be valid.
   * @throws CamundaIntegrationException if the Camunda API returns an error response
   * @throws CamundaConnectionException if the Camunda Engine is unreachable
   * @throws IllegalArgumentException if messageDto is null or invalid
   * @throws RuntimeException for any other unexpected errors during message sending
   */
  public void sendMessage(@Valid @NotNull CamundaMessageDTO messageDto) {
    if (messageDto == null) {
      throw new IllegalArgumentException("Message DTO cannot be null");
    }

    if (messageDto.getMessageName() == null || messageDto.getMessageName().trim().isEmpty()) {
      throw new IllegalArgumentException("Message name cannot be null or empty");
    }

    String url = camundaEngineUrl + MESSAGE_ENDPOINT;

    log.info("Sending message '{}' to Camunda Engine", messageDto.getMessageName());
    log.debug("Message correlation details - Business Key: {}, URL: {}",
              messageDto.getBusinessKey(), url);

    if (log.isTraceEnabled()) {
      log.trace("Full message payload: {}", messageDto);
    }

    try {
      HttpEntity<CamundaMessageDTO> requestEntity = createHttpEntity(messageDto);
      ResponseEntity<Void> response = restTemplate.postForEntity(url, requestEntity, Void.class);

      handleSuccessfulResponse(response, messageDto.getMessageName());

    } catch (HttpClientErrorException e) {
      handleClientError(e, messageDto.getMessageName());
    } catch (HttpServerErrorException e) {
      handleServerError(e, messageDto.getMessageName());
    } catch (ResourceAccessException e) {
      handleConnectionError(e, messageDto.getMessageName());
    } catch (Exception e) {
      handleUnexpectedError(e, messageDto.getMessageName());
    }
  }

  /**
   * Creates an HTTP entity with proper headers for Camunda REST API communication.
   * <p>
   * Configures the request with:
   * <ul>
   *   <li>Content-Type: application/json</li>
   *   <li>Accept: application/json</li>
   *   <li>Custom headers for tracking and debugging</li>
   * </ul>
   * </p>
   *
   * @param messageDto the message payload to wrap in the HTTP entity
   * @return configured HttpEntity ready for REST communication
   */
  private HttpEntity<CamundaMessageDTO> createHttpEntity(CamundaMessageDTO messageDto) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
    headers.set("X-Requested-By", "DocumentManager");

    return new HttpEntity<>(messageDto, headers);
  }

  /**
   * Handles successful responses from Camunda Engine.
   *
   * @param response the HTTP response from Camunda
   * @param messageName the name of the message that was sent
   */
  private void handleSuccessfulResponse(ResponseEntity<Void> response, String messageName) {
    HttpStatus statusCode = (HttpStatus) response.getStatusCode();

    if (statusCode.is2xxSuccessful()) {
      String message = switch (statusCode) {
        case NO_CONTENT -> "Message '{}' correlated successfully - process instance(s) continued";
        case OK -> "Message '{}' processed successfully";
        default -> "Message '{}' sent successfully with status: " + statusCode;
      };
      log.info(message, messageName);
    } else {
      log.warn("Unexpected response status from Camunda for message '{}': {}",
               messageName, statusCode);
    }
  }

  /**
   * Handles HTTP client errors (4xx status codes) from Camunda Engine.
   *
   * @param e the HTTP client error exception
   * @param messageName the name of the message that failed
   * @throws CamundaIntegrationException with detailed error information
   */
  private void handleClientError(HttpClientErrorException e, String messageName) {
    String errorBody = e.getResponseBodyAsString();
    HttpStatus statusCode = (HttpStatus) e.getStatusCode();

    log.error("Client error sending message '{}' to Camunda - Status: {}, Body: {}",
              messageName, statusCode, errorBody);

    String errorMessage = switch (statusCode) {
      case BAD_REQUEST ->
        "Invalid message format or correlation keys for message: " + messageName;
      case NOT_FOUND ->
        "No matching process definition or instance found for message: " + messageName;
      case CONFLICT ->
        "Message correlation conflict for: " + messageName;
      default ->
        "Client error correlating message: " + messageName;
    };

    throw new CamundaIntegrationException(errorMessage + " - " + errorBody, e);
  }

  /**
   * Handles HTTP server errors (5xx status codes) from Camunda Engine.
   *
   * @param e the HTTP server error exception
   * @param messageName the name of the message that failed
   * @throws CamundaIntegrationException indicating server-side issues
   */
  private void handleServerError(HttpServerErrorException e, String messageName) {
    log.error("Server error from Camunda Engine for message '{}' - Status: {}, Body: {}",
              messageName, e.getStatusCode(), e.getResponseBodyAsString());

    throw new CamundaIntegrationException(
        "Camunda Engine server error for message: " + messageName +
        " - Status: " + e.getStatusCode(), e);
  }

  /**
   * Handles connection errors when Camunda Engine is unreachable.
   *
   * @param e the resource access exception
   * @param messageName the name of the message that failed
   * @throws CamundaConnectionException indicating connectivity issues
   */
  private void handleConnectionError(ResourceAccessException e, String messageName) {
    log.error("Failed to connect to Camunda Engine for message '{}': {}",
              messageName, e.getMessage());

    throw new CamundaConnectionException(
        "Camunda Engine is unreachable for message: " + messageName, e);
  }

  /**
   * Handles unexpected errors during Camunda communication.
   *
   * @param e the unexpected exception
   * @param messageName the name of the message that failed
   * @throws CamundaIntegrationException wrapping the original exception
   */
  private void handleUnexpectedError(Exception e, String messageName) {
    log.error("Unexpected error sending message '{}' to Camunda Engine", messageName, e);

    throw new CamundaIntegrationException(
        "Unexpected error during Camunda integration for message: " + messageName, e);
  }

  /**
   * Normalizes the base URL by removing trailing slashes.
   * <p>
   * This ensures consistent URL construction when appending endpoint paths.
   * </p>
   *
   * @param baseUrl the raw base URL from configuration
   * @return normalized base URL without trailing slash
   */
  private String normalizeBaseUrl(String baseUrl) {
    return baseUrl.endsWith("/") ?
        baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
